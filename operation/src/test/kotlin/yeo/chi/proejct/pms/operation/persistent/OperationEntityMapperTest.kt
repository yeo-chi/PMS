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
import yeo.chi.proejct.pms.operation.persistent.entity.HostEntity
import yeo.chi.proejct.pms.operation.persistent.entity.InboundEventEntity
import yeo.chi.proejct.pms.operation.persistent.entity.OtaChannelEntity
import yeo.chi.proejct.pms.operation.persistent.entity.OutboxEventEntity
import yeo.chi.proejct.pms.operation.persistent.entity.RoomChannelListingEntity
import yeo.chi.proejct.pms.operation.persistent.entity.RoomEntity
import yeo.chi.proejct.pms.operation.persistent.entity.toDomain
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import java.time.LocalDateTime

class OperationEntityMapperTest : FeatureSpec({
    val now = LocalDateTime.of(2026, 1, 1, 0, 0, 0)

    feature("HostEntity ↔ Host 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val host =
                Host(
                    id = 1L,
                    hostId = "HOST-1",
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

    // Room/RoomChannelListing/InboundEvent/OutboxEvent는 id/createdAt/updatedAt이 순수 도메인
    // 관심사가 아니라서 domain 클래스 자체에 없다(DB가 생성하는 기술적 값). 그래서 Host/OtaChannel과
    // 달리 "domain -> entity -> domain" 왕복이 아니라, RoomEntity.from(domain) 등 컴패니언 팩토리로
    // entity를 만들고 그 entity의 toDomain()이 원본 domain의 "비즈니스 필드"와 일치하는지만 검증한다.

    feature("RoomEntity ↔ Room 매핑") {
        scenario("from()으로 만든 엔티티를 toDomain()하면 원본과 동일하다") {
            val room =
                Room(
                    roomId = "HOST-1-ROOM-101",
                    hostId = 1L,
                    name = "디럭스 룸",
                    address = "서울시 어딘가",
                    capacity = 2,
                    status = RoomStatus.ACTIVE,
                )

            RoomEntity.from(room).toDomain() shouldBe room
        }
    }

    feature("RoomChannelListingEntity ↔ RoomChannelListing 매핑") {
        scenario("from()으로 만든 엔티티를 toDomain()하면 원본과 동일하다") {
            val listing =
                RoomChannelListing(
                    listingKey = "OTA_BOOKING:EXT-PRODUCT-1",
                    roomId = "HOST-1-ROOM-101",
                    otaChannelId = 1L,
                    platformId = "OTA_BOOKING",
                    externalProductId = "EXT-PRODUCT-1",
                    status = RoomChannelListingStatus.ACTIVE,
                )

            RoomChannelListingEntity.from(listing).toDomain() shouldBe listing
        }
    }

    feature("InboundEventEntity ↔ InboundEvent 매핑") {
        scenario("from()으로 만든 엔티티를 toDomain()하면 원본과 동일하다") {
            val inboundEvent =
                InboundEvent(
                    notificationKey = "OTA_BOOKING:REF-1:RESERVATION_CONFIRMED",
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"roomCode":"ROOM-101"}""",
                    receivedAt = null,
                )

            val roundTripped = InboundEventEntity.from(inboundEvent).toDomain()

            roundTripped.notificationKey shouldBe inboundEvent.notificationKey
            roundTripped.reservationNo shouldBe inboundEvent.reservationNo
            roundTripped.eventType shouldBe inboundEvent.eventType
            roundTripped.payload shouldBe inboundEvent.payload
        }
    }

    feature("OutboxEventEntity ↔ OutboxEvent 매핑") {
        scenario("from()으로 만든 엔티티를 toDomain()하면 원본과 동일하다") {
            val outboxEvent =
                OutboxEvent(
                    outboxKey = "OTA_BOOKING:REF-1:OTA_CHANNEL:OTA_BOOKING:RESERVATION_CONFIRMED",
                    targetType = OutboxTargetType.OTA_CHANNEL,
                    targetCode = "OTA_BOOKING",
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"roomCode":"ROOM-101"}""",
                    status = OutboxEventStatus.PENDING,
                    retryCount = 0,
                    nextRetryAt = now,
                )

            OutboxEventEntity.from(outboxEvent).toDomain() shouldBe outboxEvent
        }
    }
})
