package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationRequestAction
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationEntity
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationRequestRepository
import yeo.chi.proejct.pms.reservation.persistent.toDomain
import yeo.chi.proejct.pms.reservation.persistent.toEntity
import yeo.chi.proejct.pms.reservation.persistent.toRange
import yeo.chi.proejct.pms.reservation.persistent.toReservationDateRange
import java.time.LocalDate
import java.time.OffsetDateTime

private const val DUPLICATE_BOOKING_REJECT_REASON = "DUPLICATE_BOOKING"
private const val RESERVATION_NOT_FOUND_REJECT_REASON = "RESERVATION_NOT_FOUND"
private const val ALREADY_CANCELLED_REJECT_REASON = "ALREADY_CANCELLED"
private const val RESERVATION_NOT_CHANGEABLE_REJECT_REASON = "RESERVATION_NOT_CHANGEABLE"
private const val RESERVATION_CONFIRMED_EVENT_TYPE = "RESERVATION_CONFIRMED"
private const val RESERVATION_CANCELLED_EVENT_TYPE = "RESERVATION_CANCELLED"
private const val CANCEL_REQUESTED_EVENT_TYPE = "CANCEL_REQUESTED"
private const val RESERVATION_CHANGED_EVENT_TYPE = "RESERVATION_CHANGED"
private const val RESERVATION_REJECTED_EVENT_TYPE = "RESERVATION_REJECTED"
private const val CANCEL_CONFIRM_MAX_ATTEMPTS = 3
private const val CANCEL_REQUEST_MAX_ATTEMPTS = 3
private const val CHANGE_MAX_ATTEMPTS = 3

private data class ReservationConfirmedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

private data class ReservationCancelledPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

private data class ReservationCancelRequestedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: CancelRequestReason,
)

private data class ReservationChangedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val oldStartDate: LocalDate,
    val oldEndDate: LocalDate,
    val newStartDate: LocalDate,
    val newEndDate: LocalDate,
)

// BOOK이 겹침으로 거부된 경우 예약 row가 없어 reservationNo를 DB에서 읽을 수 없다. reservations.reservation_no는
// platform_id + ':' + platform_reservation_ref로 생성되는 컬럼이라, 저장된 적 없는 예약이라도 저장됐더라면
// 가졌을 값을 커맨드만으로 그대로 계산할 수 있다.
private data class ReservationRejectedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rejectReason: String,
)

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val reservationRequestRepository: ReservationRequestRepository,
    private val outboundNotificationRepository: OutboundNotificationRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun book(command: BookReservationCommand): ReservationRequest {
        require(command.initiatedBy != RequestInitiator.HOST) {
            "HOST는 예약 요청(BOOK)을 시작할 수 없습니다: initiatedBy=${command.initiatedBy}"
        }

        val requestKey =
            buildBookRequestKey(command.platformId, command.platformReservationRef, command.roomCode, command.dateRange)

        reservationRequestRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return try {
            attemptBook(command, requestKey)
        } catch (overlapViolation: DataIntegrityViolationException) {
            recordConflict(command, requestKey)
        }
    }

    private fun attemptBook(
        command: BookReservationCommand,
        requestKey: String,
    ): ReservationRequest =
        checkNotNull(
            transactionTemplate.execute {
                val now = OffsetDateTime.now()

                val savedReservation =
                    reservationRepository.saveAndFlush(
                        Reservation
                            .createNew(
                                platformId = command.platformId,
                                platformReservationRef = command.platformReservationRef,
                                roomCode = command.roomCode,
                                dateRange = command.dateRange,
                                createdAt = now,
                            ).toEntity(),
                    )

                val savedRequest =
                    reservationRequestRepository.saveAndFlush(
                        ReservationRequest(
                            id = null,
                            requestKey = requestKey,
                            reservationId = savedReservation.id,
                            platformId = command.platformId,
                            action = ReservationRequestAction.BOOK,
                            initiatedBy = command.initiatedBy,
                            roomCode = command.roomCode,
                            oldDateRange = null,
                            newDateRange = command.dateRange,
                            resultStatus = RequestResultStatus.SUCCESS,
                            rejectReason = null,
                            requestedAt = now,
                        ).toEntity(),
                    )

                val reservationNo =
                    requireNotNull(savedReservation.reservationNo) {
                        "저장 직후 reservation_no는 채워져 있어야 합니다"
                    }
                outboundNotificationRepository.saveAndFlush(
                    confirmedNotificationEntity(savedReservation.id, savedRequest.id, reservationNo, command, now),
                )

                savedRequest.toDomain()
            },
        ) { "예약 확정 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun recordConflict(
        command: BookReservationCommand,
        requestKey: String,
    ): ReservationRequest {
        // uq_request_key 위반으로 여기서 예외가 나면 트랜잭션(과 그 Hibernate 세션)은 이미 롤백된 상태다.
        // 같은 세션을 재사용해 재조회하면 "세션이 예외 이후에 flush됨" 문제로 이어지므로,
        // 예외를 트랜잭션 바깥으로 흘려보낸 뒤 별도 트랜잭션으로 재조회한다.
        return try {
            checkNotNull(
                transactionTemplate.execute {
                    val now = OffsetDateTime.now()
                    val savedRequest =
                        reservationRequestRepository.saveAndFlush(
                            ReservationRequest(
                                id = null,
                                requestKey = requestKey,
                                reservationId = null,
                                platformId = command.platformId,
                                action = ReservationRequestAction.BOOK,
                                initiatedBy = command.initiatedBy,
                                roomCode = command.roomCode,
                                oldDateRange = null,
                                newDateRange = command.dateRange,
                                resultStatus = RequestResultStatus.CONFLICT,
                                rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
                                requestedAt = now,
                            ).toEntity(),
                        )

                    // 패자 채널에도 거부 통보를 남긴다(기획문서 4.1). 예약 row가 없어 reservation_id는
                    // null — reservationNo는 payload 안에 담아 발신 시 대체한다(아래 rejectedNotificationEntity).
                    outboundNotificationRepository.saveAndFlush(
                        rejectedNotificationEntity(command, savedRequest.id, now),
                    )

                    savedRequest.toDomain()
                },
            ) { "충돌 기록 트랜잭션은 항상 값을 반환해야 합니다" }
        } catch (duplicateRequestKey: DataIntegrityViolationException) {
            checkNotNull(reservationRequestRepository.findByRequestKey(requestKey)) {
                "uq_request_key 위반이면 동일 requestKey row가 반드시 존재해야 합니다"
            }.toDomain()
        }
    }

    fun cancelConfirm(command: CancelConfirmCommand): ReservationRequest {
        val requestKey =
            buildCancelConfirmRequestKey(
                command.platformId,
                command.platformReservationRef,
                command.externalRequestId,
                OffsetDateTime.now(),
            )

        reservationRequestRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return cancelConfirmWithRetry(command, requestKey)
    }

    // 낙관적 락 충돌(ObjectOptimisticLockingFailureException)은 attemptCancelConfirm의 트랜잭션 "밖"에서 잡는다.
    // BOOK의 recordConflict와 같은 이유로, 실패한 트랜잭션의 Hibernate 세션을 재사용하면 예외 이후 flush 문제가 생긴다.
    private fun cancelConfirmWithRetry(
        command: CancelConfirmCommand,
        requestKey: String,
    ): ReservationRequest {
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
    ): ReservationRequest =
        checkNotNull(
            transactionTemplate.execute {
                val reservation =
                    reservationRepository.findByPlatformIdAndPlatformReservationRef(
                        command.platformId,
                        command.platformReservationRef,
                    )

                if (reservation == null) {
                    reservationRequestRepository
                        .saveAndFlush(reservationNotFoundRequest(command, requestKey).toEntity())
                        .toDomain()
                } else {
                    when (reservation.status) {
                        ReservationStatus.CANCELLED ->
                            reservationRequestRepository
                                .saveAndFlush(alreadyCancelledRequest(command, requestKey, reservation).toEntity())
                                .toDomain()

                        ReservationStatus.CONFIRMED, ReservationStatus.PENDING_CANCEL ->
                            confirmCancellation(command, requestKey, reservation)
                    }
                }
            },
        ) { "CANCEL_CONFIRM 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmCancellation(
        command: CancelConfirmCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest {
        val now = OffsetDateTime.now()
        val previousDateRange = reservation.dateRange.toReservationDateRange()

        reservation.status = ReservationStatus.CANCELLED
        val savedReservation = reservationRepository.saveAndFlush(reservation)

        val savedRequest =
            reservationRequestRepository.saveAndFlush(
                ReservationRequest(
                    id = null,
                    requestKey = requestKey,
                    reservationId = savedReservation.id,
                    platformId = command.platformId,
                    action = ReservationRequestAction.CANCEL_CONFIRM,
                    initiatedBy = RequestInitiator.OTA,
                    roomCode = savedReservation.roomCode,
                    oldDateRange = previousDateRange,
                    newDateRange = null,
                    resultStatus = RequestResultStatus.SUCCESS,
                    rejectReason = null,
                    requestedAt = now,
                ).toEntity(),
            )

        outboundNotificationRepository.saveAndFlush(
            cancelledNotificationEntity(savedReservation, savedRequest.id, previousDateRange, now),
        )

        return savedRequest.toDomain()
    }

    private fun reservationNotFoundRequest(
        command: CancelConfirmCommand,
        requestKey: String,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = null,
            platformId = command.platformId,
            action = ReservationRequestAction.CANCEL_CONFIRM,
            initiatedBy = RequestInitiator.OTA,
            roomCode = null,
            oldDateRange = null,
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = OffsetDateTime.now(),
        )

    private fun alreadyCancelledRequest(
        command: CancelConfirmCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = reservation.id,
            platformId = command.platformId,
            action = ReservationRequestAction.CANCEL_CONFIRM,
            initiatedBy = RequestInitiator.OTA,
            roomCode = reservation.roomCode,
            oldDateRange = reservation.dateRange.toReservationDateRange(),
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = OffsetDateTime.now(),
        )

    private fun cancelledNotificationEntity(
        reservation: ReservationEntity,
        requestId: Long,
        previousDateRange: ReservationDateRange,
        now: OffsetDateTime,
    ): OutboundNotificationEntity {
        val reservationNo =
            requireNotNull(reservation.reservationNo) { "저장된 예약은 reservation_no를 가지고 있어야 합니다" }
        val payload =
            ReservationCancelledPayload(
                reservationNo = reservationNo,
                platformId = reservation.platformId,
                roomCode = reservation.roomCode,
                startDate = previousDateRange.startDate,
                endDate = previousDateRange.endDate,
            )

        return OutboundNotification(
            id = null,
            notificationKey = "$reservationNo:$RESERVATION_CANCELLED_EVENT_TYPE",
            reservationId = reservation.id,
            requestId = requestId,
            eventType = RESERVATION_CANCELLED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
    }

    fun cancelRequest(command: CancelRequestCommand): ReservationRequest {
        val requestKey = buildCancelRequestKey(command.reservationId, command.externalRequestId, OffsetDateTime.now())

        reservationRequestRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return cancelRequestWithRetry(command, requestKey)
    }

    // 낙관적 락 충돌은 attemptCancelRequest의 트랜잭션 "밖"에서 잡는다 (#15의 cancelConfirm과 동일한 이유 —
    // 실패한 트랜잭션의 Hibernate 세션을 재사용하면 예외 이후 flush 문제가 생긴다).
    private fun cancelRequestWithRetry(
        command: CancelRequestCommand,
        requestKey: String,
    ): ReservationRequest {
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
    ): ReservationRequest =
        checkNotNull(
            transactionTemplate.execute {
                val reservation = reservationRepository.findById(command.reservationId).orElse(null)

                if (reservation == null) {
                    // reservation_requests.platform_id는 NOT NULL이지만, 호스트가 존재하지 않는 reservationId로
                    // 요청한 경우 어떤 채널(platformId)과도 연관지을 수 없어 감사 로그 row를 만들 수 없다.
                    // DB에는 아무것도 남기지 않고 합성된(비영속) FAILED 결과만 반환한다.
                    reservationNotFoundResult(requestKey)
                } else {
                    when (reservation.status) {
                        ReservationStatus.CONFIRMED -> confirmCancelRequest(command, requestKey, reservation)

                        ReservationStatus.PENDING_CANCEL ->
                            reservationRequestRepository
                                .saveAndFlush(alreadyPendingCancelRequest(command, requestKey, reservation).toEntity())
                                .toDomain()

                        ReservationStatus.CANCELLED ->
                            reservationRequestRepository
                                .saveAndFlush(alreadyCancelledCancelRequest(requestKey, reservation).toEntity())
                                .toDomain()
                    }
                }
            },
        ) { "CANCEL_REQUEST 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmCancelRequest(
        command: CancelRequestCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest {
        val now = OffsetDateTime.now()
        val currentDateRange = reservation.dateRange.toReservationDateRange()

        reservation.status = ReservationStatus.PENDING_CANCEL
        val savedReservation = reservationRepository.saveAndFlush(reservation)

        val savedRequest =
            reservationRequestRepository.saveAndFlush(
                ReservationRequest(
                    id = null,
                    requestKey = requestKey,
                    reservationId = savedReservation.id,
                    platformId = savedReservation.platformId,
                    action = ReservationRequestAction.CANCEL_REQUEST,
                    initiatedBy = RequestInitiator.HOST,
                    roomCode = savedReservation.roomCode,
                    oldDateRange = currentDateRange,
                    newDateRange = null,
                    resultStatus = RequestResultStatus.SUCCESS,
                    rejectReason = command.reason.name,
                    requestedAt = now,
                ).toEntity(),
            )

        outboundNotificationRepository.saveAndFlush(
            cancelRequestedNotificationEntity(savedReservation, savedRequest.id, command.reason, now),
        )

        return savedRequest.toDomain()
    }

    private fun reservationNotFoundResult(requestKey: String): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = null,
            platformId = "",
            action = ReservationRequestAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = null,
            oldDateRange = null,
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = OffsetDateTime.now(),
        )

    private fun alreadyPendingCancelRequest(
        command: CancelRequestCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = reservation.id,
            platformId = reservation.platformId,
            action = ReservationRequestAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = reservation.roomCode,
            oldDateRange = reservation.dateRange.toReservationDateRange(),
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = command.reason.name,
            requestedAt = OffsetDateTime.now(),
        )

    private fun alreadyCancelledCancelRequest(
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = reservation.id,
            platformId = reservation.platformId,
            action = ReservationRequestAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = reservation.roomCode,
            oldDateRange = reservation.dateRange.toReservationDateRange(),
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = ALREADY_CANCELLED_REJECT_REASON,
            requestedAt = OffsetDateTime.now(),
        )

    private fun cancelRequestedNotificationEntity(
        reservation: ReservationEntity,
        requestId: Long,
        reason: CancelRequestReason,
        now: OffsetDateTime,
    ): OutboundNotificationEntity {
        val reservationNo =
            requireNotNull(reservation.reservationNo) { "저장된 예약은 reservation_no를 가지고 있어야 합니다" }
        val dateRange = reservation.dateRange.toReservationDateRange()
        val payload =
            ReservationCancelRequestedPayload(
                reservationNo = reservationNo,
                platformId = reservation.platformId,
                roomCode = reservation.roomCode,
                startDate = dateRange.startDate,
                endDate = dateRange.endDate,
                reason = reason,
            )

        return OutboundNotification(
            id = null,
            notificationKey = "$reservationNo:$CANCEL_REQUESTED_EVENT_TYPE",
            reservationId = reservation.id,
            requestId = requestId,
            eventType = CANCEL_REQUESTED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
    }

    private fun confirmedNotificationEntity(
        reservationId: Long,
        requestId: Long,
        reservationNo: String,
        command: BookReservationCommand,
        now: OffsetDateTime,
    ): OutboundNotificationEntity {
        val payload =
            ReservationConfirmedPayload(
                reservationNo = reservationNo,
                platformId = command.platformId,
                roomCode = command.roomCode,
                startDate = command.dateRange.startDate,
                endDate = command.dateRange.endDate,
            )

        return OutboundNotification(
            id = null,
            notificationKey = "$reservationNo:$RESERVATION_CONFIRMED_EVENT_TYPE",
            reservationId = reservationId,
            requestId = requestId,
            eventType = RESERVATION_CONFIRMED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
    }

    private fun rejectedNotificationEntity(
        command: BookReservationCommand,
        requestId: Long,
        now: OffsetDateTime,
    ): OutboundNotificationEntity {
        val reservationNo = "${command.platformId}:${command.platformReservationRef}"
        val payload =
            ReservationRejectedPayload(
                reservationNo = reservationNo,
                platformId = command.platformId,
                roomCode = command.roomCode,
                startDate = command.dateRange.startDate,
                endDate = command.dateRange.endDate,
                rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
            )

        // CHANGE의 거부 알림과 같은 이유로 requestId를 키에 포함한다. BOOK의 request_key가 이미
        // platformReservationRef를 포함하므로(#14) 이론적으로는 reservationNo만으로도 유일하지만,
        // 그 전제에 기대지 않고 CHANGE와 동일하게 안전한 패턴을 그대로 따른다.
        return OutboundNotification(
            id = null,
            notificationKey = "$reservationNo:$RESERVATION_REJECTED_EVENT_TYPE:$requestId",
            reservationId = null,
            requestId = requestId,
            eventType = RESERVATION_REJECTED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
    }

    fun change(command: ChangeReservationCommand): ReservationRequest {
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

        reservationRequestRepository.findByRequestKey(requestKey)?.let { existingRequest ->
            return existingRequest.toDomain()
        }

        return changeWithRetry(command, requestKey)
    }

    // CHANGE는 낙관적 락 충돌(재시도 대상)과 exclusion 제약 위반(진짜 겹침, 재시도해도 다시 겹침)을
    // 함께 다루는 첫 액션이다. 두 예외를 서로 다르게 처리해야 하므로 재시도 루프 안에서 타입별로 분기한다.
    private fun changeWithRetry(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationRequest {
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
    ): ReservationRequest =
        checkNotNull(
            transactionTemplate.execute {
                val reservation =
                    reservationRepository.findByPlatformIdAndPlatformReservationRef(
                        command.platformId,
                        command.platformReservationRef,
                    )

                if (reservation == null) {
                    reservationRequestRepository
                        .saveAndFlush(changeReservationNotFoundRequest(command, requestKey).toEntity())
                        .toDomain()
                } else {
                    when (reservation.status) {
                        ReservationStatus.PENDING_CANCEL, ReservationStatus.CANCELLED ->
                            reservationRequestRepository
                                .saveAndFlush(reservationNotChangeableRequest(command, requestKey, reservation).toEntity())
                                .toDomain()

                        ReservationStatus.CONFIRMED -> confirmChange(command, requestKey, reservation)
                    }
                }
            },
        ) { "CHANGE 트랜잭션은 항상 값을 반환해야 합니다" }

    private fun confirmChange(
        command: ChangeReservationCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest {
        val now = OffsetDateTime.now()
        val oldDateRange = reservation.dateRange.toReservationDateRange()

        reservation.dateRange = command.newDateRange.toRange()
        val savedReservation = reservationRepository.saveAndFlush(reservation)

        val savedRequest =
            reservationRequestRepository.saveAndFlush(
                ReservationRequest(
                    id = null,
                    requestKey = requestKey,
                    reservationId = savedReservation.id,
                    platformId = command.platformId,
                    action = ReservationRequestAction.CHANGE,
                    initiatedBy = command.initiatedBy,
                    roomCode = savedReservation.roomCode,
                    oldDateRange = oldDateRange,
                    newDateRange = command.newDateRange,
                    resultStatus = RequestResultStatus.SUCCESS,
                    rejectReason = null,
                    requestedAt = now,
                ).toEntity(),
            )

        outboundNotificationRepository.saveAndFlush(
            changeEventNotificationEntity(
                reservation = savedReservation,
                requestId = savedRequest.id,
                eventType = RESERVATION_CHANGED_EVENT_TYPE,
                oldDateRange = oldDateRange,
                newDateRange = command.newDateRange,
                now = now,
            ),
        )

        return savedRequest.toDomain()
    }

    // uq_request_key 위반으로 여기서 예외가 나면 트랜잭션(과 세션)은 이미 롤백된 상태다. BOOK의
    // recordConflict와 동일하게, 같은 세션을 재사용하지 않도록 예외를 트랜잭션 바깥에서 잡아 재조회한다.
    private fun recordChangeRejected(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationRequest =
        try {
            checkNotNull(
                transactionTemplate.execute {
                    val reservation =
                        checkNotNull(
                            reservationRepository.findByPlatformIdAndPlatformReservationRef(
                                command.platformId,
                                command.platformReservationRef,
                            ),
                        ) { "겹침으로 거부하기 직전까지 존재가 확인된 예약이 재조회 시점에 사라질 수 없습니다" }
                    val now = OffsetDateTime.now()

                    val rejectedRequest =
                        reservationRequestRepository.saveAndFlush(
                            ReservationRequest(
                                id = null,
                                requestKey = requestKey,
                                reservationId = reservation.id,
                                platformId = command.platformId,
                                action = ReservationRequestAction.CHANGE,
                                initiatedBy = command.initiatedBy,
                                roomCode = reservation.roomCode,
                                oldDateRange = reservation.dateRange.toReservationDateRange(),
                                newDateRange = command.newDateRange,
                                resultStatus = RequestResultStatus.CONFLICT,
                                rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
                                requestedAt = now,
                            ).toEntity(),
                        )

                    outboundNotificationRepository.saveAndFlush(
                        changeEventNotificationEntity(
                            reservation = reservation,
                            requestId = rejectedRequest.id,
                            eventType = RESERVATION_REJECTED_EVENT_TYPE,
                            oldDateRange = reservation.dateRange.toReservationDateRange(),
                            newDateRange = command.newDateRange,
                            now = now,
                        ),
                    )

                    rejectedRequest.toDomain()
                },
            ) { "거부 기록 트랜잭션은 항상 값을 반환해야 합니다" }
        } catch (duplicateRequestKey: DataIntegrityViolationException) {
            checkNotNull(reservationRequestRepository.findByRequestKey(requestKey)) {
                "uq_request_key 위반이면 동일 requestKey row가 반드시 존재해야 합니다"
            }.toDomain()
        }

    private fun changeReservationNotFoundRequest(
        command: ChangeReservationCommand,
        requestKey: String,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = null,
            platformId = command.platformId,
            action = ReservationRequestAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = null,
            oldDateRange = null,
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = OffsetDateTime.now(),
        )

    private fun reservationNotChangeableRequest(
        command: ChangeReservationCommand,
        requestKey: String,
        reservation: ReservationEntity,
    ): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = reservation.id,
            platformId = command.platformId,
            action = ReservationRequestAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = reservation.roomCode,
            oldDateRange = reservation.dateRange.toReservationDateRange(),
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_CHANGEABLE_REJECT_REASON,
            requestedAt = OffsetDateTime.now(),
        )

    private fun changeEventNotificationEntity(
        reservation: ReservationEntity,
        requestId: Long,
        eventType: String,
        oldDateRange: ReservationDateRange,
        newDateRange: ReservationDateRange,
        now: OffsetDateTime,
    ): OutboundNotificationEntity {
        val reservationNo =
            requireNotNull(reservation.reservationNo) { "저장된 예약은 reservation_no를 가지고 있어야 합니다" }
        val payload =
            ReservationChangedPayload(
                reservationNo = reservationNo,
                platformId = reservation.platformId,
                roomCode = reservation.roomCode,
                oldStartDate = oldDateRange.startDate,
                oldEndDate = oldDateRange.endDate,
                newStartDate = newDateRange.startDate,
                newEndDate = newDateRange.endDate,
            )

        // notification_key 유일성: CHANGE는 같은 예약에 대해 여러 번 성공/거부될 수 있는 첫 액션이라,
        // 다른 액션들이 쓰는 "reservationNo:eventType" 형식만으로는 uq_notification_key 위반이 난다.
        // requestId(항상 새로 생성되는 값)를 포함해 이벤트 단위로 유일성을 보장한다.
        return OutboundNotification(
            id = null,
            notificationKey = "$reservationNo:$eventType:$requestId",
            reservationId = reservation.id,
            requestId = requestId,
            eventType = eventType,
            payload = objectMapper.writeValueAsString(payload),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        ).toEntity()
    }
}
