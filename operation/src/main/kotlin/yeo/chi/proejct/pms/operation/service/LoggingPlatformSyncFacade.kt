package yeo.chi.proejct.pms.operation.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import yeo.chi.proejct.pms.operation.configuration.PlatformSyncFacade
import yeo.chi.proejct.pms.operation.configuration.ReservationSyncRequest

/**
 * [임시 구현체] 실제 외부 플랫폼 API 연동/재시도/멱등성 처리는 #6에서 채운다.
 * 현재는 예약 동기화 요청이 들어왔다는 사실을 로그로만 남기고 즉시 반환한다(no-op에 가까움).
 */
@Service
internal class LoggingPlatformSyncFacade : PlatformSyncFacade {

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingPlatformSyncFacade::class.java)
    }

    override fun syncReservationCreated(request: ReservationSyncRequest) {
        logger.info(
            "[TODO #6] platform sync triggered: platformCode={}, roomId={}, reservationCode={}, status={}",
            request.platformCode,
            request.roomId,
            request.reservationCode,
            request.status,
        )
    }
}
