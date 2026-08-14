package yeo.chi.proejct.pms.operation.persistent.entity

import jakarta.persistence.*
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
    val id: Long = 0,
    @Column(name = "room_id")
    val roomId: String,
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
) {
    companion object {
        fun from(room: Room) = RoomEntity(
            roomId = room.roomId,
            hostId = room.hostId,
            name = room.name,
            address = room.address,
            capacity = room.capacity,
            status = room.status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    fun toDomain() = Room(
        roomId = roomId,
        hostId = hostId,
        name = name,
        address = address,
        capacity = capacity,
        status = status,
    )
}
