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
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import java.time.LocalDateTime

@Entity
@Table(name = "ota_channels")
class OtaChannelEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "platform_id")
    val platformId: String,
    @Column(name = "name")
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "integration_mode")
    val integrationMode: OtaChannelIntegrationMode,
    @Column(name = "callback_base_url")
    val callbackBaseUrl: String?,
    @Column(name = "api_key_ref")
    val apiKeyRef: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: OtaChannelStatus,
    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT])
    val createdAt: LocalDateTime?,
    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    val updatedAt: LocalDateTime?,
)

fun OtaChannelEntity.toDomain(): OtaChannel =
    OtaChannel(
        id = id,
        platformId = platformId,
        name = name,
        integrationMode = integrationMode,
        callbackBaseUrl = callbackBaseUrl,
        apiKeyRef = apiKeyRef,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun OtaChannel.toEntity(): OtaChannelEntity =
    OtaChannelEntity(
        id = id ?: 0,
        platformId = platformId,
        name = name,
        integrationMode = integrationMode,
        callbackBaseUrl = callbackBaseUrl,
        apiKeyRef = apiKeyRef,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
