package yeo.chi.proejct.pms.operation.persistent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.domain.Room
import yeo.chi.proejct.pms.operation.domain.RoomChannelListing
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import yeo.chi.proejct.pms.operation.domain.RoomStatus
import java.time.LocalDateTime

class OperationEntityMapperTest : FeatureSpec({
    val now = LocalDateTime.of(2026, 1, 1, 0, 0, 0)

    feature("HostEntity ↔ Host 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val host =
                Host(
                    id = 1L,
                    hostCode = "HOST-1",
                    name = "호스트 이름",
                    contactEmail = "host@example.com",
                    contactPhone = "010-0000-0000",
                    status = HostStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )

            host.toEntity().toDomain() shouldBe host
        }
    }

    feature("OtaChannelEntity ↔ OtaChannel 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val otaChannel =
                OtaChannel(
                    id = 1L,
                    platformId = "OTA_BOOKING",
                    name = "Booking.com",
                    integrationMode = OtaChannelIntegrationMode.ASYNC,
                    callbackBaseUrl = "https://ota.example.com/callback",
                    apiKeyRef = "secret-ref-1",
                    status = OtaChannelStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )

            otaChannel.toEntity().toDomain() shouldBe otaChannel
        }
    }

    feature("RoomEntity ↔ Room 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val room =
                Room(
                    id = 1L,
                    roomCode = "HOST-1-ROOM-101",
                    hostId = 1L,
                    name = "디럭스 룸",
                    address = "서울시 어딘가",
                    capacity = 2,
                    status = RoomStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )

            room.toEntity().toDomain() shouldBe room
        }
    }

    feature("RoomChannelListingEntity ↔ RoomChannelListing 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val listing =
                RoomChannelListing(
                    id = 1L,
                    listingKey = "OTA_BOOKING:EXT-PRODUCT-1",
                    roomId = 1L,
                    otaChannelId = 1L,
                    platformId = "OTA_BOOKING",
                    externalProductId = "EXT-PRODUCT-1",
                    status = RoomChannelListingStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                )

            listing.toEntity().toDomain() shouldBe listing
        }
    }

    feature("InboundEventEntity ↔ InboundEvent 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val inboundEvent =
                InboundEvent(
                    id = 1L,
                    notificationKey = "OTA_BOOKING:REF-1:RESERVATION_CONFIRMED",
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"roomCode":"ROOM-101"}""",
                    receivedAt = now,
                )

            inboundEvent.toEntity().toDomain() shouldBe inboundEvent
        }
    }

    feature("OutboxEventEntity ↔ OutboxEvent 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val outboxEvent =
                OutboxEvent(
                    id = 1L,
                    outboxKey = "OTA_BOOKING:REF-1:OTA_CHANNEL:OTA_BOOKING:RESERVATION_CONFIRMED",
                    targetType = OutboxTargetType.OTA_CHANNEL,
                    targetCode = "OTA_BOOKING",
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"roomCode":"ROOM-101"}""",
                    status = OutboxEventStatus.PENDING,
                    retryCount = 0,
                    nextRetryAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            outboxEvent.toEntity().toDomain() shouldBe outboxEvent
        }
    }
})
