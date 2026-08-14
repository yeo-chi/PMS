package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

data class CancelRequestCommand(
    val reservationId: Long,
    val reason: CancelRequestReason,
    val externalRequestId: String? = null,
) {
    // 호출자가 externalRequestId를 주면(예: 호스트 어드민 UI가 요청 단위로 발급하는 값) 그 값으로 정확히
    // 식별하고, 없으면 초 단위로 묶어 "같은 초 안에 도착한 동일 재시도"만 이 키로 멱등 처리한다.
    // externalRequestId 없이 같은 초 안에서 진짜로 서로 다른 두 요청이 들어오면(흔치 않지만) 하나로
    // 합쳐질 수 있는데, 이 경우에도 CancelRequest의 상태 기반 멱등성(PENDING_CANCEL/CANCELLED면 재전이
    // 없이 감사 기록만 추가)이 최종 방어선이 되어 안전하다.
    // reservationNo가 아니라 reservationId를 쓴다: reservationNo를 쓰려면 키를 만들기 전에 예약을 미리
    // 조회해야 하는데, 그러면 재시도 루프(매 시도마다 최신 상태를 다시 읽는)와 별개로 조회가 중복되고,
    // "예약을 찾지 못한 경우"에는 애초에 reservationNo 자체가 없어 키를 만들 수 없다.
    fun buildCancelRequestKey(now: OffsetDateTime): String =
        if (externalRequestId != null) {
            "$reservationId:CANCEL_REQUEST:$externalRequestId"
        } else {
            "$reservationId:CANCEL_REQUEST:${now.truncatedTo(ChronoUnit.SECONDS)}"
        }
}

enum class CancelRequestReason {
    OVERBOOKING_CLEANUP,
    FACILITY_ISSUE,
    OTHER,
}
