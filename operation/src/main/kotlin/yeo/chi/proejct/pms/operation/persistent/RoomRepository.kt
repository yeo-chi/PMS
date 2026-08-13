package yeo.chi.proejct.pms.operation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<RoomEntity, Long> {
    fun findByRoomCode(roomCode: String): RoomEntity?
}
