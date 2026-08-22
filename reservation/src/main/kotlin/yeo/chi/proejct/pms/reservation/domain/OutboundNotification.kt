package yeo.chi.proejct.pms.reservation.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime

data class OutboundNotification(
    val notificationKey: String,
    // BOOK이 겹침으로 거부된 경우 예약 row 자체가 없어 null일 수 있다(payload에 reservationNo를
    // 담아 발신 시 대체한다).
    val reservationCode: String?,
    val requestKey: String,
    val eventType: String,
    val payload: String,
    val status: OutboundNotificationStatus,
    val retryCount: Int,
    val nextRetryAt: OffsetDateTime,
) {
    companion object {
        private const val RESERVATION_CONFIRMED_EVENT_TYPE = "RESERVATION_CONFIRMED"
        private const val RESERVATION_CANCELLED_EVENT_TYPE = "RESERVATION_CANCELLED"
        private const val CANCEL_REQUESTED_EVENT_TYPE = "CANCEL_REQUESTED"
        private const val RESERVATION_CHANGED_EVENT_TYPE = "RESERVATION_CHANGED"
        private const val RESERVATION_REJECTED_EVENT_TYPE = "RESERVATION_REJECTED"

        fun from(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = OutboundNotification(
            notificationKey = "${reservation.reservationCode}:$RESERVATION_CONFIRMED_EVENT_TYPE",
            reservationCode = reservation.reservationCode,
            requestKey = requestKey,
            eventType = RESERVATION_CONFIRMED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(ReservationConfirmedPayload.from(reservation)),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
        )

        // CANCEL_CONFIRM 성공 시 취소통보. CANCEL_CONFIRM은 status만 CANCELLED로 바꿀 뿐 dateRange는
        // 건드리지 않으므로, reservation은 저장 전/후 어느 시점 것을 넘겨도 동일하다.
        fun cancelled(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = OutboundNotification(
            notificationKey = "${reservation.reservationCode}:$RESERVATION_CANCELLED_EVENT_TYPE",
            reservationCode = reservation.reservationCode,
            requestKey = requestKey,
            eventType = RESERVATION_CANCELLED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(ReservationCancelledPayload.from(reservation)),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
        )

        // CANCEL_REQUEST 성공 시 호스트발 취소요청 통보. 마찬가지로 status만 PENDING_CANCEL로 바뀔 뿐
        // dateRange는 그대로다.
        fun cancelRequested(
            requestKey: String,
            reservation: Reservation,
            reason: CancelRequestReason,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = OutboundNotification(
            notificationKey = "${reservation.reservationCode}:$CANCEL_REQUESTED_EVENT_TYPE",
            reservationCode = reservation.reservationCode,
            requestKey = requestKey,
            eventType = CANCEL_REQUESTED_EVENT_TYPE,
            payload = objectMapper.writeValueAsString(ReservationCancelRequestedPayload.from(reservation, reason)),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
        )

        // BOOK이 겹침으로 거부된 경우 예약 row가 없어 reservationCode는 null이다. CHANGE의 거부 알림과 같은
        // 이유로 requestKey를 notification_key에 포함한다 — BOOK의 request_key가 이미
        // platformReservationRef를 포함하므로(#14) 이론적으로는 reservationNo만으로도 유일하지만, 그
        // 전제에 기대지 않고 CHANGE와 동일하게 안전한 패턴을 그대로 따른다.
        fun rejected(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotification {
            val payload = ReservationRejectedPayload.from(reservation)
            return OutboundNotification(
                notificationKey = "${payload.reservationNo}:$RESERVATION_REJECTED_EVENT_TYPE:$requestKey",
                reservationCode = null,
                requestKey = requestKey,
                eventType = RESERVATION_REJECTED_EVENT_TYPE,
                payload = objectMapper.writeValueAsString(payload),
                status = OutboundNotificationStatus.PENDING,
                retryCount = 0,
                nextRetryAt = now,
            )
        }

        // CHANGE 성공. reservation은 dateRange를 바꾸기 "전" 스냅샷이어야 한다 — oldDateRange로 그대로 쓴다.
        fun changed(
            requestKey: String,
            reservation: Reservation,
            newDateRange: ReservationDateRange,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = changeEvent(RESERVATION_CHANGED_EVENT_TYPE, requestKey, reservation, newDateRange, now, objectMapper)

        // CHANGE의 새 날짜가 다른 예약과 겹쳐 excl_room_date_overlap 위반으로 거부된 경우.
        fun changeRejected(
            requestKey: String,
            reservation: Reservation,
            newDateRange: ReservationDateRange,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = changeEvent(RESERVATION_REJECTED_EVENT_TYPE, requestKey, reservation, newDateRange, now, objectMapper)

        // notification_key 유일성: CHANGE는 같은 예약에 대해 여러 번 성공/거부될 수 있는 첫 액션이라, 다른
        // 액션들이 쓰는 "reservationNo:eventType" 형식만으로는 uq_notification_key 위반이 난다. requestKey
        // (항상 새로 생성되는 값)를 포함해 이벤트 단위로 유일성을 보장한다.
        private fun changeEvent(
            eventType: String,
            requestKey: String,
            reservation: Reservation,
            newDateRange: ReservationDateRange,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ) = OutboundNotification(
            notificationKey = "${reservation.reservationCode}:$eventType:$requestKey",
            reservationCode = reservation.reservationCode,
            requestKey = requestKey,
            eventType = eventType,
            payload = objectMapper.writeValueAsString(ReservationChangedPayload.from(reservation, newDateRange)),
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
        )
    }
}

enum class OutboundNotificationStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD,
}

data class ReservationConfirmedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    companion object {
        fun from(reservation: Reservation) = ReservationConfirmedPayload(
            reservationNo = reservation.reservationCode,
            platformId = reservation.platformId,
            roomCode = reservation.roomId,
            startDate = reservation.dateRange.startDate,
            endDate = reservation.dateRange.endDate,
        )
    }
}

data class ReservationCancelledPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    companion object {
        fun from(reservation: Reservation) = ReservationCancelledPayload(
            reservationNo = reservation.reservationCode,
            platformId = reservation.platformId,
            roomCode = reservation.roomId,
            startDate = reservation.dateRange.startDate,
            endDate = reservation.dateRange.endDate,
        )
    }
}

data class ReservationCancelRequestedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: CancelRequestReason,
) {
    companion object {
        fun from(
            reservation: Reservation,
            reason: CancelRequestReason,
        ) = ReservationCancelRequestedPayload(
            reservationNo = reservation.reservationCode,
            platformId = reservation.platformId,
            roomCode = reservation.roomId,
            startDate = reservation.dateRange.startDate,
            endDate = reservation.dateRange.endDate,
            reason = reason,
        )
    }
}

data class ReservationChangedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val oldStartDate: LocalDate,
    val oldEndDate: LocalDate,
    val newStartDate: LocalDate,
    val newEndDate: LocalDate,
) {
    companion object {
        fun from(
            reservation: Reservation,
            newDateRange: ReservationDateRange,
        ) = ReservationChangedPayload(
            reservationNo = reservation.reservationCode,
            platformId = reservation.platformId,
            roomCode = reservation.roomId,
            oldStartDate = reservation.dateRange.startDate,
            oldEndDate = reservation.dateRange.endDate,
            newStartDate = newDateRange.startDate,
            newEndDate = newDateRange.endDate,
        )
    }
}

// BOOK이 겹침으로 거부된 경우 예약 row가 없어 reservationNo를 DB에서 읽을 수 없다. reservations.reservation_code는
// platform_id + ':' + platform_reservation_ref로 생성되는 컬럼이라, 저장된 적 없는 예약이라도 저장됐더라면
// 가졌을 값을 커맨드만으로 그대로 계산할 수 있다.
data class ReservationRejectedPayload(
    val reservationNo: String,
    val platformId: String,
    val roomCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rejectReason: String,
) {
    companion object {
        private const val DUPLICATE_BOOKING_REJECT_REASON = "DUPLICATE_BOOKING"

        fun from(reservation: Reservation) = ReservationRejectedPayload(
            reservationNo = "${reservation.platformId}:${reservation.platformReservationRef}",
            platformId = reservation.platformId,
            roomCode = reservation.roomId,
            startDate = reservation.dateRange.startDate,
            endDate = reservation.dateRange.endDate,
            rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
        )
    }
}
