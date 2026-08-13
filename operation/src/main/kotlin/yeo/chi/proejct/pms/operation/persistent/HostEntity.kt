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
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import java.time.LocalDateTime

@Entity
@Table(name = "hosts")
class HostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "host_code")
    val hostCode: String,
    @Column(name = "name")
    val name: String,
    @Column(name = "contact_email")
    val contactEmail: String?,
    @Column(name = "contact_phone")
    val contactPhone: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: HostStatus,
    // created_at/updated_at은 애플리케이션이 채우지 않는다 — MySQL이 DEFAULT/ON UPDATE로 직접 관리한다.
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
        hostCode = hostCode,
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
        hostCode = hostCode,
        name = name,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
