package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationLog
import yeo.chi.proejct.pms.reservation.domain.ReservationLogAction
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.entity.OutboundNotificationEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationLogEntity
import yeo.chi.proejct.pms.reservation.persistent.repository.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationLogRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboundNotificationRepositoryTest(
    private val reservationRepository: ReservationRepository,
    private val reservationLogRepository: ReservationLogRepository,
    private val outboundNotificationRepository: OutboundNotificationRepository,
) : PostgresIntegrationTest({

    fun savedReservationCode(): String {
        val now = OffsetDateTime.now()
        val entity =
            ReservationEntity.from(
                Reservation(
                    reservationCode = "",
                    platformId = "OTA_BOOKING",
                    platformReservationRef = "REF-NOTIFY",
                    roomId = "ROOM-501",
                    dateRange = ReservationDateRange(startDate = LocalDate.of(2026, 10, 1), endDate = LocalDate.of(2026, 10, 5)),
                    status = ReservationStatus.CONFIRMED,
                    version = 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        return reservationRepository.saveAndFlush(entity).reservationCode
    }

    // outbound_notifications.request_key가 reservation_requests.request_key를 참조하는 FK라, notification을
    // 만들기 전에 먼저 그 대상이 될 request log row를 만들어둔다.
    fun savedRequestKey(reservationCode: String?): String {
        val requestKey = "REQ-${UUID.randomUUID()}"
        reservationLogRepository.saveAndFlush(
            ReservationLogEntity.from(
                ReservationLog(
                    id = null,
                    requestKey = requestKey,
                    reservationCode = reservationCode,
                    platformId = "OTA_BOOKING",
                    action = ReservationLogAction.BOOK,
                    initiatedBy = RequestInitiator.OTA,
                    roomCode = "ROOM-501",
                    oldDateRange = null,
                    newDateRange = null,
                    resultStatus = RequestResultStatus.SUCCESS,
                    rejectReason = null,
                    requestedAt = OffsetDateTime.now(),
                ),
            ),
        )
        return requestKey
    }

    fun newNotification(
        notificationKey: String,
        reservationCode: String?,
    ): OutboundNotificationEntity {
        val now = OffsetDateTime.now()
        return OutboundNotificationEntity.from(
            OutboundNotification(
                notificationKey = notificationKey,
                reservationCode = reservationCode,
                requestKey = savedRequestKey(reservationCode),
                eventType = "RESERVATION_CONFIRMED",
                payload = """{"reservationNo":"OTA_BOOKING:REF-NOTIFY"}""",
                status = OutboundNotificationStatus.PENDING,
                retryCount = 0,
                nextRetryAt = now,
            ),
        )
    }

    feature("OutboundNotification 저장/조회") {
        scenario("기본 CRUD: 저장 후 notification_key로 조회할 수 있다") {
            val reservationCode = savedReservationCode()
            outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-1", reservationCode))

            val found = outboundNotificationRepository.findByNotificationKey("NOTIFY-1")

            found?.eventType shouldBe "RESERVATION_CONFIRMED"
        }

        scenario("동일한 notification_key로 재삽입하면 저장이 거부된다") {
            val reservationCode = savedReservationCode()
            outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-DUP", reservationCode))

            shouldThrow<DataIntegrityViolationException> {
                outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-DUP", reservationCode))
            }
        }

        scenario("BOOK 겹침 거부처럼 reservation_code가 null이어도 저장할 수 있다") {
            val saved = outboundNotificationRepository.saveAndFlush(newNotification("NOTIFY-NO-RESERVATION", null))

            saved.reservationCode shouldBe null
        }
    }
})
