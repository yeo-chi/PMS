package yeo.chi.proejct.pms.reservation.service

import io.kotest.core.spec.style.FeatureSpec
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalDateTime

class RestPlatformSyncClientTest : FeatureSpec({

    feature("RestPlatformSyncClient.syncReservationCreated") {
        scenario("operation 서버로 POST /api/platform-syncs 요청을 보낸다") {
            val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val restPlatformSyncClient = RestPlatformSyncClient(restClientBuilder.build())

            mockServer.expect(requestTo("http://localhost:8082/api/platform-syncs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                    """
                    {
                      "platformCode": "AIRBNB",
                      "roomId": 1,
                      "reservationCode": "AIRBNB-CODE-0001",
                      "userIdentifyCode": "guest-001",
                      "status": "CONFIRMED"
                    }
                    """.trimIndent(),
                    false,
                ))
                .andRespond(withSuccess())

            restPlatformSyncClient.syncReservationCreated(
                PlatformSyncRequest(
                    platformCode = "AIRBNB",
                    roomId = 1L,
                    reservationCode = "AIRBNB-CODE-0001",
                    userIdentifyCode = "guest-001",
                    startDate = LocalDate.of(2026, 9, 1),
                    endDate = LocalDate.of(2026, 9, 3),
                    reservedAt = LocalDateTime.of(2026, 8, 13, 10, 0),
                    status = "CONFIRMED",
                ),
            )

            mockServer.verify()
        }
    }
})
