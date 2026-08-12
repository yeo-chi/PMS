package yeo.chi.proejct.pms.operation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OperationModuleSmokeTest : StringSpec({
    "operation module test source set (JUnit5 + Kotest) is wired correctly" {
        (1 + 1) shouldBe 2
    }
})
