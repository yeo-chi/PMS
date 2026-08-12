package yeo.chi.proejct.pms.operation.configuration

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * reservation 모듈이 예약 생성 사실을 operation 모듈에 위임할 때 사용하는 공개 API.
 *
 * reservation은 이 인터페이스에만 의존하며, operation의 실제 구현 클래스(service/domain/persistent 패키지)를
 * 직접 참조하지 않는다 (모듈 간 의존은 운영 모듈의 공개 API를 통해서만 - 이슈 #2/#3 공통 원칙).
 *
 * 실제 외부 플랫폼 연동, 재시도, 멱등성 보장은 이 인터페이스의 구현체가 담당하며, 그 실제 구현은 #6에서
 * 채워진다. 이번 티켓(#3)에는 시그니처와 로그만 남기는 임시 구현체만 포함된다.
 */
interface PlatformSyncFacade {

    fun syncReservationCreated(request: ReservationSyncRequest)
}

data class ReservationSyncRequest(
    val platformCode: String,
    val roomId: Long,
    val reservationCode: String,
    val userIdentifyCode: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reservedAt: LocalDateTime,
    val status: String,
)
