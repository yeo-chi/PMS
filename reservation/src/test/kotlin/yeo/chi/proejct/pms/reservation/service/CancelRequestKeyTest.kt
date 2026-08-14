package yeo.chi.proejct.pms.reservation.service

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import yeo.chi.proejct.pms.reservation.domain.CancelRequestCommand
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import java.time.OffsetDateTime

class CancelRequestKeyTest : FeatureSpec({
    feature("CancelRequestCommand.buildCancelRequestKey") {
        val now = OffsetDateTime.parse("2027-01-01T00:00:30.123456+09:00")

        fun command(
            reservationId: Long,
            externalRequestId: String?,
        ) = CancelRequestCommand(
            reservationId = reservationId,
            reason = CancelRequestReason.OTHER,
            externalRequestId = externalRequestId,
        )

        scenario("externalRequestId가 있으면 reservationId:CANCEL_REQUEST:externalRequestId 형태로 생성한다") {
            val requestKey = command(reservationId = 42L, externalRequestId = "EXT-1").buildCancelRequestKey(now)

            requestKey shouldBe "42:CANCEL_REQUEST:EXT-1"
        }

        scenario("externalRequestId가 없으면 reservationId:CANCEL_REQUEST:초단위시각 형태로 생성한다") {
            val requestKey = command(reservationId = 42L, externalRequestId = null).buildCancelRequestKey(now)

            requestKey shouldBe "42:CANCEL_REQUEST:2027-01-01T00:00:30+09:00"
        }

        scenario("같은 입력이면 항상 같은 키를 생성한다") {
            val first = command(7L, "EXT-2").buildCancelRequestKey(now)
            val second = command(7L, "EXT-2").buildCancelRequestKey(now)

            first shouldBe second
        }

        scenario("reservationId가 다르면 다른 키를 생성한다") {
            val first = command(1L, null).buildCancelRequestKey(now)
            val second = command(2L, null).buildCancelRequestKey(now)

            first shouldNotBe second
        }

        scenario("externalRequestId가 다르면 다른 키를 생성한다") {
            val first = command(7L, "EXT-A").buildCancelRequestKey(now)
            val second = command(7L, "EXT-B").buildCancelRequestKey(now)

            first shouldNotBe second
        }
    }
})
