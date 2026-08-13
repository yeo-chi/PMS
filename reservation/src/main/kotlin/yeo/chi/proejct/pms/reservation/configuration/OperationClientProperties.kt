package yeo.chi.proejct.pms.reservation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "operation")
data class OperationClientProperties(
    val baseUrl: String = "http://localhost:8082",
)
