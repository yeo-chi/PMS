package yeo.chi.proejct.pms.reservation.controller

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.service.ChangeReservationCommand
import java.time.LocalDate

data class ChangeReservationRequest(
    @field:NotBlank val platformId: String,
    @field:NotBlank val platformReservationRef: String,
    @field:NotNull val newStartDate: LocalDate?,
    @field:NotNull val newEndDate: LocalDate?,
    @field:NotNull val initiatedBy: RequestInitiator?,
    val externalRequestId: String? = null,
) {
    @get:AssertTrue(message = "newStartDate는 newEndDate보다 이전이어야 합니다")
    val isDateRangeOrdered: Boolean
        get() = newStartDate == null || newEndDate == null || newStartDate.isBefore(newEndDate)
}

fun ChangeReservationRequest.toCommand(): ChangeReservationCommand =
    ChangeReservationCommand(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        newDateRange = ReservationDateRange(requireNotNull(newStartDate), requireNotNull(newEndDate)),
        initiatedBy = requireNotNull(initiatedBy),
        externalRequestId = externalRequestId,
    )
