package yeo.chi.proejct.pms.operation.domain

data class RoomChannelListing(
    val listingKey: String?,
    val roomId: String,
    val otaChannelId: Long,
    val platformId: String,
    val externalProductId: String,
    val status: RoomChannelListingStatus,
)

enum class RoomChannelListingStatus {
    ACTIVE,
    INACTIVE,
}
