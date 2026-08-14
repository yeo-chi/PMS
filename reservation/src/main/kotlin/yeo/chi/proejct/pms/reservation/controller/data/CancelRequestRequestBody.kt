package yeo.chi.proejct.pms.reservation.controller.data

import jakarta.validation.constraints.NotNull
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason

// reservationId는 경로 변수(PathVariable)로 받으므로 바디에는 없다.
data class CancelRequestRequestBody(
    @field:NotNull val reason: CancelRequestReason?,
    val externalRequestId: String? = null,
)
