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
import org.hibernate.generator.EventType
import yeo.chi.proejct.pms.operation.domain.Room
import yeo.chi.proejct.pms.operation.domain.RoomStatus
import java.time.LocalDateTime

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "room_code")
    val roomCode: String,
    // 교차 애그리게잇 참조는 값으로만 다룬다(reservation 모듈과 동일 컨벤션) — 실제 FK 제약은
    // DB 레벨에서 그대로 무결성을 보장하므로 @ManyToOne 없이도 검증 가능하다.
    @Column(name = "host_id")
    val hostId: Long,
    @Column(name = "name")
    val name: String,
    @Column(name = "address")
    val address: String?,
    @Column(name = "capacity")
    val capacity: Int?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: RoomStatus,
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val createdAt: LocalDateTime?,
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    val updatedAt: LocalDateTime?,
)

fun RoomEntity.toDomain(): Room =
    Room(
        id = id,
        roomCode = roomCode,
        hostId = hostId,
        name = name,
        address = address,
        capacity = capacity,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Room.toEntity(): RoomEntity =
    RoomEntity(
        id = id ?: 0,
        roomCode = roomCode,
        hostId = hostId,
        name = name,
        address = address,
        capacity = capacity,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
