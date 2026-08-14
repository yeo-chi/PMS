package yeo.chi.proejct.pms.operation.service

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import yeo.chi.proejct.pms.operation.configuration.OutboxEventDispatchProperties
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.persistent.entity.OutboxEventEntity
import yeo.chi.proejct.pms.operation.persistent.repository.HostRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OtaChannelRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OutboxEventRepository
import java.time.LocalDateTime

@Service
class OutboxEventDispatchWorker(
    private val outboxEventRepository: OutboxEventRepository,
    private val otaChannelRepository: OtaChannelRepository,
    private val hostRepository: HostRepository,
    private val transactionTemplate: TransactionTemplate,
    private val channelDeliveryRestClient: RestClient,
    private val properties: OutboxEventDispatchProperties,
) {
    // SELECT ... FOR UPDATE SKIP LOCKED로 얻은 락은 이 트랜잭션이 끝나야 풀린다(reservation #18과
    // 동일한 이유) — "배치 조회 + 각 건 발송 + 상태 갱신"을 하나의 트랜잭션으로 묶는다.
    @Scheduled(fixedDelayString = "\${outbox-event.poll-interval-millis}")
    fun dispatchPendingOutboxEvents() {
        transactionTemplate.execute {
            outboxEventRepository
                .findBatchForDispatch(LocalDateTime.now(), properties.batchSize)
                .forEach { dispatchOne(it) }
        }
    }

    // 한 건의 발송 실패가 배치의 나머지 건 처리를 막지 않도록 여기서 예외를 잡는다.
    private fun dispatchOne(event: OutboxEventEntity) {
        try {
            val delivered =
                when (event.targetType) {
                    OutboxTargetType.OTA_CHANNEL -> deliverToOtaChannel(event)
                    OutboxTargetType.HOST -> deliverToHost(event)
                }
            check(delivered) { "채널 설정을 찾을 수 없어 전달하지 못했습니다: targetCode=${event.targetCode}" }
            event.status = OutboxEventStatus.SENT
        } catch (_: Exception) {
            // 실제 HTTP 실패(RestClientException)와 "채널/콜백 URL 설정 없음"(check 실패)을 같은
            // 재시도 경로로 합친다 — 둘 다 "지금은 전달 못 했다"는 점에서 동일하게 취급해도 무방하다
            // (plan/21.md 5번 결정 사항: 실패 원인을 세분화하지 않는다).
            event.retryCount += 1
            event.status =
                if (event.retryCount >= properties.maxRetryCount) {
                    OutboxEventStatus.DEAD
                } else {
                    OutboxEventStatus.FAILED
                }
            event.nextRetryAt = calculateNextRetryAt(event.retryCount, properties)
        }
        outboxEventRepository.saveAndFlush(event)
    }

    private fun deliverToOtaChannel(event: OutboxEventEntity): Boolean {
        val channel = otaChannelRepository.findByPlatformId(event.targetCode) ?: return false
        val callbackBaseUrl = channel.callbackBaseUrl ?: return false

        channelDeliveryRestClient
            .post()
            // 채널마다 콜백 URL이 다르므로(고정 baseUrl 없음) 절대 URI를 그대로 지정한다.
            .uri(callbackBaseUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ChannelNotificationRequest.from(event))
            .retrieve()
            .toBodilessEntity()

        return true
    }

    // hosts 테이블엔 API로 호출 가능한 웹훅/콜백 채널이 없다(이메일/전화만) — 이메일/SMS 발송은 이
    // 프로젝트의 신뢰성 메커니즘 검증 범위를 벗어나는 별도 인프라가 필요해 스텁으로 남긴다
    // (plan/21.md 6번 결정 사항). 현재 4개 이벤트 타입 중 어느 것도 HOST를 대상으로 만들지 않아
    // 이 분기는 지금은 실행되지 않는다.
    private fun deliverToHost(event: OutboxEventEntity): Boolean {
        hostRepository.findByHostId(event.targetCode) ?: return false
        logger.info(
            "[STUB] HOST 알림: outboxKey={}, targetCode={}, eventType={}",
            event.outboxKey,
            event.targetCode,
            event.eventType,
        )
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OutboxEventDispatchWorker::class.java)
    }
}

private fun calculateNextRetryAt(
    retryCount: Int,
    properties: OutboxEventDispatchProperties,
): LocalDateTime {
    val backoffSeconds =
        minOf(properties.maxBackoffSeconds, properties.initialBackoffSeconds * (1L shl (retryCount - 1)))

    return LocalDateTime.now().plusSeconds(backoffSeconds)
}
