package yeo.chi.proejct.pms.reservation.domain

data class ChangeReservationCommand(
    val platformId: String,
    val platformReservationRef: String,
    val newDateRange: ReservationDateRange,
    val initiatedBy: RequestInitiator,
    val externalRequestId: String? = null,
)
