package yeo.chi.proejct.pms.reservation.service

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

// CancelConfirmRequestKey와 대칭되는 형식: externalRequestId가 없으면 초 단위로 묶어
// "같은 초 안에 도착한 동일 재시도"만 이 키로 멱등 처리한다.
fun buildChangeRequestKey(
    platformId: String,
    platformReservationRef: String,
    externalRequestId: String?,
    now: OffsetDateTime,
): String =
    if (externalRequestId != null) {
        "$platformId:$externalRequestId"
    } else {
        "$platformId:$platformReservationRef:CHANGE:${now.truncatedTo(ChronoUnit.SECONDS)}"
    }
