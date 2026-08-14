package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

data class CancelConfirmCommand(
    val platformId: String,
    val platformReservationRef: String,
    val externalRequestId: String? = null,
) {
    // externalRequestId가 없으면 초 단위로 묶어 "같은 초 안에 도착한 동일 재시도"만 이 키로 멱등 처리한다.
    // 그보다 늦게 도착하는 진짜 재통보는 CancelConfirm의 상태 기반 멱등성(이미 CANCELLED면 무시)이 최종 방어선이 된다.
    fun buildCancelConfirmRequestKey(now: OffsetDateTime): String =
        if (externalRequestId != null) {
            "$platformId:$externalRequestId"
        } else {
            "$platformId:$platformReservationRef:CANCEL_CONFIRM:${now.truncatedTo(ChronoUnit.SECONDS)}"
        }
}
