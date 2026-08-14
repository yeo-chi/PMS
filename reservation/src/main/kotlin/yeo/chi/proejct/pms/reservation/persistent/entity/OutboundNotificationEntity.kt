package yeo.chi.proejct.pms.reservation.persistent.entity

import jakarta.persistence.*
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
    val id: Long = 0,
    @Column(name = "notification_key")
    val notificationKey: String,
    @Column(name = "reservation_code")
    val reservationCode: String?,
    @Column(name = "request_key")
    val requestKey: String,
    @Column(name = "event_type")
    val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: OutboundNotificationStatus,
    @Column(name = "retry_count")
    var retryCount: Int,
    @Column(name = "next_retry_at")
    var nextRetryAt: OffsetDateTime,
    @Column(name = "created_at")
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(outboundNotification: OutboundNotification): OutboundNotificationEntity {
            val now = OffsetDateTime.now()
            return OutboundNotificationEntity(
                notificationKey = outboundNotification.notificationKey,
                reservationCode = outboundNotification.reservationCode,
                requestKey = outboundNotification.requestKey,
                eventType = outboundNotification.eventType,
                payload = outboundNotification.payload,
                status = outboundNotification.status,
                retryCount = outboundNotification.retryCount,
                nextRetryAt = outboundNotification.nextRetryAt,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    fun toDomain() =
        OutboundNotification(
            notificationKey = notificationKey,
            reservationCode = reservationCode,
            requestKey = requestKey,
            eventType = eventType,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
        )
}
