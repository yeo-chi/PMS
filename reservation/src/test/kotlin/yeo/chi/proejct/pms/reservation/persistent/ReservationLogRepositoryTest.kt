package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.reservation.domain.ReservationLog
import yeo.chi.proejct.pms.reservation.domain.ReservationLogAction
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationLogEntity
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationLogRepository
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationLogRepositoryTest(
    private val reservationLogRepository: ReservationLogRepository,
) : PostgresIntegrationTest({

    fun newRequest(requestKey: String): ReservationLogEntity =
        ReservationLogEntity.from(
            ReservationLog(
                id = null,
                requestKey = requestKey,
                reservationCode = null,
                platformId = "OTA_BOOKING",
                action = ReservationLogAction.BOOK,
                initiatedBy = RequestInitiator.OTA,
                roomCode = "ROOM-101",
                oldDateRange = null,
                newDateRange = null,
                resultStatus = RequestResultStatus.SUCCESS,
                rejectReason = null,
                requestedAt = OffsetDateTime.now(),
            ),
        )

    feature("ReservationLog 저장/조회") {
        scenario("request_key로 저장된 요청을 조회할 수 있다") {
            reservationLogRepository.saveAndFlush(newRequest("OTA_BOOKING:ROOM-101:2026-01-01:2026-01-05:BOOK"))

            val found = reservationLogRepository.findByRequestKey("OTA_BOOKING:ROOM-101:2026-01-01:2026-01-05:BOOK")

            found?.platformId shouldBe "OTA_BOOKING"
        }

        scenario("동일한 request_key로 재삽입하면 저장이 거부된다") {
            reservationLogRepository.saveAndFlush(newRequest("DUP-KEY"))

            shouldThrow<DataIntegrityViolationException> {
                reservationLogRepository.saveAndFlush(newRequest("DUP-KEY"))
            }
        }
    }
})
