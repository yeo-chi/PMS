package yeo.chi.proejct.pms.operation.persistent

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

// Docker/Testcontainers 없이 로컬에 이미 떠 있는 MySQL(operation_test DB, docs/schema/
// operation_server_schema.sql이 미리 적용되어 있어야 함)에 직접 붙는다. reservation의
// PostgresIntegrationTest와 동일한 원칙.
abstract class MySqlIntegrationTest(body: FeatureSpec.() -> Unit = {}) : FeatureSpec(body) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                System.getenv("OPERATION_TEST_DB_URL")
                    ?: "jdbc:mysql://localhost:3306/operation_test?allowPublicKeyRetrieval=true&useSSL=false"
            }
            registry.add("spring.datasource.username") { System.getenv("OPERATION_TEST_DB_USER") ?: "operation" }
            registry.add("spring.datasource.password") { System.getenv("OPERATION_TEST_DB_PASSWORD") ?: "operation" }
        }
    }
}
