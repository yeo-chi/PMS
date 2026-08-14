package yeo.chi.proejct.pms.operation.domain

data class Room(
    val roomId: String,
    val hostId: Long,
    val name: String,
    val address: String?,
    val capacity: Int?,
    val status: RoomStatus,
)

enum class RoomStatus {
    ACTIVE,
    INACTIVE,
}
