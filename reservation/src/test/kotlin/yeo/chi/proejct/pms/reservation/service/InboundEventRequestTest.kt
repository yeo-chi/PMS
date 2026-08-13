package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class InboundEventRequestTest : FeatureSpec({
    feature("InboundEventRequest 직렬화") {
        scenario("payload는 문자열이 아니라 중첩 JSON 객체로 직렬화된다") {
            val objectMapper = ObjectMapper()
            val request =
                InboundEventRequest(
                    notificationKey = "NOTIFY-1:RESERVATION_CONFIRMED",
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"reservationNo":"OTA_BOOKING:REF-1","roomCode":"ROOM-1"}""",
                )

            val serialized = objectMapper.readTree(objectMapper.writeValueAsString(request))

            serialized.get("notificationKey").asText() shouldBe "NOTIFY-1:RESERVATION_CONFIRMED"
            (serialized.get("payload") is ObjectNode) shouldBe true
            serialized.get("payload").get("roomCode").asText() shouldBe "ROOM-1"
        }
    }
})
