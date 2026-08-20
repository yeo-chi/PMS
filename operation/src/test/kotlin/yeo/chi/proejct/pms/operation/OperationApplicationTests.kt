package yeo.chi.proejct.pms.operation

import org.springframework.boot.test.context.SpringBootTest
import yeo.chi.proejct.pms.operation.persistent.MySqlIntegrationTest

@SpringBootTest
class OperationApplicationTests : MySqlIntegrationTest({
    feature("Spring 애플리케이션 컨텍스트 로딩") {
        scenario("로컬 MySQL(operation_test)에 이미 적용된 스키마 위에서 컨텍스트가 정상적으로 로드된다") {
        }
    }
})
