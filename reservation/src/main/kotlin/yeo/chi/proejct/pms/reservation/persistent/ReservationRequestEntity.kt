package yeo.chi.proejct.pms.reservation.persistent

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
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationRequestAction
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "reservation_requests")
class ReservationRequestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "request_key")
    val requestKey: String,
    @Column(name = "reservation_id")
    val reservationId: Long?,
    @Column(name = "platform_id")
    val platformId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    val action: ReservationRequestAction,
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
)

fun ReservationRequestEntity.toDomain(): ReservationRequest =
    ReservationRequest(
        id = id,
        requestKey = requestKey,
        reservationId = reservationId,
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

fun ReservationRequest.toEntity(): ReservationRequestEntity =
    ReservationRequestEntity(
        id = id ?: 0,
        requestKey = requestKey,
        reservationId = reservationId,
        platformId = platformId,
        action = action,
        initiatedBy = initiatedBy,
        roomCode = roomCode,
        oldDateRange = oldDateRange?.toRange(),
        newDateRange = newDateRange?.toRange(),
        resultStatus = resultStatus,
        rejectReason = rejectReason,
        requestedAt = requestedAt,
    )
