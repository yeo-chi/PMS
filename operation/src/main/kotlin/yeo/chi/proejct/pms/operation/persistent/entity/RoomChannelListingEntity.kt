package yeo.chi.proejct.pms.operation.persistent.entity

import jakarta.persistence.*
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import yeo.chi.proejct.pms.operation.domain.RoomChannelListing
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import java.time.LocalDateTime

@Entity
@Table(name = "room_channel_listings")
class RoomChannelListingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "listing_key", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val listingKey: String?,
    @Column(name = "room_id")
    val roomId: String,
    @Column(name = "ota_channel_id")
    val otaChannelId: Long,
    @Column(name = "platform_id")
    val platformId: String,
    @Column(name = "external_product_id")
    val externalProductId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: RoomChannelListingStatus,
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val createdAt: LocalDateTime?,
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(roomChannelListing: RoomChannelListing) = RoomChannelListingEntity(
            listingKey = roomChannelListing.listingKey,
            roomId = roomChannelListing.roomId,
            otaChannelId = roomChannelListing.otaChannelId,
            platformId = roomChannelListing.platformId,
            externalProductId = roomChannelListing.externalProductId,
            status = roomChannelListing.status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    fun toDomain() = RoomChannelListing(
        listingKey = listingKey,
        roomId = roomId,
        otaChannelId = otaChannelId,
        platformId = platformId,
        externalProductId = externalProductId,
        status = status,
    )
}
