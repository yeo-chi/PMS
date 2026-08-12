package yeo.chi.proejct.pms.reservation.service

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.toEntity
import java.time.LocalDate
import java.time.LocalDateTime

class ReservationServiceTest : FeatureSpec({

    fun newReservation(
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

    feature("ReservationService.createReservation") {
        scenario("새로운 예약이면 저장 후 PlatformSyncClient를 호출한다") {
            val reservationRepository = mockk<ReservationRepository>()
            val platformSyncClient = mockk<PlatformSyncClient>()
            val reservationService = ReservationService(reservationRepository, platformSyncClient)

            val reservation = newReservation()
            val savedEntity = reservation.toEntity().apply { id = 100L }

            every {
                reservationRepository.findByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001")
            } returns null
            every { reservationRepository.save(any()) } returns savedEntity
            every { platformSyncClient.syncReservationCreated(any()) } just Runs

            val result = reservationService.createReservation(reservation)

            result.id shouldBe 100L
            result.status shouldBe ReservationStatus.CONFIRMED

            val requestSlot = slot<PlatformSyncRequest>()
            verify(exactly = 1) { platformSyncClient.syncReservationCreated(capture(requestSlot)) }
            requestSlot.captured.platformCode shouldBe "AIRBNB"
            requestSlot.captured.reservationCode shouldBe "AIRBNB-CODE-0001"
            requestSlot.captured.roomId shouldBe 1L
            requestSlot.captured.status shouldBe "CONFIRMED"
            verify(exactly = 1) { reservationRepository.save(any()) }
        }

        scenario("이미 존재하는 예약(platformCode+code)이면 저장/동기화 없이 기존 예약을 그대로 반환한다") {
            val reservationRepository = mockk<ReservationRepository>()
            val platformSyncClient = mockk<PlatformSyncClient>()
            val reservationService = ReservationService(reservationRepository, platformSyncClient)

            val reservation = newReservation()
            val existingEntity = reservation.toEntity().apply { id = 55L }

            every {
                reservationRepository.findByPlatformCodeAndCode("AIRBNB", "AIRBNB-CODE-0001")
            } returns existingEntity

            val result = reservationService.createReservation(reservation)

            result.id shouldBe 55L
            verify(exactly = 0) { reservationRepository.save(any()) }
            verify(exactly = 0) { platformSyncClient.syncReservationCreated(any()) }
        }
    }
})
