package yeo.chi.proejct.pms.operation.persistent.entity

import jakarta.persistence.*
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import java.time.LocalDateTime

@Entity
@Table(name = "hosts")
class HostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "host_id")
    val hostId: String,
    @Column(name = "name")
    val name: String,
    @Column(name = "contact_email")
    val contactEmail: String?,
    @Column(name = "contact_phone")
    val contactPhone: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: HostStatus,
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val createdAt: LocalDateTime?,
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    val updatedAt: LocalDateTime?,
)

fun HostEntity.toDomain(): Host =
    Host(
        id = id,
        hostId = hostId,
        name = name,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Host.toEntity(): HostEntity =
    HostEntity(
        id = id ?: 0,
        hostId = hostId,
        name = name,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
