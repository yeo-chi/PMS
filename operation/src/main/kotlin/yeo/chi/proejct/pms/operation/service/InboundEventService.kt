package yeo.chi.proejct.pms.operation.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.operation.controller.data.InboundEventRequest
import yeo.chi.proejct.pms.operation.domain.InboundEvent
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.RoomChannelListingStatus
import yeo.chi.proejct.pms.operation.persistent.entity.InboundEventEntity
import yeo.chi.proejct.pms.operation.persistent.entity.OutboxEventEntity
import yeo.chi.proejct.pms.operation.persistent.repository.HostRepository
import yeo.chi.proejct.pms.operation.persistent.repository.InboundEventRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OtaChannelRepository
import yeo.chi.proejct.pms.operation.persistent.repository.OutboxEventRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomChannelListingRepository
import yeo.chi.proejct.pms.operation.persistent.repository.RoomRepository


@Service
class InboundEventService(
    private val inboundEventRepository: InboundEventRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val roomRepository: RoomRepository,
    private val roomChannelListingRepository: RoomChannelListingRepository,
    private val otaChannelRepository: OtaChannelRepository,
    private val hostRepository: HostRepository,
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
            } catch (raceOrOtherViolation: DataIntegrityViolationException) {
                // uq_notification_key 위반으로 인한 극단적 레이스(두 요청이 findByNotificationKey를
                // 동시에 통과)라면, 다른 스레드가 이미 (같은 내용으로) 처리했다는 뜻이라 성공으로 간주해도
                // 안전하다 — 하지만 재확인 없이 그렇게 단정하면, 팬아웃/호스트 통보 단계의 다른 제약
                // 위반(예: outbox_key 충돌)으로 트랜잭션 전체가 롤백된 경우까지 성공으로 위장해버려
                // 통보가 조용히 유실된다(reservation의 recordConflict와 동일한 재확인 패턴).
                if (inboundEventRepository.findByNotificationKey(request.notificationKey) == null) {
                    throw raceOrOtherViolation
                }
            }
        }
    }

    private fun processNew(request: InboundEventRequest) {
        transactionTemplate.execute {
            val payloadJson = request.payload
            val inboundEvent = request.toDomain(objectMapper)

            inboundEventRepository.saveAndFlush(InboundEventEntity.from(inboundEvent))

            // 교차 DB라 조인이 불가능하므로, 대상 판정에 필요한 정보는 예약 서버가 payload에 미리
            // 담아 보낸 것을 그대로 쓴다. 5개 이벤트 타입 모두 통보 대상은 항상 그 예약을 소유한
            // OTA 채널이다(CANCEL_REQUESTED도 호스트가 시작했을 뿐 통보 대상은 호스트가 아니다).
            val platformId =
                requireNotNull(payloadJson.get("platformId")?.asText()) {
                    "payload에 platformId가 없습니다: notificationKey=${request.notificationKey}"
                }

            outboxEventRepository.saveAndFlush(OutboxEventEntity.primary(inboundEvent, platformId))

            FAN_OUT_EVENT_TYPE_BY_SOURCE[request.eventType]?.let { fanOutEventType ->
                fanOutToOtherChannels(inboundEvent, payloadJson, platformId, fanOutEventType)
            }

            if (request.eventType in HOST_NOTIFY_EVENT_TYPES) {
                notifyHost(inboundEvent, payloadJson)
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
        val room = roomRepository.findByRoomId(roomCode) ?: return

        getRoomChannelListings(room.roomId)
            .filter { it.status == RoomChannelListingStatus.ACTIVE && it.platformId != originatingPlatformId }
            // 리스팅은 활성이어도 채널 자체가 SUSPENDED일 수 있다 — 중단된 채널에 통보를 보내는 건
            // 의미가 없으므로 채널 상태도 함께 확인한다(#21이 이미 만들어둔
            // OtaChannelRepository.findByPlatformId를 재사용, 새 조회 메서드 불필요).
            .filter { listing -> otaChannelRepository.findByPlatformId(listing.platformId)?.status == OtaChannelStatus.ACTIVE }
            .forEach { listing ->
                outboxEventRepository.saveAndFlush(OutboxEventEntity.fanOut(inboundEvent, listing, fanOutEventType))
            }
    }

    private fun getRoomChannelListings(roomId: String) =
        roomChannelListingRepository.findByRoomId(roomId).map { it.toDomain() }

    // 예약 변경은 호스트 소유 자산의 점유 기간이 바뀌는 일이라 호스트에게도 알려야 한다(기획문서
    // 4.4). 다른 이벤트 타입은 호스트가 이미 알고 있거나(호스트 본인이 발의한 CANCEL_REQUEST)
    // 관심 대상이 아니라(BOOK/CANCEL_CONFIRM은 OTA·채널 재고 이슈) 대상에서 제외한다.
    private fun notifyHost(
        inboundEvent: InboundEvent,
        payloadJson: JsonNode,
    ) {
        val roomCode =
            requireNotNull(payloadJson.get("roomCode")?.asText()) {
                "payload에 roomCode가 없습니다: notificationKey=${inboundEvent.notificationKey}"
            }
        // 방/호스트 마스터 데이터가 없으면 통보 대상도 없다는 뜻이므로 조용히 건너뛴다 — 원본 통보는
        // 이미 저장됐으니 실패로 처리하지 않는다(fanOutToOtherChannels와 동일한 원칙).
        val room = roomRepository.findByRoomId(roomCode) ?: return
        val host = hostRepository.findById(room.hostId).orElse(null) ?: return

        outboxEventRepository.saveAndFlush(OutboxEventEntity.host(inboundEvent, host.hostId, inboundEvent.eventType))
    }

    companion object {
        // 기획문서 4.1~4.4가 다루는 5개 이벤트 타입. 스키마 컬럼 자체엔 CHECK 제약이 없어(자유 확장 가능)
        // DB가 걸러주지 않으므로, 애플리케이션 경계에서 검증한다.
        private val KNOWN_EVENT_TYPES =
            setOf(
                "RESERVATION_CONFIRMED",
                "RESERVATION_REJECTED",
                "CANCEL_REQUESTED",
                "RESERVATION_CANCELLED",
                "RESERVATION_CHANGED",
            )

        // 재고가 실제로 바뀌는(방의 점유 상태가 바뀌는) 이벤트만 다른 채널로 팬아웃한다. CANCEL_REQUESTED는
        // PENDING_CANCEL도 여전히 점유 상태라 재고 변화가 없고, RESERVATION_REJECTED는 애초에 점유된 적이
        // 없다(BOOK/CHANGE 겹침 거부). RESERVATION_CHANGED는 기존 구간 오픈+신규 구간 마감이 동시에 일어나지만,
        // payload에 이미 oldDateRange/newDateRange가 함께 담겨 있어(ReservationChangedPayload) 별도
        // 이벤트로 쪼개지 않고 INVENTORY_CHANGED 하나로 두 정보를 그대로 전달한다.
        private val FAN_OUT_EVENT_TYPE_BY_SOURCE =
            mapOf(
                "RESERVATION_CONFIRMED" to "INVENTORY_CLOSED",
                "RESERVATION_CANCELLED" to "INVENTORY_REOPENED",
                "RESERVATION_CHANGED" to "INVENTORY_CHANGED",
            )

        private val HOST_NOTIFY_EVENT_TYPES = setOf("RESERVATION_CHANGED")
    }
}
