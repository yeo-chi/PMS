package yeo.chi.proejct.pms.operation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.domain.Room
import yeo.chi.proejct.pms.operation.domain.RoomChannelListing
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import yeo.chi.proejct.pms.operation.domain.RoomStatus
import yeo.chi.proejct.pms.operation.persistent.repository.HostRepository
import yeo.chi.proejct.pms.operation.persistent.repository.InboundEventRepository
import yeo.chi.proejct.pms.operation.persistent.MySqlIntegrationTest
import yeo.chi.proejct.pms.operation.persistent.repository.OtaChannelRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OutboxEventRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomChannelListingRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomRepository
import yeo.chi.proejct.pms.operation.persistent.entity.RoomChannelListingEntity
import yeo.chi.proejct.pms.operation.persistent.entity.RoomEntity
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
class InboundEventControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val inboundEventRepository: InboundEventRepository,
    @Autowired private val outboxEventRepository: OutboxEventRepository,
    @Autowired private val hostRepository: HostRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val roomChannelListingRepository: RoomChannelListingRepository,
    @Autowired private val otaChannelRepository: OtaChannelRepository,
    @Autowired private val objectMapper: ObjectMapper,
) : MySqlIntegrationTest({

    fun requestBody(
        notificationKey: String,
        reservationNo: String,
        eventType: String,
        includePlatformId: Boolean = true,
        platformId: String = "OTA_BOOKING",
        roomCode: String = "ROOM-101",
    ): String {
        val payloadJson =
            if (includePlatformId) {
                """{"platformId":"$platformId","roomCode":"$roomCode"}"""
            } else {
                """{"roomCode":"$roomCode"}"""
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

    fun savedRoomId(roomCode: String): String {
        val hostId =
            hostRepository
                .saveAndFlush(
                    Host(
                        id = null,
                        hostId = "HOST-$roomCode",
                        name = "호스트 이름",
                        contactEmail = null,
                        contactPhone = null,
                        status = HostStatus.ACTIVE,
                        createdAt = null,
                        updatedAt = null,
                    ).toEntity(),
                ).id
        return roomRepository
            .saveAndFlush(
                RoomEntity.from(
                    Room(
                        roomId = roomCode,
                        hostId = hostId,
                        name = "디럭스 룸",
                        address = null,
                        capacity = null,
                        status = RoomStatus.ACTIVE,
                    ),
                ),
            ).roomId
    }

    fun savedOtaChannelId(
        platformId: String,
        status: OtaChannelStatus = OtaChannelStatus.ACTIVE,
    ): Long =
        otaChannelRepository
            .saveAndFlush(
                OtaChannel(
                    id = null,
                    platformId = platformId,
                    name = platformId,
                    integrationMode = OtaChannelIntegrationMode.ASYNC,
                    callbackBaseUrl = null,
                    apiKeyRef = null,
                    status = status,
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            ).id

    fun savedListing(
        roomId: String,
        otaChannelId: Long,
        platformId: String,
        status: RoomChannelListingStatus = RoomChannelListingStatus.ACTIVE,
    ) {
        roomChannelListingRepository.saveAndFlush(
            RoomChannelListingEntity.from(
                RoomChannelListing(
                    listingKey = null,
                    roomId = roomId,
                    otaChannelId = otaChannelId,
                    platformId = platformId,
                    externalProductId = "EXT-$platformId",
                    status = status,
                ),
            ),
        )
    }

    feature("신규 수신 성공") {
        listOf(
            "RESERVATION_CONFIRMED",
            "RESERVATION_REJECTED",
            "CANCEL_REQUESTED",
            "RESERVATION_CANCELLED",
            "RESERVATION_CHANGED",
        ).forEach { eventType ->
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

    feature("채널 팬아웃") {
        scenario("2개 이상 채널에 노출된 방에 예약 확정 이벤트 수신 시 다른 채널에 INVENTORY_CLOSED로 팬아웃된다") {
            val roomCode = "ROOM-FANOUT-1"
            val roomId = savedRoomId(roomCode)
            listOf("A", "B", "C").forEach { savedListing(roomId, savedOtaChannelId("OTA_$it"), "OTA_$it") }
            val notificationKey = "NOTIFY-FANOUT-CONFIRMED"
            val reservationNo = "OTA_A:REF-FANOUT-CONFIRMED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CONFIRMED", platformId = "OTA_A", roomCode = roomCode),
                        ),
                ).andExpect(status().isOk)

            val outboxEvents = outboxEventRepository.findAll().filter { it.reservationNo == reservationNo }
            outboxEvents shouldHaveSize 3
            outboxEvents.count { it.targetCode == "OTA_A" && it.eventType == "RESERVATION_CONFIRMED" } shouldBe 1
            outboxEvents.count { it.targetCode == "OTA_B" && it.eventType == "INVENTORY_CLOSED" } shouldBe 1
            outboxEvents.count { it.targetCode == "OTA_C" && it.eventType == "INVENTORY_CLOSED" } shouldBe 1
            outboxEvents.none { it.targetCode == "OTA_A" && it.eventType == "INVENTORY_CLOSED" } shouldBe true
        }

        scenario("취소확정 이벤트는 다른 채널에 INVENTORY_REOPENED로 팬아웃된다") {
            val roomCode = "ROOM-FANOUT-2"
            val roomId = savedRoomId(roomCode)
            listOf("A", "B", "C").forEach { savedListing(roomId, savedOtaChannelId("OTA2_$it"), "OTA2_$it") }
            val notificationKey = "NOTIFY-FANOUT-CANCELLED"
            val reservationNo = "OTA2_A:REF-FANOUT-CANCELLED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CANCELLED", platformId = "OTA2_A", roomCode = roomCode),
                        ),
                ).andExpect(status().isOk)

            val outboxEvents = outboxEventRepository.findAll().filter { it.reservationNo == reservationNo }
            outboxEvents shouldHaveSize 3
            outboxEvents.count { it.targetCode == "OTA2_B" && it.eventType == "INVENTORY_REOPENED" } shouldBe 1
            outboxEvents.count { it.targetCode == "OTA2_C" && it.eventType == "INVENTORY_REOPENED" } shouldBe 1
        }

        scenario("예약 변경 이벤트는 다른 채널에 INVENTORY_CHANGED로 팬아웃된다") {
            val roomCode = "ROOM-FANOUT-3-CHANGED"
            val roomId = savedRoomId(roomCode)
            listOf("A", "B", "C").forEach { savedListing(roomId, savedOtaChannelId("OTA3_$it"), "OTA3_$it") }
            val notificationKey = "NOTIFY-FANOUT-CHANGED"
            val reservationNo = "OTA3_A:REF-FANOUT-CHANGED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CHANGED", platformId = "OTA3_A", roomCode = roomCode),
                        ),
                ).andExpect(status().isOk)

            val outboxEvents = outboxEventRepository.findAll().filter { it.reservationNo == reservationNo }
            outboxEvents.count { it.targetCode == "OTA3_B" && it.eventType == "INVENTORY_CHANGED" } shouldBe 1
            outboxEvents.count { it.targetCode == "OTA3_C" && it.eventType == "INVENTORY_CHANGED" } shouldBe 1
        }

        scenario("노출 채널이 자기 자신뿐이면 팬아웃이 발생하지 않는다") {
            val roomCode = "ROOM-FANOUT-3"
            val roomId = savedRoomId(roomCode)
            savedListing(roomId, savedOtaChannelId("OTA3_SOLO"), "OTA3_SOLO")
            val notificationKey = "NOTIFY-FANOUT-SOLO"
            val reservationNo = "OTA3_SOLO:REF-FANOUT-SOLO"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CONFIRMED", platformId = "OTA3_SOLO", roomCode = roomCode),
                        ),
                ).andExpect(status().isOk)

            outboxEventRepository.findAll().count { it.reservationNo == reservationNo } shouldBe 1
        }

        scenario("비활성 리스팅과 SUSPENDED 채널은 팬아웃 대상에서 제외된다") {
            val roomCode = "ROOM-FANOUT-4"
            val roomId = savedRoomId(roomCode)
            savedListing(roomId, savedOtaChannelId("OTA4_A"), "OTA4_A")
            savedListing(roomId, savedOtaChannelId("OTA4_B"), "OTA4_B", status = RoomChannelListingStatus.INACTIVE)
            savedListing(
                roomId,
                savedOtaChannelId("OTA4_C", status = OtaChannelStatus.SUSPENDED),
                "OTA4_C",
            )
            val notificationKey = "NOTIFY-FANOUT-EXCLUDED"
            val reservationNo = "OTA4_A:REF-FANOUT-EXCLUDED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CONFIRMED", platformId = "OTA4_A", roomCode = roomCode),
                        ),
                ).andExpect(status().isOk)

            outboxEventRepository.findAll().count { it.reservationNo == reservationNo } shouldBe 1
        }

        listOf("CANCEL_REQUESTED", "RESERVATION_REJECTED").forEach { eventType ->
            scenario("$eventType 는 재고 변화가 없으므로 팬아웃이 발생하지 않는다") {
                val roomCode = "ROOM-FANOUT-NOFANOUT-$eventType"
                val roomId = savedRoomId(roomCode)
                listOf("A", "B").forEach { savedListing(roomId, savedOtaChannelId("OTA5_${eventType}_$it"), "OTA5_${eventType}_$it") }
                val notificationKey = "NOTIFY-FANOUT-$eventType"
                val reservationNo = "OTA5_${eventType}_A:REF-FANOUT-$eventType"

                mockMvc
                    .perform(
                        post("/api/inbound-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                requestBody(notificationKey, reservationNo, eventType, platformId = "OTA5_${eventType}_A", roomCode = roomCode),
                            ),
                    ).andExpect(status().isOk)

                outboxEventRepository.findAll().count { it.reservationNo == reservationNo } shouldBe 1
            }
        }

        scenario("방을 찾을 수 없으면 팬아웃만 건너뛰고 원본 통보는 정상 저장된다") {
            val notificationKey = "NOTIFY-FANOUT-NO-ROOM"
            val reservationNo = "OTA_BOOKING:REF-FANOUT-NO-ROOM"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CONFIRMED", roomCode = "ROOM-DOES-NOT-EXIST"),
                        ),
                ).andExpect(status().isOk)

            val savedOutboxEvent = outboxEventRepository.findAll().filter { it.reservationNo == reservationNo }
            savedOutboxEvent shouldHaveSize 1
            savedOutboxEvent.first().eventType shouldBe "RESERVATION_CONFIRMED"
        }
    }

    feature("호스트 통보") {
        scenario("예약 변경 이벤트는 방을 소유한 호스트에게도 HOST 대상 outbox_event가 생긴다") {
            val roomCode = "ROOM-HOST-NOTIFY-1"
            savedRoomId(roomCode)
            val hostId = "HOST-$roomCode"
            val notificationKey = "NOTIFY-HOST-CHANGED"
            val reservationNo = "OTA_BOOKING:REF-HOST-CHANGED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(notificationKey, reservationNo, "RESERVATION_CHANGED", roomCode = roomCode)),
                ).andExpect(status().isOk)

            val hostOutboxEvent =
                outboxEventRepository
                    .findAll()
                    .filter { it.reservationNo == reservationNo }
                    .firstOrNull { it.targetType == OutboxTargetType.HOST }
            requireNotNull(hostOutboxEvent) { "HOST 대상 outbox_event가 저장돼야 합니다" }
            hostOutboxEvent.targetCode shouldBe hostId
            hostOutboxEvent.eventType shouldBe "RESERVATION_CHANGED"
        }

        scenario("예약 확정처럼 변경이 아닌 이벤트는 HOST 대상 outbox_event를 만들지 않는다") {
            val roomCode = "ROOM-HOST-NOTIFY-2"
            val roomId = savedRoomId(roomCode)
            val notificationKey = "NOTIFY-HOST-NOT-CHANGED"
            val reservationNo = "OTA_BOOKING:REF-HOST-NOT-CHANGED"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(notificationKey, reservationNo, "RESERVATION_CONFIRMED", roomCode = roomId)),
                ).andExpect(status().isOk)

            outboxEventRepository
                .findAll()
                .none { it.reservationNo == reservationNo && it.targetType == OutboxTargetType.HOST } shouldBe true
        }

        scenario("호스트를 찾을 수 없으면 HOST 통보만 건너뛰고 원본 통보는 정상 저장된다") {
            val notificationKey = "NOTIFY-HOST-NO-ROOM"
            val reservationNo = "OTA_BOOKING:REF-HOST-NO-ROOM"

            mockMvc
                .perform(
                    post("/api/inbound-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            requestBody(notificationKey, reservationNo, "RESERVATION_CHANGED", roomCode = "ROOM-DOES-NOT-EXIST"),
                        ),
                ).andExpect(status().isOk)

            val savedOutboxEvent = outboxEventRepository.findAll().filter { it.reservationNo == reservationNo }
            savedOutboxEvent shouldHaveSize 1
            savedOutboxEvent.first().targetType shouldBe OutboxTargetType.OTA_CHANNEL
        }
    }
})
