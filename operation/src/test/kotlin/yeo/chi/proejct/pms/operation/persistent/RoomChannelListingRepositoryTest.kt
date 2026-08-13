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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomChannelListingRepositoryTest(
    private val hostRepository: HostRepository,
    private val otaChannelRepository: OtaChannelRepository,
    private val roomRepository: RoomRepository,
    private val roomChannelListingRepository: RoomChannelListingRepository,
) : MySqlIntegrationTest({

    fun savedRoomId(roomCode: String): Long {
        val hostId =
            hostRepository
                .saveAndFlush(
                    Host(
                        id = null,
                        hostCode = "HOST-LISTING-$roomCode",
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
                Room(
                    id = null,
                    roomCode = roomCode,
                    hostId = hostId,
                    name = "디럭스 룸",
                    address = null,
                    capacity = null,
                    status = RoomStatus.ACTIVE,
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            ).id
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
        roomId: Long,
        otaChannelId: Long,
        platformId: String,
        externalProductId: String,
    ): RoomChannelListing =
        RoomChannelListing(
            id = null,
            listingKey = null,
            roomId = roomId,
            otaChannelId = otaChannelId,
            platformId = platformId,
            externalProductId = externalProductId,
            status = RoomChannelListingStatus.ACTIVE,
            createdAt = null,
            updatedAt = null,
        )

    feature("RoomChannelListing 저장/조회") {
        scenario("저장 후 listing_key가 platform_id:external_product_id 형태로 채워진다") {
            val roomId = savedRoomId("ROOM-LISTING-1")
            val otaChannelId = savedOtaChannelId("OTA_LISTING_1")

            val saved =
                roomChannelListingRepository.saveAndFlush(
                    newListing(roomId, otaChannelId, "OTA_LISTING_1", "EXT-PRODUCT-1").toEntity(),
                )

            saved.listingKey shouldBe "OTA_LISTING_1:EXT-PRODUCT-1"
        }

        scenario("동일한 listing_key로 재삽입하면 저장이 거부된다") {
            val roomId1 = savedRoomId("ROOM-LISTING-DUP-1")
            val roomId2 = savedRoomId("ROOM-LISTING-DUP-2")
            val otaChannelId = savedOtaChannelId("OTA_LISTING_DUP")
            roomChannelListingRepository.saveAndFlush(
                newListing(roomId1, otaChannelId, "OTA_LISTING_DUP", "EXT-PRODUCT-DUP").toEntity(),
            )

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    newListing(roomId2, otaChannelId, "OTA_LISTING_DUP", "EXT-PRODUCT-DUP").toEntity(),
                )
            }
        }

        scenario("존재하지 않는 room_id로 저장하면 FK 제약 위반으로 거부된다") {
            val otaChannelId = savedOtaChannelId("OTA_LISTING_NO_ROOM")

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    newListing(roomId = -1L, otaChannelId, "OTA_LISTING_NO_ROOM", "EXT-PRODUCT-X").toEntity(),
                )
            }
        }

        scenario("존재하지 않는 ota_channel_id로 저장하면 FK 제약 위반으로 거부된다") {
            val roomId = savedRoomId("ROOM-LISTING-NO-CHANNEL")

            shouldThrow<DataIntegrityViolationException> {
                roomChannelListingRepository.saveAndFlush(
                    newListing(roomId, otaChannelId = -1L, "OTA_LISTING_NO_CHANNEL", "EXT-PRODUCT-Y").toEntity(),
                )
            }
        }
    }
})
