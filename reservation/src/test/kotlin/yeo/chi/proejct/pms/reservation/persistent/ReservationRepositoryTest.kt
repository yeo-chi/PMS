package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

@DataJpaTest
class ReservationRepositoryTest @Autowired constructor(
    private val reservationRepository: ReservationRepository,
) {

    private fun newReservation(
        platformCode: String = "AIRBNB",
        code: String = "AIRBNB-CODE-0001",
    ) = Reservation(
        platformCode = platformCode,
        roomId = 1L,
        userIdentifyCode = "guest-001",
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2026, 9, 3),
        reservedAt = LocalDateTime.of(2026, 8, 13, 10, 0),
        code = code,
    )

    @Test
    fun `저장 후 id로 조회하면 저장한 값이 그대로 조회된다`() {
        val saved = reservationRepository.save(newReservation())

        saved.id.shouldNotBeNull()
        val found = reservationRepository.findById(saved.id!!).orElseThrow()
        found.platformCode shouldBe "AIRBNB"
        found.code shouldBe "AIRBNB-CODE-0001"
        found.status shouldBe ReservationStatus.CONFIRMED
    }

    @Test
    fun `동일 platformCode + code 조합을 다시 저장하면 유니크 제약 위반으로 실패한다`() {
        reservationRepository.saveAndFlush(newReservation())

        assertFailsWith<DataIntegrityViolationException> {
            reservationRepository.saveAndFlush(newReservation())
        }
    }

    @Test
    fun `platformCode가 다르면 동일한 code 값이어도 함께 저장할 수 있다`() {
        reservationRepository.saveAndFlush(newReservation(platformCode = "AIRBNB"))
        reservationRepository.saveAndFlush(newReservation(platformCode = "BOOKING_COM"))

        reservationRepository.findAll() shouldHaveSize 2
    }

    @Test
    fun `findByPlatformCodeAndCode로 멱등성 키 기준 조회가 가능하다`() {
        reservationRepository.save(newReservation())

        reservationRepository.findByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001").shouldNotBeNull()
        reservationRepository.findByPlatformCodeAndCode("AIRBNB", "NO-SUCH-CODE") shouldBe null
    }

    @Test
    fun `existsByPlatformCodeAndCode로 멱등성 키 존재 여부를 확인할 수 있다`() {
        reservationRepository.save(newReservation())

        reservationRepository.existsByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001") shouldBe true
        reservationRepository.existsByPlatformCodeAndCode("AIRBNB", "OTHER-CODE") shouldBe false
    }
}
