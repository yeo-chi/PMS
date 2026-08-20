package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

// Docker/Testcontainers 없이 로컬에 이미 떠 있는 PostgreSQL(reservation_test DB, docs/schema/
// reservation_server_schema.sql이 미리 적용되어 있어야 함)에 직접 붙는다. 운영에서 DBA가 스키마를
// 미리 관리해두는 모델과 동일하게, 테스트도 스키마가 이미 존재하는 DB에 연결만 한다.
abstract class PostgresIntegrationTest(body: FeatureSpec.() -> Unit = {}) : FeatureSpec(body) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                System.getenv("RESERVATION_TEST_DB_URL") ?: "jdbc:postgresql://localhost:5432/reservation_test"
            }
            registry.add("spring.datasource.username") { System.getenv("RESERVATION_TEST_DB_USER") ?: "reservation" }
            registry.add("spring.datasource.password") { System.getenv("RESERVATION_TEST_DB_PASSWORD") ?: "reservation" }
        }
    }
}
