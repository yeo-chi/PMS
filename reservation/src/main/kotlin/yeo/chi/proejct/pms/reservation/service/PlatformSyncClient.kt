package yeo.chi.proejct.pms.reservation.service

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * reservation이 operation 서버에 예약 동기화를 위임할 때 사용하는 내부 추상화.
 *
 * reservation과 operation은 더 이상 Gradle 컴파일 의존이 없으므로(이슈 #12), 이 인터페이스는 operation의
 * PlatformSyncFacade와 이름/역할이 유사해 보이지만 완전히 별개의, reservation 모듈 소유 타입이다. 실제 구현체
 * [RestPlatformSyncClient]는 HTTP로 operation 서버를 호출한다. ReservationService는 이 인터페이스에만
 * 의존하므로, 서비스 단위 테스트에서는 이 인터페이스를 MockK로 mock할 수 있다.
 */
interface PlatformSyncClient {

    fun syncReservationCreated(request: PlatformSyncRequest)
}

data class PlatformSyncRequest(
    val platformCode: String,
    val roomId: Long,
    val reservationCode: String,
    val userIdentifyCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reservedAt: LocalDateTime,
    val status: String,
)
