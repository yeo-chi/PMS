package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class OutboxEvent(
    val id: Long?,
    val outboxKey: String,
    val targetType: OutboxTargetType,
    // ota_channels.platform_id 또는 hosts.host_code
    val targetCode: String,
    val reservationNo: String,
    val eventType: String,
    val payload: String,
    val status: OutboxEventStatus,
    val retryCount: Int,
    val nextRetryAt: LocalDateTime,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
