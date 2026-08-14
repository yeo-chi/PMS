package yeo.chi.proejct.pms.reservation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import yeo.chi.proejct.pms.reservation.persistent.entity.OutboundNotificationEntity
import java.time.OffsetDateTime

interface OutboundNotificationRepository : JpaRepository<OutboundNotificationEntity, Long> {
    fun findByNotificationKey(notificationKey: String): OutboundNotificationEntity?

    // 여러 워커 인스턴스가 동시에 폴링해도 같은 row를 중복 발송하지 않도록 FOR UPDATE SKIP LOCKED를 쓴다.
    // 파생 쿼리 메서드로는 표현할 수 없어 스키마 주석의 예시 쿼리를 네이티브 쿼리로 그대로 옮긴다.
    // 이 락은 호출 트랜잭션이 끝나야 풀리므로 반드시 활성 트랜잭션 안에서만 호출해야 한다.
    @Query(
        value = """
            SELECT * FROM outbound_notifications
            WHERE status IN ('PENDING', 'FAILED') AND next_retry_at <= :now
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findBatchForDispatch(
        @Param("now") now: OffsetDateTime,
        @Param("limit") limit: Int,
    ): List<OutboundNotificationEntity>
}
