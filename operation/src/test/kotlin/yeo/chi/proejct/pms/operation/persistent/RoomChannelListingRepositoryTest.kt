package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.Room
import yeo.chi.proejct.pms.operation.domain.RoomChannelListing
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import yeo.chi.proejct.pms.operation.domain.RoomStatus
import yeo.chi.proejct.pms.operation.persistent.entity.RoomChannelListingEntity
import yeo.chi.proejct.pms.operation.persistent.entity.RoomEntity
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import yeo.chi.proejct.pms.operation.persistent.repository.HostRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OtaChannelRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomChannelListingRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomRepository

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomChannelListingRepositoryTest(
    private val hostRepository: HostRepository,
    private val otaChannelRepository: OtaChannelRepository,
    private val roomRepository: RoomRepository,
    private val roomChannelListingRepository: RoomChannelListingRepository,
) : MySqlIntegrationTest({

    fun savedRoomId(roomId: String): String {
        val hostId =
            hostRepository
                .saveAndFlush(
                    Host(
                        id = null,
                        hostId = "HOST-LISTING-$roomId",
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
                        roomId = roomId,
                        hostId = hostId,
                        name = "디럭스 룸",
                        address = null,
                        capacity = null,
                        status = RoomStatus.ACTIVE,
                    ),
                ),
            ).roomId
    }

    fun savedOtaChannelId(platformId: String): Long =
        otaChannelRepository
            .saveAndFlush(
                OtaChannel(
                    id = null,
                    platformId = platformId,
                    name = "Booking.com",
                    integrationMode = OtaChannelIntegrationMode.ASYNC,
                    callbackBaseUrl = null,
                    apiKeyRef = null,
                    status = OtaChannelStatus.ACTIVE,
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            ).id

    fun newListing(
        roomId: String,
        otaChannelId: Long,
        platformId: String,
        externalProductId: String,
    ): RoomChannelListing =
        RoomChannelListing(
            listingKey = null,
            roomId = roomId,
            otaChannelId = otaChannelId,
            platformId = platformId,
            externalProductId = externalProductId,
            status = RoomChannelListingStatus.ACTIVE,
        )

    feature("RoomChannelListing 저장/조회") {
        scenario("저장 후 listing_key가 platform_id:external_product_id 형태로 채워진다") {
            val roomId = savedRoomId("ROOM-LISTING-1")
            val otaChannelId = savedOtaChannelId("OTA_LISTING_1")

            val saved =
                roomChannelListingRepository.saveAndFlush(
                    RoomChannelListingEntity.from(newListing(roomId, otaChannelId, "OTA_LISTING_1", "EXT-PRODUCT-1")),
                )

            saved.listingKey shouldBe "OTA_LISTING_1:EXT-PRODUCT-1"
        }

        scenario("동일한 listing_key로 재삽입하면 저장이 거부된다") {
            val roomId1 = savedRoomId("ROOM-LISTING-DUP-1")
            val roomId2 = savedRoomId("ROOM-LISTING-DUP-2")
            val otaChannelId = savedOtaChannelId("OTA_LISTING_DUP")
            roomChannelListingRepository.saveAndFlush(
                RoomChannelListingEntity.from(newListing(roomId1, otaChannelId, "OTA_LISTING_DUP", "EXT-PRODUCT-DUP")),
            )

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    RoomChannelListingEntity.from(newListing(roomId2, otaChannelId, "OTA_LISTING_DUP", "EXT-PRODUCT-DUP")),
                )
            }
        }

        scenario("존재하지 않는 room_id로 저장하면 FK 제약 위반으로 거부된다") {
            val otaChannelId = savedOtaChannelId("OTA_LISTING_NO_ROOM")

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    RoomChannelListingEntity.from(
                        newListing(roomId = "ROOM-DOES-NOT-EXIST", otaChannelId, "OTA_LISTING_NO_ROOM", "EXT-PRODUCT-X"),
                    ),
                )
            }
        }

        scenario("존재하지 않는 ota_channel_id로 저장하면 FK 제약 위반으로 거부된다") {
            val roomId = savedRoomId("ROOM-LISTING-NO-CHANNEL")

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    RoomChannelListingEntity.from(
                        newListing(roomId, otaChannelId = -1L, "OTA_LISTING_NO_CHANNEL", "EXT-PRODUCT-Y"),
                    ),
                )
            }
        }
    }
})
