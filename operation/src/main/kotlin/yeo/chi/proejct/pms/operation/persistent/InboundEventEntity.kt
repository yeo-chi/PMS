package yeo.chi.proejct.pms.operation.persistent

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
    val id: Long,
    @Column(name = "notification_key")
    val notificationKey: String,
    @Column(name = "reservation_no")
    val reservationNo: String,
    @Column(name = "event_type")
    val eventType: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    val payload: String,
    // append-only 테이블이라 ON UPDATE가 없다 — INSERT 시점에만 재조회한다.
    @Column(name = "received_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val receivedAt: LocalDateTime?,
)

fun InboundEventEntity.toDomain(): InboundEvent =
    InboundEvent(
        id = id,
        notificationKey = notificationKey,
        reservationNo = reservationNo,
        eventType = eventType,
        payload = payload,
        receivedAt = receivedAt,
    )

fun InboundEvent.toEntity(): InboundEventEntity =
    InboundEventEntity(
        id = id ?: 0,
        notificationKey = notificationKey,
        reservationNo = reservationNo,
        eventType = eventType,
        payload = payload,
        receivedAt = receivedAt,
    )
