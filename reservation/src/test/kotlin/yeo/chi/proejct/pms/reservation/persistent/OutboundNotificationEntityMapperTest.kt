package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import java.time.OffsetDateTime

class OutboundNotificationEntityMapperTest : FeatureSpec({
    fun notification(reservationId: Long?): OutboundNotification {
        val now = OffsetDateTime.now()
        return OutboundNotification(
            id = 1L,
            notificationKey = "NOTIFY-1",
            reservationId = reservationId,
            requestId = 1L,
            eventType = "RESERVATION_CONFIRMED",
            payload = """{"reservationNo":"OTA_BOOKING:REF-1"}""",
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    feature("OutboundNotificationEntity ↔ OutboundNotification 매핑") {
        scenario("reservationId가 있으면 toEntity 후 toDomain으로 왕복 변환해도 원본과 동일하다") {
            val original = notification(reservationId = 1L)

            original.toEntity().toDomain() shouldBe original
        }

        scenario("BOOK 겹침 거부처럼 reservationId가 null이어도 toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val original = notification(reservationId = null)

            original.toEntity().toDomain() shouldBe original
        }
    }
})
