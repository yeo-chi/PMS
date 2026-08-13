package yeo.chi.proejct.pms.reservation.service

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.OffsetDateTime

class CancelConfirmRequestKeyTest : FeatureSpec({
    feature("buildCancelConfirmRequestKey") {
        val now = OffsetDateTime.parse("2027-01-01T00:00:30.123456+09:00")

        scenario("externalRequestId가 있으면 platformId:externalRequestId 형태로 생성한다") {
            val requestKey =
                buildCancelConfirmRequestKey(
                    platformId = "OTA_BOOKING",
                    platformReservationRef = "REF-1",
                    externalRequestId = "EXT-1",
                    now = now,
                )

            requestKey shouldBe "OTA_BOOKING:EXT-1"
        }

        scenario("externalRequestId가 없으면 platformId:platformReservationRef:CANCEL_CONFIRM:초단위시각 형태로 생성한다") {
            val requestKey =
                buildCancelConfirmRequestKey(
                    platformId = "OTA_BOOKING",
                    platformReservationRef = "REF-1",
                    externalRequestId = null,
                    now = now,
                )

            requestKey shouldBe "OTA_BOOKING:REF-1:CANCEL_CONFIRM:2027-01-01T00:00:30+09:00"
        }

        scenario("같은 입력이면 항상 같은 키를 생성한다") {
            val first = buildCancelConfirmRequestKey("OTA_AGODA", "REF-2", "EXT-2", now)
            val second = buildCancelConfirmRequestKey("OTA_AGODA", "REF-2", "EXT-2", now)

            first shouldBe second
        }

        scenario("externalRequestId가 다르면 다른 키를 생성한다") {
            val first = buildCancelConfirmRequestKey("OTA_AGODA", "REF-3", "EXT-A", now)
            val second = buildCancelConfirmRequestKey("OTA_AGODA", "REF-3", "EXT-B", now)

            first shouldNotBe second
        }
    }
})
