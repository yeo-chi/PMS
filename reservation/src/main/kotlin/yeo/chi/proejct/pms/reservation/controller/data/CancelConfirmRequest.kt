package yeo.chi.proejct.pms.reservation.controller.data

import jakarta.validation.constraints.NotBlank
import yeo.chi.proejct.pms.reservation.domain.CancelConfirmCommand

data class CancelConfirmRequest(
    @field:NotBlank val platformId: String,
    @field:NotBlank val platformReservationRef: String,
    val externalRequestId: String? = null,
)

fun CancelConfirmRequest.toCommand(): CancelConfirmCommand =
    CancelConfirmCommand(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        externalRequestId = externalRequestId,
    )
