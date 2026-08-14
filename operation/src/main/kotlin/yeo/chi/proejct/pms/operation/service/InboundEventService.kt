package yeo.chi.proejct.pms.operation.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.operation.controller.data.InboundEventRequest
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import yeo.chi.proejct.pms.operation.persistent.repository.*


@Service
class InboundEventService(
    private val inboundEventRepository: InboundEventRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val roomRepository: RoomRepository,
    private val roomChannelListingRepository: RoomChannelListingRepository,
    private val otaChannelRepository: OtaChannelRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun receive(request: InboundEventRequest) {
        require(request.eventType in KNOWN_EVENT_TYPES) { "알 수 없는 eventType입니다: ${request.eventType}" }

        // 요청 단위 멱등성: 정확히 같은 notification_key 재수신은 여기서 걸러진다(reservation의
        // findByRequestKey와 동일 패턴). 신규 처리든 멱등 재처리든 호출자에게는 항상 성공으로 보인다.
        if (inboundEventRepository.findByNotificationKey(request.notificationKey) == null) {
            try {
                processNew(request)
            } catch (_: DataIntegrityViolationException) {
                // uq_notification_key 위반 = 두 요청이 findByNotificationKey를 동시에 통과한 극단적 레이스.
                // 이미 다른 스레드가 (같은 내용으로) 처리했으므로 재조회 없이 성공으로 간주한다.
            }
        }
    }

    private fun processNew(request: InboundEventRequest) {
        transactionTemplate.execute {
            val payloadJson = request.payload
            val inboundEvent = request.toDomain(objectMapper)

            inboundEventRepository.saveAndFlush(inboundEvent.toEntity())

            // 교차 DB라 조인이 불가능하므로, 대상 판정에 필요한 정보는 예약 서버가 payload에 미리
            // 담아 보낸 것을 그대로 쓴다. 4개 이벤트 타입 모두 통보 대상은 항상 그 예약을 소유한
            // OTA 채널이다(CANCEL_REQUESTED도 호스트가 시작했을 뿐 통보 대상은 호스트가 아니다).
            val platformId =
                requireNotNull(payloadJson.get("platformId")?.asText()) {
                    "payload에 platformId가 없습니다: notificationKey=${request.notificationKey}"
                }

            outboxEventRepository.saveAndFlush(OutboxEvent.of(inboundEvent, platformId).toEntity())

            FAN_OUT_EVENT_TYPE_BY_SOURCE[request.eventType]?.let { fanOutEventType ->
                fanOutToOtherChannels(inboundEvent, payloadJson, platformId, fanOutEventType)
            }
        }
    }

    private fun fanOutToOtherChannels(
        inboundEvent: InboundEvent,
        payloadJson: JsonNode,
        originatingPlatformId: String,
        fanOutEventType: String,
    ) {
        val roomCode =
            requireNotNull(payloadJson.get("roomCode")?.asText()) {
                "payload에 roomCode가 없습니다: notificationKey=${inboundEvent.notificationKey}"
            }
        // 방 마스터 데이터가 아직 없으면(이 프로젝트에 마스터 데이터 등록 API가 없어 흔히 있을 수 있는
        // 상태) 팬아웃 대상도 없다는 뜻이므로 조용히 건너뛴다 — 원본 통보(이벤트를 발생시킨 채널
        // 대상)는 이미 저장됐으니 실패로 처리하지 않는다.
        val room = roomRepository.findByRoomCode(roomCode) ?: return

        getRoomChannelListings(room.roomId)
            .filter { it.status == RoomChannelListingStatus.ACTIVE && it.platformId != originatingPlatformId }
            // 리스팅은 활성이어도 채널 자체가 SUSPENDED일 수 있다 — 중단된 채널에 통보를 보내는 건
            // 의미가 없으므로 채널 상태도 함께 확인한다(#21이 이미 만들어둔
            // OtaChannelRepository.findByPlatformId를 재사용, 새 조회 메서드 불필요).
            .filter { listing -> otaChannelRepository.findByPlatformId(listing.platformId)?.status == OtaChannelStatus.ACTIVE }
            .forEach { listing ->
                outboxEventRepository.saveAndFlush(
                    OutboxEvent.ofB(inboundEvent, listing, fanOutEventType).toEntity()
                )
            }
    }

    private fun getRoomChannelListings(roomId: String) =
        roomChannelListingRepository.findByRoomId(roomId).map { it.toDomain() }

    companion object {
        // 기획문서 4.1~4.3이 다루는 4개 이벤트 타입. 스키마 컬럼 자체엔 CHECK 제약이 없어(자유 확장 가능)
        // DB가 걸러주지 않으므로, 애플리케이션 경계에서 검증한다.
        private val KNOWN_EVENT_TYPES =
            setOf(
                "RESERVATION_CONFIRMED",
                "RESERVATION_REJECTED",
                "CANCEL_REQUESTED",
                "RESERVATION_CANCELLED",
            )

        // 재고가 실제로 바뀌는(방의 점유 상태가 바뀌는) 두 이벤트만 다른 채널로 팬아웃한다. CANCEL_REQUESTED는
        // PENDING_CANCEL도 여전히 점유 상태라 재고 변화가 없고, RESERVATION_REJECTED는 애초에 점유된 적이
        // 없다(BOOK/CHANGE 겹침 거부). CHANGE(RESERVATION_CHANGED)는 기존 구간 오픈+신규 구간 마감이 동시에
        // 일어나 로직이 더 복잡해지므로 이번 티켓 범위에서 제외한다.
        private val FAN_OUT_EVENT_TYPE_BY_SOURCE =
            mapOf(
                "RESERVATION_CONFIRMED" to "INVENTORY_CLOSED",
                "RESERVATION_CANCELLED" to "INVENTORY_REOPENED",
            )
    }
}
