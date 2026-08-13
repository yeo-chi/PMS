package yeo.chi.proejct.pms.operation.persistent

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import java.time.LocalDateTime

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "outbox_key")
    val outboxKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    val targetType: OutboxTargetType,
    @Column(name = "target_code")
    val targetCode: String,
    @Column(name = "reservation_no")
    val reservationNo: String,
    @Column(name = "event_type")
    val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: OutboxEventStatus,
    @Column(name = "retry_count")
    var retryCount: Int,
    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime,
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val createdAt: LocalDateTime?,
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    val updatedAt: LocalDateTime?,
)

fun OutboxEventEntity.toDomain(): OutboxEvent =
    OutboxEvent(
        id = id,
        outboxKey = outboxKey,
        targetType = targetType,
        targetCode = targetCode,
        reservationNo = reservationNo,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun OutboxEvent.toEntity(): OutboxEventEntity =
    OutboxEventEntity(
        id = id ?: 0,
        outboxKey = outboxKey,
        targetType = targetType,
        targetCode = targetCode,
        reservationNo = reservationNo,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
