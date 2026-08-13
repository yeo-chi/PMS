package yeo.chi.proejct.pms.reservation

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class ReservationModuleSmokeTest : FeatureSpec({
    feature("reservation module test source set") {
        scenario("JUnit5 + Kotest is wired correctly") {
            (1 + 1) shouldBe 2
        }
    }
})
