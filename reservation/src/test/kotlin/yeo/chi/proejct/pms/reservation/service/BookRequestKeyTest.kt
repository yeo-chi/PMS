package yeo.chi.proejct.pms.reservation.service

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate

class BookRequestKeyTest : FeatureSpec({
    fun reservation(
        platformId: String,
        platformReservationRef: String,
        roomId: String,
        dateRange: ReservationDateRange,
    ) = Reservation(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        roomId = roomId,
        dateRange = dateRange,
        status = ReservationStatus.CONFIRMED,
        initiatedBy = RequestInitiator.OTA,
    )

    feature("Reservation.logKey") {
        scenario("platformId:platformReservationRef:roomId:startDate:endDate:BOOK 형태로 멱등키를 생성한다") {
            val requestKey =
                reservation(
                    platformId = "OTA_BOOKING",
                    platformReservationRef = "REF-1",
                    roomId = "ROOM-101",
                    dateRange =
                        ReservationDateRange(
                            startDate = LocalDate.of(2026, 1, 1),
                            endDate = LocalDate.of(2026, 1, 5),
                        ),
                ).logKey()

            requestKey shouldBe "OTA_BOOKING:REF-1:ROOM-101:2026-01-01:2026-01-05:BOOK"
        }

        scenario("같은 입력이면 항상 같은 키를 생성한다") {
            val dateRange = ReservationDateRange(startDate = LocalDate.of(2026, 2, 1), endDate = LocalDate.of(2026, 2, 3))

            val first = reservation("OTA_AGODA", "REF-2", "ROOM-202", dateRange).logKey()
            val second = reservation("OTA_AGODA", "REF-2", "ROOM-202", dateRange).logKey()

            first shouldBe second
        }

        scenario("같은 room_id·날짜라도 platformReservationRef가 다르면 서로 다른 키를 생성한다") {
            val dateRange = ReservationDateRange(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 3, 3))

            val first = reservation("OTA_AGODA", "REF-A", "ROOM-303", dateRange).logKey()
            val second = reservation("OTA_AGODA", "REF-B", "ROOM-303", dateRange).logKey()

            first shouldNotBe second
        }
    }
})
