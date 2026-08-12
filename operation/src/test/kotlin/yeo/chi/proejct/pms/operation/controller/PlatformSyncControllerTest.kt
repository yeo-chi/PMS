package yeo.chi.proejct.pms.operation.controller

import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import yeo.chi.proejct.pms.operation.configuration.PlatformSyncFacade

/**
 * Kotest `FeatureSpec` + `@WebMvcTest`/`SpringExtension` 생성자 주입 조합을 시도했으나, 이 저장소에서는
 * 컴파일 타임 오류("Annotation argument must be a compile-time constant" — `@Import`가 참조하는
 * `@TestConfiguration` 클래스가 테스트 클래스 자신(또는 그 companion object) 내부에 중첩되어 있으면 Kotlin이
 * 자기 자신을 애노테이션 인자로 해석하지 못함)로 실패하는 것을 확인했다. 이는 `FeatureSpec` 여부와 무관하게
 * "테스트 클래스 내부에 중첩된 `@TestConfiguration`을 그 클래스 자신의 애노테이션에서 참조"하는 패턴 자체의
 * 문제였으므로, `MockFacadeConfig`를 톱레벨 클래스로 분리했다. plan/12.md §10-3의 명시적 경고에 따라, 기존
 * `ReservationApplicationTests`/`OperationApplicationTests`와 동일하게 순수 JUnit5 `@Test` 클래스 +
 * `@WebMvcTest` + MockK로 작성한다.
 */
@WebMvcTest(PlatformSyncController::class)
@Import(PlatformSyncControllerTestConfig::class)
class PlatformSyncControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var platformSyncFacade: PlatformSyncFacade

    @BeforeEach
    fun resetMock() {
        clearMocks(platformSyncFacade)
    }

    @Test
    fun `정상 요청이면 202 Accepted를 반환하고 PlatformSyncFacade를 호출한다`() {
        every { platformSyncFacade.syncReservationCreated(any()) } just Runs

        mockMvc.post("/api/platform-syncs") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "platformCode": "AIRBNB",
                  "roomId": 1,
                  "reservationCode": "AIRBNB-CODE-0001",
                  "userIdentifyCode": "guest-001",
                  "startDate": "2026-09-01",
                  "endDate": "2026-09-03",
                  "reservedAt": "2026-08-13T10:00:00",
                  "status": "CONFIRMED"
                }
            """.trimIndent()
        }.andExpect {
            status { isAccepted() }
        }

        verify(exactly = 1) { platformSyncFacade.syncReservationCreated(any()) }
    }
}

@TestConfiguration
class PlatformSyncControllerTestConfig {
    @Bean
    fun platformSyncFacade(): PlatformSyncFacade = mockk(relaxed = true)
}
