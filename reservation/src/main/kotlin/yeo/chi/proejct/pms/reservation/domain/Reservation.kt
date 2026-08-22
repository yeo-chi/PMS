package yeo.chi.proejct.pms.reservation.domain

data class Reservation(
    val reservationCode: String = TEMP,
    val platformId: String,
    val platformReservationRef: String,
    val roomId: String,
    val dateRange: ReservationDateRange,
    val status: ReservationStatus,
    val initiatedBy: RequestInitiator,
) {
    init {
        require(initiatedBy.isNotHost()) { "HOST는 예약 요청(BOOK)을 시작할 수 없습니다" }
    }

    companion object {
        private const val TEMP = "TEMP"
    }

    fun logKey() = "$platformId:$platformReservationRef:$roomId:${dateRange.startDate}:${dateRange.endDate}:BOOK"
}

enum class ReservationStatus {
    CONFIRMED,
    PENDING_CANCEL,
    CANCELLED,
}
