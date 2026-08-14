package yeo.chi.proejct.pms.operation.persistent.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import java.time.LocalDateTime

@Entity
@Table(name = "inbound_events")
class InboundEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "notification_key")
    val notificationKey: String,
    @Column(name = "reservation_no")
    val reservationNo: String,
    @Column(name = "event_type")
    val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    val payload: String,
    @Column(name = "received_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val receivedAt: LocalDateTime?,
) {
    companion object {
        fun from(inboundEvent: InboundEvent) =
            InboundEventEntity(
                notificationKey = inboundEvent.notificationKey,
                reservationNo = inboundEvent.reservationNo,
                eventType = inboundEvent.eventType,
                payload = inboundEvent.payload,
                receivedAt = LocalDateTime.now(),
            )
    }

    fun toDomain() =
        InboundEvent(
            notificationKey = notificationKey,
            reservationNo = reservationNo,
            eventType = eventType,
            payload = payload,
            receivedAt = receivedAt,
        )
}
