package yeo.chi.proejct.pms.reservation.controller.data

import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.ReservationLog
import java.time.OffsetDateTime

data class ReservationRequestResponse(
    val requestId: Long?,
    val reservationCode: String?,
    val resultStatus: RequestResultStatus,
    val rejectReason: String?,
    val requestedAt: OffsetDateTime,
)

fun ReservationLog.toResponse(): ReservationRequestResponse =
    ReservationRequestResponse(
        requestId = id,
        reservationCode = reservationCode,
        resultStatus = resultStatus,
        rejectReason = rejectReason,
        requestedAt = requestedAt,
    )
