package yeo.chi.proejct.pms.operation.service

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import yeo.chi.proejct.pms.operation.configuration.ReservationSyncRequest
import java.time.LocalDate
import java.time.LocalDateTime

class LoggingPlatformSyncFacadeTest : FeatureSpec({

    feature("LoggingPlatformSyncFacade") {
        scenario("syncReservationCreated 호출 시 예외 없이 완료된다 (임시 구현체, 실제 연동은 #6)") {
            val facade = LoggingPlatformSyncFacade()
            val request = ReservationSyncRequest(
                platformCode = "AIRBNB",
                roomId = 1L,
                reservationCode = "AIRBNB-CODE-0001",
                userIdentifyCode = "guest-001",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2026, 9, 3),
                reservedAt = LocalDateTime.of(2026, 8, 13, 10, 0),
                status = "CONFIRMED",
            )

            facade.shouldNotBeNull()
            facade.syncReservationCreated(request)
            // 예외 없이 반환되면 성공 - 이 구현체는 아직 아무 것도 검증할 상태를 갖지 않는다(no-op에 가까움).
        }
    }
})
