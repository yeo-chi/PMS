package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class Host(
    val id: Long?,
    val hostCode: String,
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val status: HostStatus,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
