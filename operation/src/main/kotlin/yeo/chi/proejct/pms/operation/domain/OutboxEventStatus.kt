package yeo.chi.proejct.pms.operation.domain

enum class OutboxEventStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD,
}
