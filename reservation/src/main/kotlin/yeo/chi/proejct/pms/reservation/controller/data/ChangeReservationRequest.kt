package yeo.chi.proejct.pms.reservation.controller.data

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ChangeReservationCommand
import java.time.LocalDate

data class ChangeReservationRequest(
    @field:NotBlank val platformId: String,
    @field:NotBlank val platformReservationRef: String,
    @field:NotNull val newStartDate: LocalDate?,
    @field:NotNull val newEndDate: LocalDate?,
    @field:NotNull val initiatedBy: RequestInitiator?,
    val externalRequestId: String? = null,
)

fun ChangeReservationRequest.toCommand(): ChangeReservationCommand =
    ChangeReservationCommand(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        newDateRange = ReservationDateRange(requireNotNull(newStartDate), requireNotNull(newEndDate)),
        initiatedBy = requireNotNull(initiatedBy),
        externalRequestId = externalRequestId,
    )
