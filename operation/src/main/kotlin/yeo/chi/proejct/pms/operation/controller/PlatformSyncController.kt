package yeo.chi.proejct.pms.operation.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import yeo.chi.proejct.pms.operation.configuration.PlatformSyncFacade
import yeo.chi.proejct.pms.operation.configuration.ReservationSyncRequest
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * reservation 모듈이 예약 생성 사실을 HTTP로 전달하는 진입점.
 *
 * reservation과 operation은 이제 Gradle 컴파일 의존이 없는 별도 프로세스이므로, 이 컨트롤러가 받는
 * [PlatformSyncHttpRequest]는 operation.configuration.ReservationSyncRequest와 필드 구성이 동일하지만
 * 완전히 별개의 타입이다 (reservation 쪽의 PlatformSyncRequest와도 공유하지 않는다 - 이슈 #12 요구사항).
 * 이 컨트롤러는 HTTP 요청 바디를 받아 기존 [PlatformSyncFacade]로 그대로 위임한다 - 실제 동기화 로직
 * (외부 플랫폼 연동, 재시도)은 여전히 #6 범위이며 이번 티켓에서 변경하지 않는다.
 */
@RestController
@RequestMapping("/api/platform-syncs")
class PlatformSyncController(
    private val platformSyncFacade: PlatformSyncFacade,
) {

    @PostMapping
    fun syncReservationCreated(
        @RequestBody request: PlatformSyncHttpRequest,
    ): ResponseEntity<Void> {
        platformSyncFacade.syncReservationCreated(request.toFacadeRequest())
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}

data class PlatformSyncHttpRequest(
    val platformCode: String,
    val roomId: Long,
    val reservationCode: String,
    val userIdentifyCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reservedAt: LocalDateTime,
    val status: String,
) {
    fun toFacadeRequest(): ReservationSyncRequest = ReservationSyncRequest(
        platformCode = platformCode,
        roomId = roomId,
        reservationCode = reservationCode,
        userIdentifyCode = userIdentifyCode,
        startDate = startDate,
        endDate = endDate,
        reservedAt = reservedAt,
        status = status,
    )
}
