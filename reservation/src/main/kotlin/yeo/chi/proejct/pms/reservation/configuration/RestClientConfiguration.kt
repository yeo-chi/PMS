package yeo.chi.proejct.pms.reservation.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(OperationClientProperties::class)
class RestClientConfiguration(
    private val operationClientProperties: OperationClientProperties,
) {

    @Bean
    fun operationRestClient(restClientBuilder: RestClient.Builder): RestClient =
        restClientBuilder
            .baseUrl(operationClientProperties.baseUrl)
            .build()
}
