package yeo.chi.proejct.pms.operation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface InboundEventRepository : JpaRepository<InboundEventEntity, Long> {
    fun findByNotificationKey(notificationKey: String): InboundEventEntity?
}
