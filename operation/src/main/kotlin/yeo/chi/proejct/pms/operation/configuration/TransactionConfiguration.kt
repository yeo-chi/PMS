package yeo.chi.proejct.pms.operation.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class TransactionConfiguration {

    // MySQL InnoDB의 기본 격리수준(REPEATABLE READ)에서는 SELECT ... FOR UPDATE SKIP LOCKED가
    // ORDER BY + LIMIT와 결합될 때 갭 락/넥스트 키 락의 영향을 받아 동시 폴링 트랜잭션이 잠기지 않은
    // row인데도 결과에서 누락시키는 경우가 있다(OutboxEventDispatchWorker의 폴링 쿼리에서 실제로
    // 재현됨). READ COMMITTED로 낮추면 각 statement가 항상 최신 커밋 스냅샷을 보고, 이 큐 폴링
    // 패턴에서 InnoDB가 일반적으로 권장하는 격리수준이기도 하다.
    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }
}
