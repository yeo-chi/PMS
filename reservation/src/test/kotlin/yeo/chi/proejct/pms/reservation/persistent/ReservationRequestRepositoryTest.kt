package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationRequestAction
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRequestRepositoryTest(
    private val reservationRequestRepository: ReservationRequestRepository,
) : PostgresIntegrationTest({

    fun newRequest(requestKey: String): ReservationRequest =
        ReservationRequest(
            id = null,
            requestKey = requestKey,
            reservationId = null,
            platformId = "OTA_BOOKING",
            action = ReservationRequestAction.BOOK,
            initiatedBy = RequestInitiator.OTA,
            roomCode = "ROOM-101",
            oldDateRange = null,
            newDateRange = null,
            resultStatus = RequestResultStatus.SUCCESS,
            rejectReason = null,
            requestedAt = OffsetDateTime.now(),
        )

    feature("ReservationRequest 저장/조회") {
        scenario("request_key로 저장된 요청을 조회할 수 있다") {
            reservationRequestRepository.saveAndFlush(newRequest("OTA_BOOKING:ROOM-101:2026-01-01:2026-01-05:BOOK").toEntity())

            val found = reservationRequestRepository.findByRequestKey("OTA_BOOKING:ROOM-101:2026-01-01:2026-01-05:BOOK")

            found?.platformId shouldBe "OTA_BOOKING"
        }

        scenario("동일한 request_key로 재삽입하면 저장이 거부된다") {
            reservationRequestRepository.saveAndFlush(newRequest("DUP-KEY").toEntity())

            shouldThrow<DataIntegrityViolationException> {
                reservationRequestRepository.saveAndFlush(newRequest("DUP-KEY").toEntity())
            }
        }
    }
})
