package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime

data class Reservation(
    val id: Long?,
    val reservationNo: String?,
    val platformId: String,
    val platformReservationRef: String,
    val roomCode: String,
    val dateRange: ReservationDateRange,
    val status: ReservationStatus,
    val version: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun createNew(
            platformId: String,
            platformReservationRef: String,
            roomCode: String,
            dateRange: ReservationDateRange,
            createdAt: OffsetDateTime,
        ): Reservation =
            Reservation(
                id = null,
                reservationNo = null,
                platformId = platformId,
                platformReservationRef = platformReservationRef,
                roomCode = roomCode,
                dateRange = dateRange,
                status = ReservationStatus.CONFIRMED,
                version = 1,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
    }
}
