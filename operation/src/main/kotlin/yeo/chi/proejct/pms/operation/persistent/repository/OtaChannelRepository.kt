package yeo.chi.proejct.pms.operation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import yeo.chi.proejct.pms.operation.persistent.entity.OtaChannelEntity

@Repository
interface OtaChannelRepository : JpaRepository<OtaChannelEntity, Long> {
    fun findByPlatformId(platformId: String): OtaChannelEntity?
}
