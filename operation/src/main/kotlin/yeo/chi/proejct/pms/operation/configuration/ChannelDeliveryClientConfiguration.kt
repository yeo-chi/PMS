package yeo.chi.proejct.pms.operation.configuration

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

// reservation의 operationRestClient(RestClientConfiguration)와 달리 baseUrl을 고정하지 않는다 —
// ota_channels.callback_base_url이 row마다 다른 절대 URL이라, 호출 시 .uri(callbackBaseUrl)에
// 절대 URL을 그대로 넘긴다. 타임아웃만 공통으로 설정한다.
@Configuration
class ChannelDeliveryClientConfiguration {

    @Bean
    fun channelDeliveryRestClient(builder: RestClient.Builder): RestClient {
        val requestFactory =
            ClientHttpRequestFactoryBuilder
                .detect()
                .build(
                    ClientHttpRequestFactorySettings
                        .defaults()
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(5)),
                )

        return builder.requestFactory(requestFactory).build()
    }
}
