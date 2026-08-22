package yeo.chi.proejct.pms.reservation.persistent.entity

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
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

        fun confirmed(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity = from(OutboundNotification.from(requestKey, reservation, now, objectMapper))

        fun rejected(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity = from(OutboundNotification.rejected(requestKey, reservation, now, objectMapper))

        fun cancelled(
            requestKey: String,
            reservation: Reservation,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity = from(OutboundNotification.cancelled(requestKey, reservation, now, objectMapper))

        fun cancelRequested(
            requestKey: String,
            reservation: Reservation,
            reason: CancelRequestReason,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity =
            from(OutboundNotification.cancelRequested(requestKey, reservation, reason, now, objectMapper))

        fun changed(
            requestKey: String,
            reservation: Reservation,
            newDateRange: ReservationDateRange,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity =
            from(OutboundNotification.changed(requestKey, reservation, newDateRange, now, objectMapper))

        fun changeRejected(
            requestKey: String,
            reservation: Reservation,
            newDateRange: ReservationDateRange,
            now: OffsetDateTime,
            objectMapper: ObjectMapper,
        ): OutboundNotificationEntity =
            from(OutboundNotification.changeRejected(requestKey, reservation, newDateRange, now, objectMapper))
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
