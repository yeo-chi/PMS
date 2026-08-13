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
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.annotations.Type
import org.hibernate.generator.EventType
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "reservations")
class ReservationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "reservation_no", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val reservationNo: String?,
    @Column(name = "platform_id")
    val platformId: String,
    @Column(name = "platform_reservation_ref")
    val platformReservationRef: String,
    @Column(name = "room_code")
    val roomCode: String,
    @Type(PostgreSQLRangeType::class)
    @Column(name = "date_range", columnDefinition = "daterange")
    val dateRange: Range<LocalDate>,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: ReservationStatus,
    @Version
    @Column(name = "version")
    val version: Int,
    @Column(name = "created_at")
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at")
    val updatedAt: OffsetDateTime,
)

fun ReservationEntity.toDomain(): Reservation =
    Reservation(
        id = id,
        reservationNo = reservationNo,
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        roomCode = roomCode,
        dateRange = dateRange.toReservationDateRange(),
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Reservation.toEntity(): ReservationEntity =
    ReservationEntity(
        id = id ?: 0,
        reservationNo = reservationNo,
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        roomCode = roomCode,
        dateRange = dateRange.toRange(),
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Range<LocalDate>.toReservationDateRange(): ReservationDateRange {
    val startDate = requireNotNull(lower()) { "date_range lower bound must not be null" }
    val endDate = requireNotNull(upper()) { "date_range upper bound must not be null" }
    return ReservationDateRange(startDate = startDate, endDate = endDate)
}

fun ReservationDateRange.toRange(): Range<LocalDate> = Range.closedOpen(startDate, endDate)
