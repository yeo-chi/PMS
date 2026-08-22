package yeo.chi.proejct.pms.reservation.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.ChangeReservationCommand
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.repository.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationLogRepository
import java.time.LocalDate
import java.time.OffsetDateTime

@SpringBootTest
class ReservationChangeServiceTest(
    @Autowired private val reservationService: ReservationService,
    @Autowired private val reservationRepository: ReservationRepository,
    @Autowired private val reservationLogRepository: ReservationLogRepository,
    @Autowired private val outboundNotificationRepository: OutboundNotificationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val entityManager: EntityManager,
) : PostgresIntegrationTest({

    fun bookCommand(
        platformReservationRef: String,
        roomCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Reservation =
        Reservation(
            platformId = "OTA_BOOKING",
            platformReservationRef = platformReservationRef,
            roomId = roomCode,
            dateRange = ReservationDateRange(startDate, endDate),
            status = ReservationStatus.CONFIRMED,
            initiatedBy = RequestInitiator.OTA,
        )

    fun changeCommand(
        platformReservationRef: String,
        startDate: LocalDate,
        endDate: LocalDate,
        externalRequestId: String? = null,
        initiatedBy: RequestInitiator = RequestInitiator.OTA,
    ): ChangeReservationCommand =
        ChangeReservationCommand(
            platformId = "OTA_BOOKING",
            platformReservationRef = platformReservationRef,
            newDateRange = ReservationDateRange(startDate, endDate),
            initiatedBy = initiatedBy,
            externalRequestId = externalRequestId,
        )

    feature("CHANGE 처리 성공") {
        scenario("겹치지 않는 새 날짜로 변경하면 date_range가 갱신되고 알림을 정확히 1건 남긴다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-1", "ROOM-CHANGE-1", LocalDate.of(2028, 6, 1), LocalDate.of(2028, 6, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-1", LocalDate.of(2028, 7, 1), LocalDate.of(2028, 7, 5)),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            val savedReservation = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            savedReservation.dateRange.lower() shouldBe LocalDate.of(2028, 7, 1)
            savedReservation.dateRange.upper() shouldBe LocalDate.of(2028, 7, 5)
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_CHANGED"
            } shouldBe 1
        }

        scenario("자기 자신의 기존 구간과 겹치는 새 날짜로 변경해도 성공한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-2", "ROOM-CHANGE-2", LocalDate.of(2028, 8, 1), LocalDate.of(2028, 8, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)

            // 하루 연장: 새 구간이 기존 구간과 겹치지만, UPDATE 대상 row 자기 자신이므로 exclusion 위반이 아니다.
            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-2", LocalDate.of(2028, 8, 1), LocalDate.of(2028, 8, 6)),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            val savedReservation = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            savedReservation.dateRange.upper() shouldBe LocalDate.of(2028, 8, 6)
        }

        scenario("같은 예약에 대해 두 번째로 성공한 CHANGE도 서로 다른 알림을 남긴다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-3", "ROOM-CHANGE-3", LocalDate.of(2028, 9, 1), LocalDate.of(2028, 9, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            // externalRequestId 없이 같은 초 안에 두 번 호출하면 request_key가 초 단위로 같아져 두 번째
            // 호출이 (서로 다른 변경 시도임에도) 첫 번째의 요청 단위 멱등성에 걸려버린다. 이 테스트는
            // "진짜로 서로 다른 두 번의 성공한 CHANGE"를 검증하려는 것이므로 externalRequestId로 구분한다.
            reservationService.change(
                changeCommand("REF-CHANGE-3", LocalDate.of(2028, 9, 10), LocalDate.of(2028, 9, 15), externalRequestId = "EXT-CHANGE-3-A"),
            )

            val secondResult =
                reservationService.change(
                    changeCommand(
                        "REF-CHANGE-3",
                        LocalDate.of(2028, 9, 20),
                        LocalDate.of(2028, 9, 25),
                        externalRequestId = "EXT-CHANGE-3-B",
                    ),
                )

            secondResult.resultStatus shouldBe RequestResultStatus.SUCCESS
            val changedNotifications =
                outboundNotificationRepository.findAll().filter {
                    it.reservationCode == reservationCode && it.eventType == "RESERVATION_CHANGED"
                }
            changedNotifications shouldHaveSize 2
            changedNotifications.map { it.notificationKey }.toSet() shouldHaveSize 2
        }

        scenario("같은 externalRequestId로 재요청하면 재처리 없이 이전 결과를 그대로 반환한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-4", "ROOM-CHANGE-4", LocalDate.of(2028, 10, 1), LocalDate.of(2028, 10, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val command =
                changeCommand("REF-CHANGE-4", LocalDate.of(2028, 11, 1), LocalDate.of(2028, 11, 5), externalRequestId = "EXT-CHANGE-4")

            val firstResult = reservationService.change(command)
            val secondResult = reservationService.change(command)

            secondResult.id shouldBe firstResult.id
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_CHANGED"
            } shouldBe 1
            reservationLogRepository.findAll().count { it.requestKey == firstResult.requestKey } shouldBe 1
        }

        scenario("낙관적 락 충돌이 나도 재시도해 결국 성공한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-5", "ROOM-CHANGE-5", LocalDate.of(2028, 12, 1), LocalDate.of(2028, 12, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val reservationId = checkNotNull(reservationRepository.findByReservationCode(reservationCode)).id

            transactionTemplate.execute {
                entityManager
                    .createNativeQuery("UPDATE reservations SET version = version + 1 WHERE id = :id")
                    .setParameter("id", reservationId)
                    .executeUpdate()
            }

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-5", LocalDate.of(2029, 1, 1), LocalDate.of(2029, 1, 5)),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
        }
    }

    feature("CHANGE 처리 거부") {
        scenario("다른 예약과 겹치는 날짜로 변경하면 거부되고 원본 예약의 날짜는 그대로 유지된다") {
            reservationService.book(
                bookCommand("REF-CHANGE-6-OTHER", "ROOM-CHANGE-6", LocalDate.of(2029, 2, 10), LocalDate.of(2029, 2, 20)),
            )
            val targetBookResult =
                reservationService.book(
                    bookCommand("REF-CHANGE-6", "ROOM-CHANGE-6", LocalDate.of(2029, 2, 1), LocalDate.of(2029, 2, 5)),
                )
            val reservationCode = requireNotNull(targetBookResult.reservationCode)

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-6", LocalDate.of(2029, 2, 12), LocalDate.of(2029, 2, 18)),
                )

            result.resultStatus shouldBe RequestResultStatus.CONFLICT
            result.rejectReason shouldBe "DUPLICATE_BOOKING"
            val reservationAfterRejection = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            reservationAfterRejection.dateRange.lower() shouldBe LocalDate.of(2029, 2, 1)
            reservationAfterRejection.dateRange.upper() shouldBe LocalDate.of(2029, 2, 5)
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_REJECTED"
            } shouldBe 1
        }

        scenario("PENDING_CANCEL 상태의 예약을 변경하려 하면 FAILED로 기록되고 알림을 남기지 않는다") {
            val now = OffsetDateTime.now()
            val pendingReservation =
                reservationRepository.saveAndFlush(
                    ReservationEntity.of(
                        Reservation(
                            reservationCode = "",
                            platformId = "OTA_BOOKING",
                            platformReservationRef = "REF-CHANGE-7",
                            roomId = "ROOM-CHANGE-7",
                            dateRange = ReservationDateRange(LocalDate.of(2029, 3, 1), LocalDate.of(2029, 3, 5)),
                            status = ReservationStatus.PENDING_CANCEL,
                            initiatedBy = RequestInitiator.OTA,
                        ),
                        now,
                    ),
                )

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-7", LocalDate.of(2029, 4, 1), LocalDate.of(2029, 4, 5)),
                )

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_CHANGEABLE"
            outboundNotificationRepository.findAll().count { it.reservationCode == pendingReservation.reservationCode } shouldBe 0
        }

        scenario("CANCELLED 상태의 예약을 변경하려 하면 FAILED로 기록되고 알림을 남기지 않는다") {
            val now = OffsetDateTime.now()
            val cancelledReservation =
                reservationRepository.saveAndFlush(
                    ReservationEntity.of(
                        Reservation(
                            reservationCode = "",
                            platformId = "OTA_BOOKING",
                            platformReservationRef = "REF-CHANGE-8",
                            roomId = "ROOM-CHANGE-8",
                            dateRange = ReservationDateRange(LocalDate.of(2029, 5, 1), LocalDate.of(2029, 5, 5)),
                            status = ReservationStatus.CANCELLED,
                            initiatedBy = RequestInitiator.OTA,
                        ),
                        now,
                    ),
                )

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-8", LocalDate.of(2029, 6, 1), LocalDate.of(2029, 6, 5)),
                )

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_CHANGEABLE"
            outboundNotificationRepository.findAll().count { it.reservationCode == cancelledReservation.reservationCode } shouldBe 0
        }

        scenario("존재하지 않는 예약에 대한 변경 요청은 FAILED로 기록되고 알림을 남기지 않는다") {
            val notificationCountBefore = outboundNotificationRepository.findAll().size

            val result =
                reservationService.change(
                    changeCommand("REF-CHANGE-NOT-FOUND", LocalDate.of(2029, 7, 1), LocalDate.of(2029, 7, 5)),
                )

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_FOUND"
            result.reservationCode.shouldBeNull()
            outboundNotificationRepository.findAll() shouldHaveSize notificationCountBefore
        }

        scenario("HOST가 시작한 CHANGE 요청은 거부된다") {
            // ChangeReservationCommand.init이 즉시 검증하므로(서비스 호출 전 생성 시점) 생성 자체를
            // shouldThrow로 감싸야 한다 — BOOK의 동일 시나리오와 같은 이유.
            shouldThrow<IllegalArgumentException> {
                changeCommand(
                    "REF-CHANGE-HOST",
                    LocalDate.of(2029, 8, 1),
                    LocalDate.of(2029, 8, 5),
                    initiatedBy = RequestInitiator.HOST,
                )
            }
        }

        scenario("startDate가 endDate보다 이전이 아닌 CHANGE 요청은 거부된다") {
            shouldThrow<IllegalArgumentException> {
                changeCommand("REF-CHANGE-INVERTED", LocalDate.of(2029, 9, 5), LocalDate.of(2029, 9, 1))
            }
        }
    }
})
