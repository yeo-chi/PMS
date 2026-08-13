package yeo.chi.proejct.pms.operation.persistent

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
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
    val id: Long,
    @Column(name = "listing_key", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val listingKey: String?,
    @Column(name = "room_id")
    val roomId: Long,
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
)

fun RoomChannelListingEntity.toDomain(): RoomChannelListing =
    RoomChannelListing(
        id = id,
        listingKey = listingKey,
        roomId = roomId,
        otaChannelId = otaChannelId,
        platformId = platformId,
        externalProductId = externalProductId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun RoomChannelListing.toEntity(): RoomChannelListingEntity =
    RoomChannelListingEntity(
        id = id ?: 0,
        listingKey = listingKey,
        roomId = roomId,
        otaChannelId = otaChannelId,
        platformId = platformId,
        externalProductId = externalProductId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
