package yeo.chi.proejct.pms.reservation.domain

import java.time.LocalDate

// PostgreSQL daterange의 [startDate, endDate) 반개구간 의미론: endDate(체크아웃일)는 exclusive.
data class ReservationDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
)
