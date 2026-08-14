package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.toReservationDateRange
import yeo.chi.proejct.pms.reservation.persistent.entity.toRange
import java.time.LocalDate
import java.time.OffsetDateTime

class ReservationEntityMapperTest : FeatureSpec({
    feature("ReservationEntity와 Reservation 도메인 간 매핑") {
        scenario("toEntity 후 toDomain으로 왕복 변환하면 원본과 동일하다") {
            val createdAt = OffsetDateTime.now()
            val reservation =
                Reservation(
                    reservationCode = "OTA_BOOKING:REF-1",
                    platformId = "OTA_BOOKING",
                    platformReservationRef = "REF-1",
                    roomId = "ROOM-101",
                    dateRange =
                        ReservationDateRange(
                            startDate = LocalDate.of(2026, 1, 1),
                            endDate = LocalDate.of(2026, 1, 5),
                        ),
                    status = ReservationStatus.CONFIRMED,
                    version = 1,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                )

            val roundTripped = ReservationEntity.from(reservation).toDomain()

            roundTripped shouldBe reservation
        }

        scenario("dateRange 경계값(시작일 inclusive, 종료일 exclusive)이 Range 변환 후에도 보존된다") {
            val dateRange =
                ReservationDateRange(
                    startDate = LocalDate.of(2026, 3, 10),
                    endDate = LocalDate.of(2026, 3, 12),
                )

            val range = dateRange.toRange()

            range.lower() shouldBe dateRange.startDate
            range.upper() shouldBe dateRange.endDate
            range.toReservationDateRange() shouldBe dateRange
        }
    }
})
