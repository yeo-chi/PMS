package yeo.chi.proejct.pms.reservation.persistent.entity

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import jakarta.persistence.*
import org.hibernate.annotations.Generated
import org.hibernate.annotations.Type
import org.hibernate.generator.EventType
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
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
    val id: Long = 0,
    @Column(name = "reservation_code", insertable = false, updatable = false, unique = true)
    @Generated(event = [EventType.INSERT])
    val reservationCode: String,
    @Column(name = "platform_id")
    val platformId: String,
    @Column(name = "platform_reservation_ref")
    val platformReservationRef: String,
    @Column(name = "room_code")
    val roomCode: String,
    @Type(PostgreSQLRangeType::class)
    @Column(name = "date_range", columnDefinition = "daterange")
    var dateRange: Range<LocalDate>,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: ReservationStatus,
    @Version
    @Column(name = "version")
    val version: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by")
    val initiatedBy: RequestInitiator,
    @Column(name = "created_at")
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(reservation: Reservation, offsetDateTime: OffsetDateTime) =
            ReservationEntity(
                reservationCode = reservation.reservationCode,
                platformId = reservation.platformId,
                platformReservationRef = reservation.platformReservationRef,
                roomCode = reservation.roomId,
                dateRange = reservation.dateRange.toRange(),
                status = reservation.status,
                version = 1,
                initiatedBy = reservation.initiatedBy,
                createdAt = offsetDateTime,
                updatedAt = offsetDateTime,
            )
    }

    fun toDomain() =
        Reservation(
            reservationCode = reservationCode,
            platformId = platformId,
            platformReservationRef = platformReservationRef,
            roomId = roomCode,
            dateRange = dateRange.toReservationDateRange(),
            status = status,
            initiatedBy = initiatedBy,
        )
}

fun Range<LocalDate>.toReservationDateRange(): ReservationDateRange {
    val startDate = requireNotNull(lower()) { "date_range lower bound must not be null" }
    val endDate = requireNotNull(upper()) { "date_range upper bound must not be null" }
    return ReservationDateRange(startDate = startDate, endDate = endDate)
}

