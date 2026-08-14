package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.*
import yeo.chi.proejct.pms.reservation.persistent.entity.OutboundNotificationEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationLogEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.toRange
import yeo.chi.proejct.pms.reservation.persistent.repository.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationLogRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationRepository
import java.time.OffsetDateTime

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val reservationLogRepository: ReservationLogRepository,
    private val outboundNotificationRepository: OutboundNotificationRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun book(command: BookReservationCommand): ReservationLog {
        require(command.initiatedBy != RequestInitiator.HOST) {
            "HOST는 예약 요청(BOOK)을 시작할 수 없습니다: initiatedBy=${command.initiatedBy}"
        }

        command.buildBookRequestKey().let {
            reservationLogRepository.findByRequestKey(it)
                ?.let { existingRequest ->
                    return existingRequest.toDomain()
                }

            return try {
                attemptBook(command, it)
            } catch (_: DataIntegrityViolationException) {
                recordConflict(command, it)
            }
        }
    }

    private fun attemptBook(
        command: BookReservationCommand,
        requestKey: String,
    ): ReservationLog =
        checkNotNull(
            transactionTemplate.execute {
                // reservationCode는 DB가 생성하는 컬럼이라(ReservationEntity의 @Generated), 저장 전
                // 도메인 객체의 reservationCode는 실제 값이 아닌 placeholder다. reservation_requests/
                // outbound_notifications가 이 값을 FK로 참조하므로, 저장 후 재조회한 실제 값을 써야 한다.
                val reservation =
                    reservationRepository.saveAndFlush(ReservationEntity.from(Reservation.of(command))).toDomain()

                val savedRequest =
                    reservationLogRepository.saveAndFlush(
                        ReservationLogEntity.from(ReservationLog.booked(requestKey, reservation, command.initiatedBy)),
                    )

                outboundNotificationRepository.saveAndFlush(confirmedNotificationEntity(requestKey, reservation))

                savedRequest.toDomain()
            },
        ) { "예약 확정 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun recordConflict(
        command: BookReservationCommand,
        requestKey: String,
    ): ReservationLog {
        // uq_request_key 위반으로 여기서 예외가 나면 트랜잭션(과 그 Hibernate 세션)은 이미 롤백된 상태다.
        // 같은 세션을 재사용해 재조회하면 "세션이 예외 이후에 flush됨" 문제로 이어지므로,
        // 예외를 트랜잭션 바깥으로 흘려보낸 뒤 별도 트랜잭션으로 재조회한다.
        return try {
            checkNotNull(
                transactionTemplate.execute {
                    val now = OffsetDateTime.now()
                    val savedRequest =
                        reservationLogRepository.saveAndFlush(
                            ReservationLogEntity.from(ReservationLog.bookConflict(requestKey, command, now)),
                        )

                    // 패자 채널에도 거부 통보를 남긴다(기획문서 4.1). 예약 row가 없어 reservation_code는
                    // null — reservationNo는 payload 안에 담아 발신 시 대체한다.
                    outboundNotificationRepository.saveAndFlush(rejectedNotificationEntity(command, requestKey, now))

                    savedRequest.toDomain()
                },
            ) { "충돌 기록 트랜잭션은 항상 값을 반환해야 합니다" }
        } catch (duplicateRequestKey: DataIntegrityViolationException) {
            checkNotNull(reservationLogRepository.findByRequestKey(requestKey)) {
                "uq_request_key 위반이면 동일 requestKey row가 반드시 존재해야 합니다"
            }.toDomain()
        }
    }

    fun cancelConfirm(command: CancelConfirmCommand): ReservationLog {
        val requestKey =
            buildCancelConfirmRequestKey(
                command.platformId,
                command.platformReservationRef,
                command.externalRequestId,
                OffsetDateTime.now(),
            )

        reservationLogRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return cancelConfirmWithRetry(command, requestKey)
    }

    // 낙관적 락 충돌(ObjectOptimisticLockingFailureException)은 attemptCancelConfirm의 트랜잭션 "밖"에서 잡는다.
    // BOOK의 recordConflict와 같은 이유로, 실패한 트랜잭션의 Hibernate 세션을 재사용하면 예외 이후 flush 문제가 생긴다.
    private fun cancelConfirmWithRetry(
        command: CancelConfirmCommand,
        requestKey: String,
    ): ReservationLog {
        var attempt = 1
        while (true) {
            try {
                return attemptCancelConfirm(command, requestKey)
            } catch (staleVersion: ObjectOptimisticLockingFailureException) {
                if (attempt >= CANCEL_CONFIRM_MAX_ATTEMPTS) throw staleVersion
                attempt++
            }
        }
    }

    private fun attemptCancelConfirm(
        command: CancelConfirmCommand,
        requestKey: String,
    ): ReservationLog =
        checkNotNull(
            transactionTemplate.execute {
                val reservation =
                    reservationRepository.findByPlatformIdAndPlatformReservationRef(
                        command.platformId,
                        command.platformReservationRef,
                    )

                if (reservation == null) {
                    reservationLogRepository
                        .saveAndFlush(
                            ReservationLogEntity.from(
                                ReservationLog.cancelConfirmNotFound(
                                    requestKey,
                                    command
                                )
                            )
                        )
                        .toDomain()
                } else {
                    when (reservation.status) {
                        ReservationStatus.CANCELLED ->
                            reservationLogRepository
                                .saveAndFlush(
                                    ReservationLogEntity.from(
                                        ReservationLog.cancelConfirmAlreadyCancelled(
                                            requestKey,
                                            reservation.toDomain()
                                        ),
                                    ),
                                ).toDomain()

                        ReservationStatus.CONFIRMED, ReservationStatus.PENDING_CANCEL ->
                            confirmCancellation(requestKey, reservation)
                    }
                }
            },
        ) { "CANCEL_CONFIRM 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmCancellation(
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationLog {
        val now = OffsetDateTime.now()

        reservation.status = ReservationStatus.CANCELLED
        val savedReservation = reservationRepository.saveAndFlush(reservation).toDomain()

        val savedRequest =
            reservationLogRepository.saveAndFlush(
                ReservationLogEntity.from(ReservationLog.cancelConfirmed(requestKey, savedReservation, now)),
            )

        outboundNotificationRepository.saveAndFlush(cancelledNotificationEntity(requestKey, savedReservation, now))

        return savedRequest.toDomain()
    }

    fun cancelRequest(command: CancelRequestCommand): ReservationLog {
        val requestKey = buildCancelRequestKey(command.reservationId, command.externalRequestId, OffsetDateTime.now())

        reservationLogRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return cancelRequestWithRetry(command, requestKey)
    }

    // 낙관적 락 충돌은 attemptCancelRequest의 트랜잭션 "밖"에서 잡는다 (#15의 cancelConfirm과 동일한 이유 —
    // 실패한 트랜잭션의 Hibernate 세션을 재사용하면 예외 이후 flush 문제가 생긴다).
    private fun cancelRequestWithRetry(
        command: CancelRequestCommand,
        requestKey: String,
    ): ReservationLog {
        var attempt = 1
        while (true) {
            try {
                return attemptCancelRequest(command, requestKey)
            } catch (staleVersion: ObjectOptimisticLockingFailureException) {
                if (attempt >= CANCEL_REQUEST_MAX_ATTEMPTS) throw staleVersion
                attempt++
            }
        }
    }

    private fun attemptCancelRequest(
        command: CancelRequestCommand,
        requestKey: String,
    ): ReservationLog =
        checkNotNull(
            transactionTemplate.execute {
                val reservation = reservationRepository.findById(command.reservationId).orElse(null)

                if (reservation == null) {
                    // reservation_requests.platform_id는 NOT NULL이지만, 호스트가 존재하지 않는 reservationId로
                    // 요청한 경우 어떤 채널(platformId)과도 연관지을 수 없어 감사 로그 row를 만들 수 없다.
                    // DB에는 아무것도 남기지 않고 합성된(비영속) FAILED 결과만 반환한다.
                    logger.warn("CANCEL_REQUEST 대상 예약을 찾을 수 없어 감사 로그 없이 처리됨: requestKey={}", requestKey)
                    ReservationLog.cancelRequestNotFound(requestKey)
                } else {
                    when (reservation.status) {
                        ReservationStatus.CONFIRMED -> confirmCancelRequest(command, requestKey, reservation)

                        ReservationStatus.PENDING_CANCEL ->
                            reservationLogRepository
                                .saveAndFlush(
                                    ReservationLogEntity.from(
                                        ReservationLog.cancelRequestAlreadyPending(
                                            requestKey,
                                            reservation.toDomain(),
                                            command.reason,
                                        ),
                                    ),
                                ).toDomain()

                        ReservationStatus.CANCELLED ->
                            reservationLogRepository
                                .saveAndFlush(
                                    ReservationLogEntity.from(
                                        ReservationLog.cancelRequestAlreadyCancelled(
                                            requestKey,
                                            reservation.toDomain()
                                        ),
                                    ),
                                )
                                .toDomain()
                    }
                }
            },
        ) { "CANCEL_REQUEST 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmCancelRequest(
        command: CancelRequestCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationLog {
        val now = OffsetDateTime.now()

        reservation.status = ReservationStatus.PENDING_CANCEL
        val savedReservation = reservationRepository.saveAndFlush(reservation).toDomain()

        val savedRequest =
            reservationLogRepository.saveAndFlush(
                ReservationLogEntity.from(
                    ReservationLog.cancelRequested(requestKey, savedReservation, command.reason, now),
                ),
            )

        outboundNotificationRepository.saveAndFlush(
            cancelRequestedNotificationEntity(requestKey, savedReservation, command.reason, now),
        )

        return savedRequest.toDomain()
    }

    private fun confirmedNotificationEntity(
        requestKey: String,
        reservation: Reservation,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(OutboundNotification.from(requestKey, reservation, objectMapper))

    private fun rejectedNotificationEntity(
        command: BookReservationCommand,
        requestKey: String,
        now: OffsetDateTime,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(OutboundNotification.rejected(requestKey, command, now, objectMapper))

    private fun cancelledNotificationEntity(
        requestKey: String,
        reservation: Reservation,
        now: OffsetDateTime,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(OutboundNotification.cancelled(requestKey, reservation, now, objectMapper))

    private fun cancelRequestedNotificationEntity(
        requestKey: String,
        reservation: Reservation,
        reason: CancelRequestReason,
        now: OffsetDateTime,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(
            OutboundNotification.cancelRequested(requestKey, reservation, reason, now, objectMapper),
        )

    private fun changedNotificationEntity(
        requestKey: String,
        reservation: Reservation,
        newDateRange: ReservationDateRange,
        now: OffsetDateTime,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(
            OutboundNotification.changed(requestKey, reservation, newDateRange, now, objectMapper),
        )

    private fun changeRejectedNotificationEntity(
        requestKey: String,
        reservation: Reservation,
        newDateRange: ReservationDateRange,
        now: OffsetDateTime,
    ): OutboundNotificationEntity =
        OutboundNotificationEntity.from(
            OutboundNotification.changeRejected(requestKey, reservation, newDateRange, now, objectMapper),
        )

    fun change(command: ChangeReservationCommand): ReservationLog {
        require(command.initiatedBy != RequestInitiator.HOST) {
            "HOST는 예약 변경(CHANGE)을 요청할 수 없습니다: initiatedBy=${command.initiatedBy}"
        }

        val requestKey =
            buildChangeRequestKey(
                command.platformId,
                command.platformReservationRef,
                command.externalRequestId,
                OffsetDateTime.now(),
            )

        reservationLogRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return changeWithRetry(command, requestKey)
    }

    // CHANGE는 낙관적 락 충돌(재시도 대상)과 exclusion 제약 위반(진짜 겹침, 재시도해도 다시 겹침)을
    // 함께 다루는 첫 액션이다. 두 예외를 서로 다르게 처리해야 하므로 재시도 루프 안에서 타입별로 분기한다.
    private fun changeWithRetry(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationLog {
        var attempt = 1
        while (true) {
            try {
                return attemptChange(command, requestKey)
            } catch (staleVersion: ObjectOptimisticLockingFailureException) {
                if (attempt >= CHANGE_MAX_ATTEMPTS) throw staleVersion
                attempt++
            } catch (overlapViolation: DataIntegrityViolationException) {
                return recordChangeRejected(command, requestKey)
            }
        }
    }

    private fun attemptChange(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationLog =
        checkNotNull(
            transactionTemplate.execute {
                val reservation =
                    reservationRepository.findByPlatformIdAndPlatformReservationRef(
                        command.platformId,
                        command.platformReservationRef,
                    )

                if (reservation == null) {
                    reservationLogRepository
                        .saveAndFlush(ReservationLogEntity.from(ReservationLog.changeNotFound(requestKey, command)))
                        .toDomain()
                } else {
                    when (reservation.status) {
                        ReservationStatus.PENDING_CANCEL, ReservationStatus.CANCELLED ->
                            reservationLogRepository
                                .saveAndFlush(
                                    ReservationLogEntity.from(
                                        ReservationLog.changeNotChangeable(requestKey, command, reservation.toDomain()),
                                    ),
                                ).toDomain()

                        ReservationStatus.CONFIRMED -> confirmChange(command, requestKey, reservation)
                    }
                }
            },
        ) { "CHANGE 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmChange(
        command: ChangeReservationCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationLog {
        val now = OffsetDateTime.now()
        // dateRange를 바꾸기 전 스냅샷 — ReservationLog.changed와 알림의 oldDateRange로 그대로 쓴다.
        val originalReservation = reservation.toDomain()

        reservation.dateRange = command.newDateRange.toRange()
        reservationRepository.saveAndFlush(reservation)

        val savedRequest =
            reservationLogRepository.saveAndFlush(
                ReservationLogEntity.from(ReservationLog.changed(requestKey, command, originalReservation, now)),
            )

        outboundNotificationRepository.saveAndFlush(
            changedNotificationEntity(requestKey, originalReservation, command.newDateRange, now),
        )

        return savedRequest.toDomain()
    }

    // uq_request_key 위반으로 여기서 예외가 나면 트랜잭션(과 세션)은 이미 롤백된 상태다. BOOK의
    // recordConflict와 동일하게, 같은 세션을 재사용하지 않도록 예외를 트랜잭션 바깥에서 잡아 재조회한다.
    private fun recordChangeRejected(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationLog =
        try {
            checkNotNull(
                transactionTemplate.execute {
                    val reservation =
                        checkNotNull(
                            reservationRepository.findByPlatformIdAndPlatformReservationRef(
                                command.platformId,
                                command.platformReservationRef,
                            ),
                        ) { "겹침으로 거부하기 직전까지 존재가 확인된 예약이 재조회 시점에 사라질 수 없습니다" }.toDomain()
                    val now = OffsetDateTime.now()

                    val rejectedRequest =
                        reservationLogRepository.saveAndFlush(
                            ReservationLogEntity.from(
                                ReservationLog.changeConflict(
                                    requestKey,
                                    command,
                                    reservation,
                                    now
                                )
                            ),
                        )

                    outboundNotificationRepository.saveAndFlush(
                        changeRejectedNotificationEntity(requestKey, reservation, command.newDateRange, now),
                    )

                    rejectedRequest.toDomain()
                },
            ) { "거부 기록 트랜잭션은 항상 값을 반환해야 합니다" }
        } catch (_: DataIntegrityViolationException) {
            checkNotNull(reservationLogRepository.findByRequestKey(requestKey)) {
                "uq_request_key 위반이면 동일 requestKey row가 반드시 존재해야 합니다"
            }.toDomain()
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ReservationService::class.java)
        private const val CANCEL_CONFIRM_MAX_ATTEMPTS = 3
        private const val CANCEL_REQUEST_MAX_ATTEMPTS = 3
        private const val CHANGE_MAX_ATTEMPTS = 3
    }
}
