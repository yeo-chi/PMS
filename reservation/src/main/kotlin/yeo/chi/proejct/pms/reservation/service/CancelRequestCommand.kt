package yeo.chi.proejct.pms.reservation.service

import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason

data class CancelRequestCommand(
    val reservationId: Long,
    val reason: CancelRequestReason,
    val externalRequestId: String? = null,
)
