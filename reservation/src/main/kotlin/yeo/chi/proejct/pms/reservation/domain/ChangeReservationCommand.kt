package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

data class ChangeReservationCommand(
    val platformId: String,
    val platformReservationRef: String,
    val newDateRange: ReservationDateRange,
    val initiatedBy: RequestInitiator,
    val externalRequestId: String? = null,
) {
    init {
        require(initiatedBy.isNotHost()) { "HOST는 예약 변경(CHANGE)을 요청할 수 없습니다: initiatedBy=$initiatedBy" }
    }

    // CancelConfirmRequest.buildCancelConfirmRequestKey와 대칭되는 형식: externalRequestId가 없으면
    // 초 단위로 묶어 "같은 초 안에 도착한 동일 재시도"만 이 키로 멱등 처리한다.
    fun buildChangeRequestKey(now: OffsetDateTime): String =
        if (externalRequestId != null) {
            "$platformId:$externalRequestId"
        } else {
            "$platformId:$platformReservationRef:CHANGE:${now.truncatedTo(ChronoUnit.SECONDS)}"
        }
}
