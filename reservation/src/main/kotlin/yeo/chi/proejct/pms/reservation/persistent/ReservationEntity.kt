package yeo.chi.proejct.pms.reservation.persistent

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "reservation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_reservation_platform_code_code",
            columnNames = ["platform_code", "code"],
        ),
    ],
)
class ReservationEntity(
    @Column(name = "platform_code", nullable = false, updatable = false)
    val platformCode: String,

    @Column(name = "room_id", nullable = false, updatable = false)
    val roomId: Long,

    @Column(name = "user_identify_code", nullable = false, updatable = false)
    val userIdentifyCode: String,

    @Column(name = "start_date", nullable = false, updatable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false, updatable = false)
    val endDate: LocalDate,

    @Column(name = "reserved_at", nullable = false, updatable = false)
    val reservedAt: LocalDateTime,

    // 플랫폼이 발급한 예약 코드. platformCode + code 조합이 멱등성 키이며,
    // 위 @Table(uniqueConstraints=...)로 DB 레벨 중복 저장을 막는다.
    @Column(name = "code", nullable = false, updatable = false)
    val code: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        internal set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ReservationStatus = ReservationStatus.CONFIRMED
        internal set
}

fun ReservationEntity.toDomain(): Reservation = Reservation(
    platformCode = platformCode,
    roomId = roomId,
    userIdentifyCode = userIdentifyCode,
    startDate = startDate,
    endDate = endDate,
    reservedAt = reservedAt,
    code = code,
    status = status,
    id = id,
)

fun Reservation.toEntity(): ReservationEntity {
    val entity = ReservationEntity(
        platformCode = platformCode,
        roomId = roomId,
        userIdentifyCode = userIdentifyCode,
        startDate = startDate,
        endDate = endDate,
        reservedAt = reservedAt,
        code = code,
    )
    entity.id = id
    entity.status = status
    return entity
}
