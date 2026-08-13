package yeo.chi.proejct.pms.reservation.service

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

// 호스트에게는 OTA의 externalRequestId 같은 통보 단위 참조값이 없으므로, 항상 초 단위 타임스탬프로
// "같은 초 안의 정확한 재시도"만 이 키로 멱등 처리한다. 그보다 늦게 도착하는 재요청은
// CancelRequest의 상태 기반 멱등성(PENDING_CANCEL/CANCELLED면 재전이 없이 감사 기록만 추가)이 최종 방어선이다.
// reservationNo가 아니라 reservationId를 쓴다: reservationNo를 쓰려면 키를 만들기 전에 예약을 미리
// 조회해야 하는데, 그러면 재시도 루프(매 시도마다 최신 상태를 다시 읽는)와 별개로 조회가 중복되고,
// "예약을 찾지 못한 경우"에는 애초에 reservationNo 자체가 없어 키를 만들 수 없다.
fun buildCancelRequestKey(
    reservationId: Long,
    now: OffsetDateTime,
): String = "$reservationId:CANCEL_REQUEST:${now.truncatedTo(ChronoUnit.SECONDS)}"
