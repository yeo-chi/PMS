package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.InboundEvent

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InboundEventRepositoryTest(
    private val inboundEventRepository: InboundEventRepository,
) : MySqlIntegrationTest({

    fun newInboundEvent(notificationKey: String): InboundEvent =
        InboundEvent(
            id = null,
            notificationKey = notificationKey,
            reservationNo = "OTA_BOOKING:REF-1",
            eventType = "RESERVATION_CONFIRMED",
            payload = """{"roomCode":"ROOM-101"}""",
            receivedAt = null,
        )

    feature("InboundEvent 저장/조회") {
        scenario("저장 후 notification_key로 조회할 수 있다") {
            inboundEventRepository.saveAndFlush(newInboundEvent("NOTIFY-1").toEntity())

            val found = inboundEventRepository.findByNotificationKey("NOTIFY-1")

            found?.eventType shouldBe "RESERVATION_CONFIRMED"
        }

        scenario("동일한 notification_key로 재삽입하면 저장이 거부된다") {
            inboundEventRepository.saveAndFlush(newInboundEvent("NOTIFY-DUP").toEntity())

            shouldThrow<DataIntegrityViolationException> {
                inboundEventRepository.saveAndFlush(newInboundEvent("NOTIFY-DUP").toEntity())
            }
        }
    }
})
