package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class OutboxEvent(
    val outboxKey: String,
    val targetType: OutboxTargetType,
    // ota_channels.platform_id 또는 hosts.host_id
    val targetCode: String,
    val reservationNo: String,
    val eventType: String,
    val payload: String,
    val status: OutboxEventStatus,
    val retryCount: Int,
    val nextRetryAt: LocalDateTime,
) {
    companion object {
        fun of(inboundEvent: InboundEvent, platformId: String) = OutboxEvent(
            outboxKey = "${inboundEvent.reservationNo}:${OutboxTargetType.OTA_CHANNEL}:$platformId:${inboundEvent.eventType}",
            targetType = OutboxTargetType.OTA_CHANNEL,
            targetCode = platformId,
            reservationNo = inboundEvent.reservationNo,
            eventType = inboundEvent.eventType,
            payload = inboundEvent.payload,
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = LocalDateTime.now(),
        )

        fun ofB(inboundEvent: InboundEvent, listing: RoomChannelListing, eventType: String) = OutboxEvent(
            outboxKey = "${inboundEvent.reservationNo}:${OutboxTargetType.OTA_CHANNEL}:${listing.platformId}:$eventType",
            targetType = OutboxTargetType.OTA_CHANNEL,
            targetCode = listing.platformId,
            reservationNo = inboundEvent.reservationNo,
            eventType = eventType,
            payload = inboundEvent.payload,
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = LocalDateTime.now(),
        )

        fun ofHost(inboundEvent: InboundEvent, hostId: String, eventType: String) = OutboxEvent(
            outboxKey = "${inboundEvent.reservationNo}:${OutboxTargetType.HOST}:$hostId:$eventType",
            targetType = OutboxTargetType.HOST,
            targetCode = hostId,
            reservationNo = inboundEvent.reservationNo,
            eventType = eventType,
            payload = inboundEvent.payload,
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = LocalDateTime.now(),
        )
    }
}

enum class OutboxTargetType {
    OTA_CHANNEL,
    HOST,
}

enum class OutboxEventStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD,
}
