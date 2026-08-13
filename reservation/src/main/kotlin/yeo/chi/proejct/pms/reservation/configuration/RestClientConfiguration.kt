package yeo.chi.proejct.pms.reservation.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(OperationClientProperties::class)
class RestClientConfiguration(
    private val operationClientProperties: OperationClientProperties,
) {

    @Bean
    fun operationRestClient(restClientBuilder: RestClient.Builder): RestClient {
        // OutboundNotificationDispatchWorker는 이 클라이언트로 호출하는 동안 DB row lock(SKIP LOCKED)을
        // 들고 있으므로, 타임아웃이 없으면 응답 지연이 곧 락 무한 보유로 이어진다.
        val requestFactory =
            ClientHttpRequestFactoryBuilder
                .detect()
                .build(
                    ClientHttpRequestFactorySettings
                        .defaults()
                        .withConnectTimeout(Duration.ofSeconds(operationClientProperties.connectTimeoutSeconds))
                        .withReadTimeout(Duration.ofSeconds(operationClientProperties.readTimeoutSeconds)),
                )

        return restClientBuilder
            .baseUrl(operationClientProperties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
