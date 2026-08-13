package yeo.chi.proejct.pms.operation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.persistent.InboundEventRepository
import yeo.chi.proejct.pms.operation.persistent.MySqlIntegrationTest
import yeo.chi.proejct.pms.operation.persistent.OutboxEventRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
class InboundEventControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val inboundEventRepository: InboundEventRepository,
    @Autowired private val outboxEventRepository: OutboxEventRepository,
    @Autowired private val objectMapper: ObjectMapper,
) : MySqlIntegrationTest({

    fun requestBody(
        notificationKey: String,
        reservationNo: String,
        eventType: String,
        includePlatformId: Boolean = true,
        platformId: String = "OTA_BOOKING",
    ): String {
        val payloadJson =
            if (includePlatformId) {
                """{"platformId":"$platformId","roomCode":"ROOM-101"}"""
            } else {
                """{"roomCode":"ROOM-101"}"""
            }
        return """
            {
              "notificationKey": "$notificationKey",
              "reservationNo": "$reservationNo",
              "eventType": "$eventType",
              "payload": $payloadJson
            }
        """.trimIndent()
    }

    feature("신규 수신 성공") {
        listOf("RESERVATION_CONFIRMED", "RESERVATION_REJECTED", "CANCEL_REQUESTED", "RESERVATION_CANCELLED").forEach { eventType ->
            scenario("$eventType 수신 시 inbound_events/outbox_events에 각각 1건씩 저장된다") {
                val notificationKey = "NOTIFY-$eventType"
                val reservationNo = "OTA_BOOKING:REF-$eventType"

                mockMvc
                    .perform(
                        post("/api/inbound-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(notificationKey, reservationNo, eventType)),
                    ).andExpect(status().isOk)

                val savedInboundEvent = inboundEventRepository.findByNotificationKey(notificationKey)
                requireNotNull(savedInboundEvent) { "inbound_event가 저장돼야 합니다" }
                objectMapper.readTree(savedInboundEvent.payload).get("roomCode").asText() shouldBe "ROOM-101"

                val outboxKey = "$reservationNo:${OutboxTargetType.OTA_CHANNEL}:OTA_BOOKING:$eventType"
                val savedOutboxEvent = outboxEventRepository.findByOutboxKey(outboxKey)
                requireNotNull(savedOutboxEvent) { "outbox_event가 저장돼야 합니다" }
                savedOutboxEvent.targetType shouldBe OutboxTargetType.OTA_CHANNEL
                savedOutboxEvent.targetCode shouldBe "OTA_BOOKING"
                savedOutboxEvent.status shouldBe OutboxEventStatus.PENDING
            }
        }
    }

    feature("멱등성") {
        scenario("동일한 notification_key로 재전송해도 항상 200이고 row 수는 늘지 않는다") {
            val notificationKey = "NOTIFY-IDEMPOTENT"
            val body = requestBody(notificationKey, "OTA_BOOKING:REF-IDEMPOTENT", "RESERVATION_CONFIRMED")

            mockMvc.perform(post("/api/inbound-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk)
            mockMvc.perform(post("/api/inbound-events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk)

            inboundEventRepository.findAll().count { it.notificationKey == notificationKey } shouldBe 1
            outboxEventRepository
                .findAll()
                .count { it.reservationNo == "OTA_BOOKING:REF-IDEMPOTENT" } shouldBe 1
        }
    }

    feature("처리 실패") {
        scenario("payload에 platformId가 없으면 어떤 row도 생성되지 않는다") {
            val notificationKey = "NOTIFY-NO-PLATFORM-ID"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(
                                notificationKey,
                                "OTA_BOOKING:REF-NO-PLATFORM-ID",
                                "RESERVATION_CONFIRMED",
                                includePlatformId = false,
                            ),
                        ),
                ).andExpect(status().isBadRequest)

            inboundEventRepository.findByNotificationKey(notificationKey) shouldBe null
        }

        scenario("알 수 없는 eventType이면 400을 반환하고 row가 생성되지 않는다") {
            val notificationKey = "NOTIFY-UNKNOWN-TYPE"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(notificationKey, "OTA_BOOKING:REF-UNKNOWN-TYPE", "SOMETHING_ELSE")),
                ).andExpect(status().isBadRequest)

            inboundEventRepository.findByNotificationKey(notificationKey) shouldBe null
        }

        scenario("필수 필드가 비어있으면 400을 반환한다") {
            val invalidBody =
                """
                {
                  "notificationKey": "",
                  "reservationNo": "OTA_BOOKING:REF-BLANK",
                  "eventType": "RESERVATION_CONFIRMED",
                  "payload": {"platformId":"OTA_BOOKING"}
                }
                """.trimIndent()

            mockMvc
                .perform(post("/api/inbound-events").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                .andExpect(status().isBadRequest)
        }
    }

    feature("동시 중복 수신") {
        scenario("같은 notification_key로 동시에 두 요청이 와도 각 테이블에 정확히 1건만 생성된다") {
            val notificationKey = "NOTIFY-CONCURRENT"
            val body = requestBody(notificationKey, "OTA_BOOKING:REF-CONCURRENT", "RESERVATION_CONFIRMED")
            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val statusCodes: List<Int>
            try {
                val futures =
                    (1..2).map {
                        executor.submit<Int> {
                            readyLatch.countDown()
                            startLatch.await()
                            mockMvc
                                .perform(post("/api/inbound-events").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andReturn()
                                .response.status
                        }
                    }
                readyLatch.await()
                startLatch.countDown()
                statusCodes = futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                executor.shutdown()
            }

            statusCodes.forEach { it shouldBe 200 }
            inboundEventRepository.findAll().count { it.notificationKey == notificationKey } shouldBe 1
            outboxEventRepository.findAll().count { it.reservationNo == "OTA_BOOKING:REF-CONCURRENT" } shouldBe 1
        }
    }
})
