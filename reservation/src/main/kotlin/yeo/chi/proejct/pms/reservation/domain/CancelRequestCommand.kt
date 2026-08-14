package yeo.chi.proejct.pms.reservation.domain

data class CancelRequestCommand(
    val reservationId: Long,
    val reason: CancelRequestReason,
    val externalRequestId: String? = null,
)

enum class CancelRequestReason {
    OVERBOOKING_CLEANUP,
    FACILITY_ISSUE,
    OTHER,
}
