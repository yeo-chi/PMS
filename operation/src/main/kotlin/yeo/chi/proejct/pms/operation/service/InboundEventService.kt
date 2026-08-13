package yeo.chi.proejct.pms.operation.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.operation.controller.InboundEventRequest
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.persistent.InboundEventRepository
import yeo.chi.proejct.pms.operation.persistent.OutboxEventRepository
import yeo.chi.proejct.pms.operation.persistent.toEntity
import java.time.LocalDateTime

// 기획문서 4.1~4.3이 다루는 4개 이벤트 타입. 스키마 컬럼 자체엔 CHECK 제약이 없어(자유 확장 가능)
// DB가 걸러주지 않으므로, 애플리케이션 경계에서 검증한다.
private val KNOWN_EVENT_TYPES =
    setOf(
        "RESERVATION_CONFIRMED",
        "RESERVATION_REJECTED",
        "CANCEL_REQUESTED",
        "RESERVATION_CANCELLED",
    )

@Service
class InboundEventService(
    private val inboundEventRepository: InboundEventRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun receive(request: InboundEventRequest) {
        require(request.eventType in KNOWN_EVENT_TYPES) {
            "알 수 없는 eventType입니다: ${request.eventType}"
        }

        // 요청 단위 멱등성: 정확히 같은 notification_key 재수신은 여기서 걸러진다(reservation의
        // findByRequestKey와 동일 패턴). 신규 처리든 멱등 재처리든 호출자에게는 항상 성공으로 보인다.
        if (inboundEventRepository.findByNotificationKey(request.notificationKey) != null) return

        try {
            processNew(request)
        } catch (duplicateKey: DataIntegrityViolationException) {
            // uq_notification_key 위반 = 두 요청이 findByNotificationKey를 동시에 통과한 극단적 레이스.
            // 이미 다른 스레드가 (같은 내용으로) 처리했으므로 재조회 없이 성공으로 간주한다.
        }
    }

    private fun processNew(request: InboundEventRequest) {
        transactionTemplate.execute {
            val payloadJson = objectMapper.writeValueAsString(request.payload)
            inboundEventRepository.saveAndFlush(
                InboundEvent(
                    id = null,
                    notificationKey = request.notificationKey,
                    reservationNo = request.reservationNo,
                    eventType = request.eventType,
                    payload = payloadJson,
                    receivedAt = null,
                ).toEntity(),
            )

            // 교차 DB라 조인이 불가능하므로, 대상 판정에 필요한 정보는 예약 서버가 payload에 미리
            // 담아 보낸 것을 그대로 쓴다. 4개 이벤트 타입 모두 통보 대상은 항상 그 예약을 소유한
            // OTA 채널이다(CANCEL_REQUESTED도 호스트가 시작했을 뿐 통보 대상은 호스트가 아니다).
            val platformId =
                requireNotNull(request.payload.get("platformId")?.asText()) {
                    "payload에 platformId가 없습니다: notificationKey=${request.notificationKey}"
                }
            outboxEventRepository.saveAndFlush(
                OutboxEvent(
                    id = null,
                    outboxKey = "${request.reservationNo}:${OutboxTargetType.OTA_CHANNEL}:$platformId:${request.eventType}",
                    targetType = OutboxTargetType.OTA_CHANNEL,
                    targetCode = platformId,
                    reservationNo = request.reservationNo,
                    eventType = request.eventType,
                    payload = payloadJson,
                    status = OutboxEventStatus.PENDING,
                    retryCount = 0,
                    // next_retry_at은 created_at/updated_at과 달리 DB가 관리하지 않으므로(plan/19.md 6번과
                    // 동일 원칙) 즉시 폴링 대상이 되도록 애플리케이션이 채운다.
                    nextRetryAt = LocalDateTime.now(),
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            )
        }
    }
}
