package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime

data class OutboundNotification(
    val id: Long?,
    val notificationKey: String,
    val reservationId: Long,
    val requestId: Long?,
    val eventType: String,
    val payload: String,
    val status: OutboundNotificationStatus,
    val retryCount: Int,
    val nextRetryAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
