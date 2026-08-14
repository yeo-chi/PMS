package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class OtaChannel(
    val id: Long?,
    val platformId: String,
    val name: String,
    val integrationMode: OtaChannelIntegrationMode,
    val callbackBaseUrl: String?,
    // 시크릿 매니저 참조 키. 평문 시크릿 자체를 담지 않는다.
    val apiKeyRef: String?,
    val status: OtaChannelStatus,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

enum class OtaChannelIntegrationMode {
    SYNC,
    ASYNC,
}

enum class OtaChannelStatus {
    ACTIVE,
    SUSPENDED,
}
