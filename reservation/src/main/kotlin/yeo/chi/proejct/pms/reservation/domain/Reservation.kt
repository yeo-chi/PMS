package yeo.chi.proejct.pms.reservation.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Reservation(
    val platformCode: String,
    val roomId: Long,
    val userIdentifyCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reservedAt: LocalDateTime,
    val code: String,
    val status: ReservationStatus = ReservationStatus.CONFIRMED,
    val id: Long? = null,
)

enum class ReservationStatus(val description: String) {
    CONFIRMED("예약 확정"),
    CANCELLED("예약 취소"),
}
