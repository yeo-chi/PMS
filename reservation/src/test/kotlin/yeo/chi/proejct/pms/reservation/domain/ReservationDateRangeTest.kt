package yeo.chi.proejct.pms.reservation.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ReservationDateRangeTest : FeatureSpec({
    feature("ReservationDateRange 생성") {
        scenario("startDate가 endDate보다 이전이면 정상 생성된다") {
            val dateRange = ReservationDateRange(startDate = LocalDate.of(2027, 1, 1), endDate = LocalDate.of(2027, 1, 2))

            dateRange.startDate shouldBe LocalDate.of(2027, 1, 1)
            dateRange.endDate shouldBe LocalDate.of(2027, 1, 2)
        }

        scenario("startDate와 endDate가 같으면(0박) 예외가 발생한다") {
            val date = LocalDate.of(2027, 1, 1)

            shouldThrow<IllegalArgumentException> {
                ReservationDateRange(startDate = date, endDate = date)
            }
        }

        scenario("startDate가 endDate보다 늦으면(역전) 예외가 발생한다") {
            shouldThrow<IllegalArgumentException> {
                ReservationDateRange(startDate = LocalDate.of(2027, 1, 5), endDate = LocalDate.of(2027, 1, 1))
            }
        }
    }
})
