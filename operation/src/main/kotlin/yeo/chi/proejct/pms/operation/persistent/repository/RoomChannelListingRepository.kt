package yeo.chi.proejct.pms.operation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import yeo.chi.proejct.pms.operation.persistent.entity.RoomChannelListingEntity

@Repository
interface RoomChannelListingRepository : JpaRepository<RoomChannelListingEntity, Long> {
    fun findByRoomId(roomId: String): List<RoomChannelListingEntity>
}
