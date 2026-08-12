package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import yeo.chi.proejct.pms.reservation.domain.Reservation
import java.time.LocalDate
import java.time.LocalDateTime

class ReservationRepositoryTest {

    private val reservationRepository = mockk<ReservationRepository>()

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
    fun `save 호출 시 id가 채번된 엔티티가 반환된다`() {
        val entityToSave = newReservation().toEntity()
        val savedEntity = newReservation().toEntity().apply { id = 100L }
        every { reservationRepository.save(entityToSave) } returns savedEntity

        val result = reservationRepository.save(entityToSave)

        result.id shouldBe 100L
        result.platformCode shouldBe "AIRBNB"
        verify(exactly = 1) { reservationRepository.save(entityToSave) }
    }

    @Test
    fun `findByPlatformCodeAndCode로 조회되면 도메인으로 변환 가능한 엔티티를 반환한다`() {
        val entity = newReservation().toEntity().apply { id = 1L }
        every { reservationRepository.findByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001") } returns entity

        val found = reservationRepository.findByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001")

        found.shouldNotBeNull()
        found.toDomain().code shouldBe "AIRBNB-CODE-0001"
    }

    @Test
    fun `findByPlatformCodeAndCode로 조회되지 않으면 null을 반환한다 (존재 여부는 null 체크로 판단)`() {
        every { reservationRepository.findByPlatformCodeAndCode("AIRBNB", "NO-SUCH-CODE") } returns null

        val found = reservationRepository.findByPlatformCodeAndCode("AIRBNB", "NO-SUCH-CODE")

        found.shouldBeNull()
    }
}
