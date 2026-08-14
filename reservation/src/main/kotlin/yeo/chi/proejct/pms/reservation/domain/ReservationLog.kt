package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime

data class ReservationLog(
    val id: Long?,
    val requestKey: String,
    val reservationCode: String?,
    val platformId: String,
    val action: ReservationLogAction,
    val initiatedBy: RequestInitiator,
    val roomCode: String?,
    val oldDateRange: ReservationDateRange?,
    val newDateRange: ReservationDateRange?,
    val resultStatus: RequestResultStatus,
    val rejectReason: String?,
    val requestedAt: OffsetDateTime,
) {
    companion object {
        private const val DUPLICATE_BOOKING_REJECT_REASON = "DUPLICATE_BOOKING"
        private const val RESERVATION_NOT_FOUND_REJECT_REASON = "RESERVATION_NOT_FOUND"
        private const val ALREADY_CANCELLED_REJECT_REASON = "ALREADY_CANCELLED"
        private const val RESERVATION_NOT_CHANGEABLE_REJECT_REASON = "RESERVATION_NOT_CHANGEABLE"

        fun booked(
            requestKey: String,
            reservation: Reservation,
            initiatedBy: RequestInitiator,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.BOOK,
            initiatedBy = initiatedBy,
            roomCode = reservation.roomId,
            oldDateRange = null,
            newDateRange = reservation.dateRange,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = reservation.createdAt,
        )

        // BOOK이 겹침(uq_request_key가 아니라 excl_room_date_overlap)으로 거부된 경우. 예약 row 자체가
        // 없어 reservationCode/roomCode는 command에서만 얻을 수 있다.
        fun bookConflict(
            requestKey: String,
            command: BookReservationCommand,
            now: OffsetDateTime,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = null,
            platformId = command.platformId,
            action = ReservationLogAction.BOOK,
            initiatedBy = command.initiatedBy,
            roomCode = command.roomId,
            oldDateRange = null,
            newDateRange = command.dateRange,
            resultStatus = RequestResultStatus.CONFLICT,
            rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
            requestedAt = now,
        )

        // CANCEL_CONFIRM은 status만 CANCELLED로 바꿀 뿐 dateRange는 건드리지 않으므로, reservation은
        // 저장 전/후 어느 시점 것을 넘겨도 oldDateRange로 그대로 쓸 수 있다.
        fun cancelConfirmed(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CANCEL_CONFIRM,
            initiatedBy = RequestInitiator.OTA,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = now,
        )

        fun cancelConfirmNotFound(
            requestKey: String,
            command: CancelConfirmCommand,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = null,
            platformId = command.platformId,
            action = ReservationLogAction.CANCEL_CONFIRM,
            initiatedBy = RequestInitiator.OTA,
            roomCode = null,
            oldDateRange = null,
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = now,
        )

        // 이미 CANCELLED인 예약에 재통보가 온 경우: 재전이는 없지만 감사 기록은 SUCCESS로 추가로 남긴다.
        fun cancelConfirmAlreadyCancelled(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CANCEL_CONFIRM,
            initiatedBy = RequestInitiator.OTA,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = now,
        )

        // CANCEL_REQUEST(PENDING_CANCEL 전이)도 status만 바꿀 뿐 dateRange는 건드리지 않는다.
        fun cancelRequested(
            requestKey: String,
            reservation: Reservation,
            reason: CancelRequestReason,
            now: OffsetDateTime,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = reason.name,
            requestedAt = now,
        )

        // reservation_requests.platform_id는 NOT NULL이라 존재하지 않는 reservationId로 온 취소요청은
        // 어떤 채널과도 연관지을 수 없어 감사 로그 row를 만들 수 없다 — 합성된(비영속) 결과만 반환한다.
        fun cancelRequestNotFound(
            requestKey: String,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = null,
            platformId = "",
            action = ReservationLogAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = null,
            oldDateRange = null,
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = now,
        )

        fun cancelRequestAlreadyPending(
            requestKey: String,
            reservation: Reservation,
            reason: CancelRequestReason,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = reason.name,
            requestedAt = now,
        )

        fun cancelRequestAlreadyCancelled(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CANCEL_REQUEST,
            initiatedBy = RequestInitiator.HOST,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = null,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = ALREADY_CANCELLED_REJECT_REASON,
            requestedAt = now,
        )

        // reservation은 dateRange를 바꾸기 "전" 스냅샷이어야 한다 — oldDateRange를 여기서 그대로 읽는다.
        fun changed(
            requestKey: String,
            command: ChangeReservationCommand,
            reservation: Reservation,
            now: OffsetDateTime,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = now,
        )

        // CHANGE의 새 날짜가 다른 예약과 겹쳐 excl_room_date_overlap 위반으로 거부된 경우.
        fun changeConflict(
            requestKey: String,
            command: ChangeReservationCommand,
            reservation: Reservation,
            now: OffsetDateTime,
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.CONFLICT,
            rejectReason = DUPLICATE_BOOKING_REJECT_REASON,
            requestedAt = now,
        )

        fun changeNotFound(
            requestKey: String,
            command: ChangeReservationCommand,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = null,
            platformId = command.platformId,
            action = ReservationLogAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = null,
            oldDateRange = null,
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_FOUND_REJECT_REASON,
            requestedAt = now,
        )

        fun changeNotChangeable(
            requestKey: String,
            command: ChangeReservationCommand,
            reservation: Reservation,
            now: OffsetDateTime = OffsetDateTime.now(),
        ) = ReservationLog(
            id = null,
            requestKey = requestKey,
            reservationCode = reservation.reservationCode,
            platformId = reservation.platformId,
            action = ReservationLogAction.CHANGE,
            initiatedBy = command.initiatedBy,
            roomCode = reservation.roomId,
            oldDateRange = reservation.dateRange,
            newDateRange = command.newDateRange,
            resultStatus = RequestResultStatus.FAILED,
            rejectReason = RESERVATION_NOT_CHANGEABLE_REJECT_REASON,
            requestedAt = now,
        )
    }
}

enum class ReservationLogAction {
    BOOK,
    CHANGE,
    CANCEL_REQUEST,
    CANCEL_CONFIRM,
}
