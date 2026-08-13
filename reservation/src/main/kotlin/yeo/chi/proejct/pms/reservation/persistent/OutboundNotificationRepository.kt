package yeo.chi.proejct.pms.reservation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface OutboundNotificationRepository : JpaRepository<OutboundNotificationEntity, Long> {
    fun findByNotificationKey(notificationKey: String): OutboundNotificationEntity?
}
