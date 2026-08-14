package yeo.chi.proejct.pms.operation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import yeo.chi.proejct.pms.operation.persistent.entity.RoomEntity

@Repository
interface RoomRepository : JpaRepository<RoomEntity, Long> {
    fun findByRoomId(roomId: String): RoomEntity?
}
