package yeo.chi.proejct.pms.reservation

import org.springframework.boot.test.context.SpringBootTest
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest

@SpringBootTest
class ReservationApplicationTests : PostgresIntegrationTest({
    feature("Spring 애플리케이션 컨텍스트 로딩") {
        scenario("Flyway 마이그레이션을 포함해 컨텍스트가 정상적으로 로드된다") {
        }
    }
})
