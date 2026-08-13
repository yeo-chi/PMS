package yeo.chi.proejct.pms.reservation.service

import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import yeo.chi.proejct.pms.reservation.configuration.OutboundNotificationDispatchProperties
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationEntity
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import java.time.OffsetDateTime

private const val INBOUND_EVENTS_PATH = "/api/inbound-events"

@Service
class OutboundNotificationDispatchWorker(
    private val outboundNotificationRepository: OutboundNotificationRepository,
    private val reservationRepository: ReservationRepository,
    private val transactionTemplate: TransactionTemplate,
    private val operationRestClient: RestClient,
    private val properties: OutboundNotificationDispatchProperties,
) {

    // SELECT ... FOR UPDATE SKIP LOCKED로 얻은 락은 이 트랜잭션이 끝나야 풀린다. "배치 조회 + 각 건 발송 +
    // 상태 갱신"을 하나의 트랜잭션으로 묶어야 조회~처리 사이의 틈에 다른 워커가 같은 row를 다시 집어가지 않는다.
    @Scheduled(fixedDelayString = "\${outbound-notification.poll-interval-millis}")
    fun dispatchPendingNotifications() {
        transactionTemplate.execute {
            val batch = outboundNotificationRepository.findBatchForDispatch(OffsetDateTime.now(), properties.batchSize)
            batch.forEach { notification -> dispatchOne(notification) }
        }
    }

    // 한 건의 발송 실패가 배치의 나머지 건 처리를 막지 않도록(배치 트랜잭션 전체가 롤백되지 않도록)
    // 여기서 예외를 잡는다.
    private fun dispatchOne(notification: OutboundNotificationEntity) {
        try {
            val reservationNo =
                requireNotNull(reservationRepository.findById(notification.reservationId).orElseThrow().reservationNo) {
                    "저장된 예약은 reservation_no를 가지고 있어야 합니다"
                }

            operationRestClient
                .post()
                .uri(INBOUND_EVENTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    InboundEventRequest(
                        notificationKey = notification.notificationKey,
                        reservationNo = reservationNo,
                        eventType = notification.eventType,
                        payload = notification.payload,
                    ),
                ).retrieve()
                .toBodilessEntity()

            notification.status = OutboundNotificationStatus.SENT
        } catch (deliveryFailure: RestClientException) {
            // 4xx/5xx/타임아웃/커넥션 실패를 전부 "발송 실패"로 취급한다 (모두 RestClientException으로 도달).
            notification.retryCount += 1
            notification.status =
                if (notification.retryCount >= properties.maxRetryCount) {
                    OutboundNotificationStatus.DEAD
                } else {
                    OutboundNotificationStatus.FAILED
                }
            notification.nextRetryAt = computeNextRetryAt(notification.retryCount, properties)
        }
        outboundNotificationRepository.saveAndFlush(notification)
    }
}

private fun computeNextRetryAt(
    retryCount: Int,
    properties: OutboundNotificationDispatchProperties,
): OffsetDateTime {
    val backoffSeconds =
        minOf(properties.maxBackoffSeconds, properties.initialBackoffSeconds * (1L shl (retryCount - 1)))
    return OffsetDateTime.now().plusSeconds(backoffSeconds)
}
