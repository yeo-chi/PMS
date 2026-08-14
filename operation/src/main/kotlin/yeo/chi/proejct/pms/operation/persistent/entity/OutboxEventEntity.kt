package yeo.chi.proejct.pms.operation.persistent.entity

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
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.domain.RoomChannelListing
import java.time.LocalDateTime

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
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
) {
    companion object {
        fun from(outboxEvent: OutboxEvent) =
            OutboxEventEntity(
                outboxKey = outboxEvent.outboxKey,
                targetType = outboxEvent.targetType,
                targetCode = outboxEvent.targetCode,
                reservationNo = outboxEvent.reservationNo,
                eventType = outboxEvent.eventType,
                payload = outboxEvent.payload,
                status = outboxEvent.status,
                retryCount = outboxEvent.retryCount,
                nextRetryAt = outboxEvent.nextRetryAt,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        // 예약을 소유한 원 채널 대상 (inbound event 1건당 항상 1건).
        fun primary(
            inboundEvent: InboundEvent,
            platformId: String,
        ): OutboxEventEntity = from(OutboxEvent.of(inboundEvent, platformId))

        // 같은 방을 노출 중인 다른 활성 채널로의 팬아웃 대상.
        fun fanOut(
            inboundEvent: InboundEvent,
            listing: RoomChannelListing,
            eventType: String,
        ): OutboxEventEntity = from(OutboxEvent.ofB(inboundEvent, listing, eventType))

        // 방을 소유한 호스트 대상.
        fun host(
            inboundEvent: InboundEvent,
            hostId: String,
            eventType: String,
        ): OutboxEventEntity = from(OutboxEvent.ofHost(inboundEvent, hostId, eventType))
    }

    fun toDomain() =
        OutboxEvent(
            outboxKey = outboxKey,
            targetType = targetType,
            targetCode = targetCode,
            reservationNo = reservationNo,
            eventType = eventType,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
        )
}
