package yeo.chi.proejct.pms.operation.persistent

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

abstract class MySqlIntegrationTest(body: FeatureSpec.() -> Unit = {}) : FeatureSpec(body) {

    companion object {
        private val mysqlContainer: MySQLContainer<*> =
            MySQLContainer("mysql:8.0")
                .withDatabaseName("operation")
                .withUsername("operation")
                .withPassword("operation")
                .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl)
            registry.add("spring.datasource.username", mysqlContainer::getUsername)
            registry.add("spring.datasource.password", mysqlContainer::getPassword)
        }
    }
}
