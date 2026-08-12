package yeo.chi.proejct.pms.reservation.service

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class RestPlatformSyncClient(
    private val operationRestClient: RestClient,
) : PlatformSyncClient {

    companion object {
        private val logger = LoggerFactory.getLogger(RestPlatformSyncClient::class.java)
        private const val PLATFORM_SYNC_PATH = "/api/platform-syncs"
    }

    override fun syncReservationCreated(request: PlatformSyncRequest) {
        logger.info(
            "Calling operation platform-sync API: platformCode={}, reservationCode={}",
            request.platformCode,
            request.reservationCode,
        )
        operationRestClient.post()
            .uri(PLATFORM_SYNC_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
