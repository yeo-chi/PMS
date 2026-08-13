package yeo.chi.proejct.pms.operation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface HostRepository : JpaRepository<HostEntity, Long> {
    fun findByHostCode(hostCode: String): HostEntity?
}
