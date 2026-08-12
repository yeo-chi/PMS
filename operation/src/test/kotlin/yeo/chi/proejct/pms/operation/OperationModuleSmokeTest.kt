package yeo.chi.proejct.pms.operation

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class OperationModuleSmokeTest : FeatureSpec({
    feature("operation module test source set") {
        scenario("JUnit5 + Kotest is wired correctly") {
            (1 + 1) shouldBe 2
        }
    }
})
