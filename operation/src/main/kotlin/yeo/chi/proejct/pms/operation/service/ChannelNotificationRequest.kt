package yeo.chi.proejct.pms.operation.service

import com.fasterxml.jackson.annotation.JsonRawValue

// reservation의 InboundEventRequest(#18)와 같은 이유로 payload를 raw로 보낸다: 이미 직렬화된
// JSON 문자열이므로 @JsonRawValue 없이 평범한 String으로 두면 이중 이스케이프된다.
data class ChannelNotificationRequest(
    val outboxKey: String,
    val reservationNo: String,
    val eventType: String,
    @get:JsonRawValue
    val payload: String,
)
