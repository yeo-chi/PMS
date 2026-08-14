package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import yeo.chi.proejct.pms.operation.persistent.repository.OutboxEventRepository
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryTest(
    private val outboxEventRepository: OutboxEventRepository,
) : MySqlIntegrationTest({

    fun newOutboxEvent(outboxKey: String): OutboxEvent {
        val now = LocalDateTime.now()
        return OutboxEvent(
            id = null,
            outboxKey = outboxKey,
            targetType = OutboxTargetType.OTA_CHANNEL,
            targetCode = "OTA_BOOKING",
            reservationNo = "OTA_BOOKING:REF-1",
            eventType = "RESERVATION_CONFIRMED",
            payload = """{"roomCode":"ROOM-101"}""",
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = null,
            updatedAt = null,
        )
    }

    feature("OutboxEvent 저장/조회") {
        scenario("저장 후 outbox_key로 조회할 수 있다") {
            outboxEventRepository.saveAndFlush(newOutboxEvent("OUTBOX-1").toEntity())

            val found = outboxEventRepository.findByOutboxKey("OUTBOX-1")

            found?.eventType shouldBe "RESERVATION_CONFIRMED"
        }

        scenario("동일한 outbox_key로 재삽입하면 저장이 거부된다") {
            outboxEventRepository.saveAndFlush(newOutboxEvent("OUTBOX-DUP").toEntity())

            shouldThrow<DataIntegrityViolationException> {
                outboxEventRepository.saveAndFlush(newOutboxEvent("OUTBOX-DUP").toEntity())
            }
        }
    }
})
