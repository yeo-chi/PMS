package yeo.chi.proejct.pms.reservation.controller

import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import java.time.OffsetDateTime

data class ReservationRequestResponse(
    val requestId: Long?,
    val reservationId: Long?,
    val resultStatus: RequestResultStatus,
    val rejectReason: String?,
    val requestedAt: OffsetDateTime,
)

fun ReservationRequest.toResponse(): ReservationRequestResponse =
    ReservationRequestResponse(
        requestId = id,
        reservationId = reservationId,
        resultStatus = resultStatus,
        rejectReason = rejectReason,
        requestedAt = requestedAt,
    )
