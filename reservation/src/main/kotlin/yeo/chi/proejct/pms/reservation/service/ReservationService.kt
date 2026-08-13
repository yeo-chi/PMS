package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationRequestAction
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationEntity
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationRequestRepository
import yeo.chi.proejct.pms.reservation.persistent.toDomain
import yeo.chi.proejct.pms.reservation.persistent.toEntity
import java.time.LocalDate
import java.time.OffsetDateTime

private const val DUPLICATE_BOOKING_REJECT_REASON = "DUPLICATE_BOOKING"
private const val RESERVATION_CONFIRMED_EVENT_TYPE = "RESERVATION_CONFIRMED"

private data class ReservationConfirmedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
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
        val conflictRequest =
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
                requestedAt = OffsetDateTime.now(),
            )

        // uq_request_key 위반으로 여기서 예외가 나면 트랜잭션(과 그 Hibernate 세션)은 이미 롤백된 상태다.
        // 같은 세션을 재사용해 재조회하면 "세션이 예외 이후에 flush됨" 문제로 이어지므로,
        // 예외를 트랜잭션 바깥으로 흘려보낸 뒤 별도 트랜잭션으로 재조회한다.
        return try {
            checkNotNull(
                transactionTemplate.execute {
                    reservationRequestRepository.saveAndFlush(conflictRequest.toEntity()).toDomain()
                },
            ) { "충돌 기록 트랜잭션은 항상 값을 반환해야 합니다" }
        } catch (duplicateRequestKey: DataIntegrityViolationException) {
            checkNotNull(reservationRequestRepository.findByRequestKey(requestKey)) {
                "uq_request_key 위반이면 동일 requestKey row가 반드시 존재해야 합니다"
            }.toDomain()
        }
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
}
