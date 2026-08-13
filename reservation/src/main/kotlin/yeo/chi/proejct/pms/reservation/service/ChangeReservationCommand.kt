package yeo.chi.proejct.pms.reservation.service

import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange

data class ChangeReservationCommand(
    val platformId: String,
    val platformReservationRef: String,
    val newDateRange: ReservationDateRange,
    val initiatedBy: RequestInitiator,
    val externalRequestId: String? = null,
)
