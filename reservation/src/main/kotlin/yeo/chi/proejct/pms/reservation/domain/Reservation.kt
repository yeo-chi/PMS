package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime
import java.util.*

data class Reservation(
    val reservationCode: String,
    val platformId: String,
    val platformReservationRef: String,
    val roomId: String,
    val dateRange: ReservationDateRange,
    val status: ReservationStatus,
    val version: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(
            command: BookReservationCommand,
            createdAt: OffsetDateTime = OffsetDateTime.now(),
        ): Reservation =
            Reservation(
                reservationCode = UUID.randomUUID().toString(),
                platformId = command.platformId,
                platformReservationRef = command.platformReservationRef,
                roomId = command.roomId,
                dateRange = command.dateRange,
                status = ReservationStatus.CONFIRMED,
                version = 1,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
    }
}

enum class ReservationStatus {
    CONFIRMED,
    PENDING_CANCEL,
    CANCELLED,
}
