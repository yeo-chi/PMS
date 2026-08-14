package yeo.chi.proejct.pms.operation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import yeo.chi.proejct.pms.operation.persistent.entity.InboundEventEntity

@Repository
interface InboundEventRepository : JpaRepository<InboundEventEntity, Long> {
    fun findByNotificationKey(notificationKey: String): InboundEventEntity?
}
