package yeo.chi.proejct.pms.operation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import yeo.chi.proejct.pms.operation.persistent.entity.HostEntity

@Repository
interface HostRepository : JpaRepository<HostEntity, Long> {
    fun findByHostId(hostId: String): HostEntity?
}
