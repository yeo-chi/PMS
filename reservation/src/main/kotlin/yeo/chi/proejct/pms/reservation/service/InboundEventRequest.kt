package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.annotation.JsonRawValue

// docs/schema/operation_server_schema.sql의 inbound_events 컬럼(notification_key/reservation_no/
// event_type/payload)에 1:1 대응시킨 요청 계약. operation 서버가 아직 없으므로(#20 미착수) 이 티켓이
// 처음 제안하는 계약이며, #20 구현 시 실제로 맞춰봐야 한다.
data class InboundEventRequest(
    val notificationKey: String,
    val reservationNo: String,
    val eventType: String,
    // payload는 이미 직렬화된 JSON 문자열이다. @JsonRawValue 없이 평범한 String으로 두면
    // 이중 이스케이프되어 중첩 JSON이 아니라 문자열 값으로 나간다.
    @get:JsonRawValue
    val payload: String,
)
