package yeo.chi.proejct.pms.reservation.persistent

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import java.time.OffsetDateTime

@Entity
@Table(name = "outbound_notifications")
class OutboundNotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "notification_key")
    val notificationKey: String,
    @Column(name = "reservation_id")
    val reservationId: Long,
    @Column(name = "request_id")
    val requestId: Long?,
    @Column(name = "event_type")
    val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: OutboundNotificationStatus,
    @Column(name = "retry_count")
    val retryCount: Int,
    @Column(name = "next_retry_at")
    val nextRetryAt: OffsetDateTime,
    @Column(name = "created_at")
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at")
    val updatedAt: OffsetDateTime,
)

fun OutboundNotificationEntity.toDomain(): OutboundNotification =
    OutboundNotification(
        id = id,
        notificationKey = notificationKey,
        reservationId = reservationId,
        requestId = requestId,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun OutboundNotification.toEntity(): OutboundNotificationEntity =
    OutboundNotificationEntity(
        id = id ?: 0,
        notificationKey = notificationKey,
        reservationId = reservationId,
        requestId = requestId,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
