package yeo.chi.proejct.pms.reservation.controller

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.service.BookReservationCommand
import java.time.LocalDate

data class BookReservationRequest(
    @field:NotBlank val platformId: String,
    @field:NotBlank val platformReservationRef: String,
    @field:NotBlank val roomCode: String,
    @field:NotNull val startDate: LocalDate?,
    @field:NotNull val endDate: LocalDate?,
    @field:NotNull val initiatedBy: RequestInitiator?,
) {
    @get:AssertTrue(message = "startDate는 endDate보다 이전이어야 합니다")
    val isDateRangeOrdered: Boolean
        get() = startDate == null || endDate == null || startDate.isBefore(endDate)
}

fun BookReservationRequest.toCommand(): BookReservationCommand =
    BookReservationCommand(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        roomCode = roomCode,
        dateRange = ReservationDateRange(requireNotNull(startDate), requireNotNull(endDate)),
        initiatedBy = requireNotNull(initiatedBy),
    )
