package yeo.chi.proejct.pms.operation.persistent

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findByOutboxKey(outboxKey: String): OutboxEventEntity?

    // reservation의 OutboundNotificationRepository.findBatchForDispatch(#18)와 완전히 대칭.
    // 이 락은 호출 트랜잭션이 끝나야 풀리므로 반드시 활성 트랜잭션 안에서만 호출해야 한다.
    @Query(
        value = """
            SELECT * FROM outbox_events
            WHERE status IN ('PENDING', 'FAILED') AND next_retry_at <= :now
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findBatchForDispatch(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int,
    ): List<OutboxEventEntity>
}
