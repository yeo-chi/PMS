package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class Room(
    val id: Long?,
    val roomCode: String,
    val hostId: Long,
    val name: String,
    val address: String?,
    val capacity: Int?,
    val status: RoomStatus,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
