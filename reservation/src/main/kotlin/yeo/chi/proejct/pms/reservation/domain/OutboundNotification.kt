package yeo.chi.proejct.pms.reservation.domain

import java.time.OffsetDateTime

data class OutboundNotification(
    val id: Long?,
    val notificationKey: String,
    // BOOK이 겹침으로 거부된 경우 예약 row 자체가 없어 null일 수 있다(payload에 reservationNo를
    // 담아 발신 시 대체한다).
    val reservationId: Long?,
    val requestId: Long?,
    val eventType: String,
    val payload: String,
    val status: OutboundNotificationStatus,
    val retryCount: Int,
    val nextRetryAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
