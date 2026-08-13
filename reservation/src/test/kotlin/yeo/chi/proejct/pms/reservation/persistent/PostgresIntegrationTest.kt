package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

abstract class PostgresIntegrationTest(body: FeatureSpec.() -> Unit = {}) : FeatureSpec(body) {

    companion object {
        private val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("reservation")
                .withUsername("reservation")
                .withPassword("reservation")
                .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
        }
    }
}
