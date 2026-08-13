package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime

data class ReservationRequest(
    val id: Long?,
    val requestKey: String,
    val reservationId: Long?,
    val platformId: String,
    val action: ReservationRequestAction,
    val initiatedBy: RequestInitiator,
    val roomCode: String?,
    val oldDateRange: ReservationDateRange?,
    val newDateRange: ReservationDateRange?,
    val resultStatus: RequestResultStatus,
    val rejectReason: String?,
    val requestedAt: OffsetDateTime,
)
