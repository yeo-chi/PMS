package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboundNotificationRepositoryTest(
    private val reservationRepository: ReservationRepository,
    private val outboundNotificationRepository: OutboundNotificationRepository,
) : PostgresIntegrationTest({

    fun savedReservationId(): Long {
        val now = OffsetDateTime.now()
        val entity =
            Reservation(
                id = null,
                reservationNo = null,
                platformId = "OTA_BOOKING",
                platformReservationRef = "REF-NOTIFY",
                roomCode = "ROOM-501",
                dateRange = ReservationDateRange(startDate = LocalDate.of(2026, 10, 1), endDate = LocalDate.of(2026, 10, 5)),
                status = ReservationStatus.CONFIRMED,
                version = 1,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
        return reservationRepository.saveAndFlush(entity).id
    }

    fun newNotification(
        notificationKey: String,
        reservationId: Long,
    ): OutboundNotification {
        val now = OffsetDateTime.now()
        return OutboundNotification(
            id = null,
            notificationKey = notificationKey,
            reservationId = reservationId,
            requestId = null,
            eventType = "RESERVATION_CONFIRMED",
            payload = """{"reservationNo":"OTA_BOOKING:REF-NOTIFY"}""",
            status = OutboundNotificationStatus.PENDING,
            retryCount = 0,
            nextRetryAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    feature("OutboundNotification 저장/조회") {
        scenario("기본 CRUD: 저장 후 notification_key로 조회할 수 있다") {
            val reservationId = savedReservationId()
            outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-1", reservationId).toEntity())

            val found = outboundNotificationRepository.findByNotificationKey("NOTIFY-1")

            found?.eventType shouldBe "RESERVATION_CONFIRMED"
        }

        scenario("동일한 notification_key로 재삽입하면 저장이 거부된다") {
            val reservationId = savedReservationId()
            outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-DUP", reservationId).toEntity())

            shouldThrow<DataIntegrityViolationException> {
                outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-DUP", reservationId).toEntity())
            }
        }
    }
})
