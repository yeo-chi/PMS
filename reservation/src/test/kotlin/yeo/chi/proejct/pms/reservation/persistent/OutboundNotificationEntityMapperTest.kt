package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.persistent.entity.OutboundNotificationEntity
import java.time.OffsetDateTime

class OutboundNotificationEntityMapperTest : FeatureSpec({
    fun notification(reservationCode: String?): OutboundNotification {
        val now = OffsetDateTime.now()
        return OutboundNotification(
            notificationKey = "NOTIFY-1",
            reservationCode = reservationCode,
            requestKey = "REQUEST-1",
            eventType = "RESERVATION_CONFIRMED",
            payload = """{"reservationNo":"OTA_BOOKING:REF-1"}""",
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
        )
    }

    feature("OutboundNotificationEntity ↔ OutboundNotification 매핑") {
        scenario("reservationCode가 있으면 toEntity 후 toDomain으로 왕복 변환해도 원본과 동일하다") {
            val original = notification(reservationCode = "OTA_BOOKING:REF-1")

            OutboundNotificationEntity.from(original).toDomain() shouldBe original
        }

        scenario("BOOK 겹침 거부처럼 reservationCode가 null이어도 toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val original = notification(reservationCode = null)

            OutboundNotificationEntity.from(original).toDomain() shouldBe original
        }
    }
})
