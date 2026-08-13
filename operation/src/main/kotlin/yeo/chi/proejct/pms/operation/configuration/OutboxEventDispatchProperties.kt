package yeo.chi.proejct.pms.operation.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "outbox-event")
data class OutboxEventDispatchProperties(
    val batchSize: Int = 20,
    val maxRetryCount: Int = 5,
    val initialBackoffSeconds: Long = 30,
    val maxBackoffSeconds: Long = 1800,
    val pollIntervalMillis: Long = 5000,
)
