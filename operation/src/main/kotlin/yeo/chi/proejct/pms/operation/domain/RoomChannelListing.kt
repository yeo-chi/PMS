package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class RoomChannelListing(
    val id: Long?,
    val listingKey: String?,
    val roomId: Long,
    val otaChannelId: Long,
    val platformId: String,
    val externalProductId: String,
    val status: RoomChannelListingStatus,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
