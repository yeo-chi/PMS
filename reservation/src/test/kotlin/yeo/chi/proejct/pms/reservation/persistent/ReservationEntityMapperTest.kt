package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.LocalDateTime

class ReservationEntityMapperTest {

    @Test
    fun `Reservation을 toEntity 후 toDomain 하면 원본과 동일하다 (id, status 포함)`() {
        val reservation = Reservation(
            platformCode = "AIRBNB",
            roomId = 1L,
            userIdentifyCode = "guest-001",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 3),
            reservedAt = LocalDateTime.of(2026, 8, 13, 10, 0),
            code = "AIRBNB-CODE-0001",
            status = ReservationStatus.CANCELLED,
            id = 42L,
        )

        val roundTripped = reservation.toEntity().toDomain()

        roundTripped shouldBe reservation
    }
}
