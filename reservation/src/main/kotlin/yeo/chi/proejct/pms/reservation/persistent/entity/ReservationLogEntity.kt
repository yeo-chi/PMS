package yeo.chi.proejct.pms.reservation.persistent.entity

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import yeo.chi.proejct.pms.reservation.domain.ReservationLog
import yeo.chi.proejct.pms.reservation.domain.ReservationLogAction
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "reservation_requests")
class ReservationLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "request_key")
    val requestKey: String,
    @Column(name = "reservation_code")
    val reservationCode: String?,
    @Column(name = "platform_id")
    val platformId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    val action: ReservationLogAction,
    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by")
    val initiatedBy: RequestInitiator,
    @Column(name = "room_code")
    val roomCode: String?,
    @Type(PostgreSQLRangeType::class)
    @Column(name = "old_date_range", columnDefinition = "daterange")
    val oldDateRange: Range<LocalDate>?,
    @Type(PostgreSQLRangeType::class)
    @Column(name = "new_date_range", columnDefinition = "daterange")
    val newDateRange: Range<LocalDate>?,
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status")
    val resultStatus: RequestResultStatus,
    @Column(name = "reject_reason")
    val rejectReason: String?,
    @Column(name = "requested_at")
    val requestedAt: OffsetDateTime,
) {
    companion object {
        fun from(reservationLog: ReservationLog) =
            ReservationLogEntity(
                id = reservationLog.id ?: 0,
                requestKey = reservationLog.requestKey,
                reservationCode = reservationLog.reservationCode,
                platformId = reservationLog.platformId,
                action = reservationLog.action,
                initiatedBy = reservationLog.initiatedBy,
                roomCode = reservationLog.roomCode,
                oldDateRange = reservationLog.oldDateRange?.toRange(),
                newDateRange = reservationLog.newDateRange?.toRange(),
                resultStatus = reservationLog.resultStatus,
                rejectReason = reservationLog.rejectReason,
                requestedAt = reservationLog.requestedAt,
            )
    }

    fun toDomain() =
        ReservationLog(
            id = id,
            requestKey = requestKey,
            reservationCode = reservationCode,
            platformId = platformId,
            action = action,
            initiatedBy = initiatedBy,
            roomCode = roomCode,
            oldDateRange = oldDateRange?.toReservationDateRange(),
            newDateRange = newDateRange?.toReservationDateRange(),
            resultStatus = resultStatus,
            rejectReason = rejectReason,
            requestedAt = requestedAt,
        )
}
