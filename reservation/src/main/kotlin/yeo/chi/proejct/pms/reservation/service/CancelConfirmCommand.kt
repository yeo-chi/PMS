package yeo.chi.proejct.pms.reservation.service

data class CancelConfirmCommand(
    val platformId: String,
    val platformReservationRef: String,
    val externalRequestId: String? = null,
)
