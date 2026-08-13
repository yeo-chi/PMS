package yeo.chi.proejct.pms.operation.controller

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank

// reservation 쪽 InboundEventRequest(service/InboundEventRequest.kt)와 이름은 같지만 서로 다른
// 모듈이라(Gradle 컴파일 의존성 없음) 무방하다. payload는 발신 측이 @JsonRawValue로 이스케이프
// 없이 중첩 JSON으로 보내므로, 여기서는 String이 아니라 JsonNode로 받아야 역직렬화가 실패하지 않는다.
data class InboundEventRequest(
    @field:NotBlank val notificationKey: String,
    @field:NotBlank val reservationNo: String,
    @field:NotBlank val eventType: String,
    val payload: JsonNode,
)
