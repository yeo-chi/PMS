package yeo.chi.proejct.pms.reservation.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.BookReservationCommand
import yeo.chi.proejct.pms.reservation.domain.CancelConfirmCommand
import yeo.chi.proejct.pms.reservation.domain.CancelRequestCommand
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationLog
import yeo.chi.proejct.pms.reservation.domain.ReservationLogAction
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity
import yeo.chi.proejct.pms.reservation.persistent.repository.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationLogRepository
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ReservationServiceTest(
    @Autowired private val reservationService: ReservationService,
    @Autowired private val reservationRepository: ReservationRepository,
    @Autowired private val reservationLogRepository: ReservationLogRepository,
    @Autowired private val outboundNotificationRepository: OutboundNotificationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val objectMapper: ObjectMapper,
) : PostgresIntegrationTest({

    fun bookCommand(
        platformReservationRef: String,
        roomCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        initiatedBy: RequestInitiator = RequestInitiator.OTA,
    ): BookReservationCommand =
        BookReservationCommand(
            platformId = "OTA_BOOKING",
            platformReservationRef = platformReservationRef,
            roomId = roomCode,
            dateRange = ReservationDateRange(startDate, endDate),
            initiatedBy = initiatedBy,
        )

    fun cancelConfirmCommand(
        platformReservationRef: String,
        externalRequestId: String,
    ): CancelConfirmCommand =
        CancelConfirmCommand(
            platformId = "OTA_BOOKING",
            platformReservationRef = platformReservationRef,
            externalRequestId = externalRequestId,
        )

    // CancelRequestCommand/네이티브 UPDATE 쿼리는 내부 PK(Long)가 필요하고, ReservationLog/알림/로그
    // 비교는 논리 키인 reservationCode(String)를 쓴다 — book()이 돌려주는 ReservationLog에는
    // reservationCode만 있으므로, 내부 PK가 필요한 곳에서는 이걸로 다시 조회한다.
    fun idOf(reservationCode: String): Long = checkNotNull(reservationRepository.findByReservationCode(reservationCode)).id

    feature("BOOK 처리 성공") {
        scenario("신규 BOOK 요청은 예약을 확정하고 알림을 정확히 1건 남긴다") {
            val command = bookCommand("REF-BOOK-1", "ROOM-BOOK-1", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5))

            val result = reservationService.book(command)

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            result.rejectReason.shouldBeNull()
            val reservationCode = requireNotNull(result.reservationCode) { "SUCCESS 결과는 reservationCode를 가져야 합니다" }

            val savedReservation = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            savedReservation.status shouldBe ReservationStatus.CONFIRMED

            val notifications = outboundNotificationRepository.findAll().filter { it.reservationCode == reservationCode }
            notifications shouldHaveSize 1
            notifications.first().eventType shouldBe "RESERVATION_CONFIRMED"
            notifications.first().status shouldBe OutboundNotificationStatus.PENDING
        }

        scenario("동일한 platformId+roomCode+날짜로 재요청하면 재처리 없이 이전 결과를 그대로 반환한다") {
            val command = bookCommand("REF-BOOK-2", "ROOM-BOOK-2", LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 5))

            val firstResult = reservationService.book(command)
            val secondResult = reservationService.book(command)

            // TIMESTAMPTZ 왕복 시 offset이 UTC로 정규화되어 requestedAt은 인스턴트는 같아도 offset이 달라질 수 있으므로,
            // "같은 row가 그대로 반환됐는지"를 나타내는 필드만 비교한다.
            secondResult.id shouldBe firstResult.id
            secondResult.resultStatus shouldBe firstResult.resultStatus
            secondResult.reservationCode shouldBe firstResult.reservationCode
            reservationRepository.findAll().count { it.roomCode == "ROOM-BOOK-2" } shouldBe 1
            reservationLogRepository.findAll().count { it.requestKey == firstResult.requestKey } shouldBe 1
        }
    }

    feature("BOOK 처리 실패") {
        scenario("HOST가 시작한 BOOK 요청은 거부된다") {
            val command =
                bookCommand(
                    "REF-BOOK-HOST",
                    "ROOM-BOOK-HOST",
                    LocalDate.of(2027, 3, 1),
                    LocalDate.of(2027, 3, 5),
                    initiatedBy = RequestInitiator.HOST,
                )

            shouldThrow<IllegalArgumentException> { reservationService.book(command) }
        }

        scenario("startDate가 endDate보다 이전이 아닌 BOOK 요청은 거부된다") {
            shouldThrow<IllegalArgumentException> {
                bookCommand("REF-BOOK-INVERTED", "ROOM-BOOK-INVERTED", LocalDate.of(2027, 3, 5), LocalDate.of(2027, 3, 1))
            }
        }
    }

    feature("동시 겹침 요청") {
        scenario("같은 room_code에 겹치는 날짜로 동시에 요청하면 정확히 하나만 성공한다") {
            val roomCode = "ROOM-BOOK-CONCURRENT"
            val startDate = LocalDate.of(2027, 4, 1)
            val endDate = LocalDate.of(2027, 4, 10)
            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            // 어느 쪽이 이기고 지는지 미리 알 수 없으므로, 각 결과를 그 결과를 만든 platformReservationRef와
            // 짝지어 둔다(패자 채널의 RESERVATION_REJECTED payload가 그 ref로 계산된 reservationNo를
            // 담고 있는지 검증하려면 필요).
            val outcomes: List<Pair<String, ReservationLog>>
            try {
                val futures =
                    listOf("REF-CONCURRENT-A", "REF-CONCURRENT-B").map { platformReservationRef ->
                        executor.submit<Pair<String, ReservationLog>> {
                            readyLatch.countDown()
                            startLatch.await()
                            platformReservationRef to
                                reservationService.book(bookCommand(platformReservationRef, roomCode, startDate, endDate))
                        }
                    }
                readyLatch.await()
                startLatch.countDown()
                outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                executor.shutdown()
            }

            val successCount = outcomes.count { it.second.resultStatus == RequestResultStatus.SUCCESS }
            val conflictCount = outcomes.count { it.second.resultStatus == RequestResultStatus.CONFLICT }

            successCount shouldBe 1
            conflictCount shouldBe 1
            val (conflictRef, conflictOutcome) = outcomes.first { it.second.resultStatus == RequestResultStatus.CONFLICT }
            conflictOutcome.rejectReason shouldBe "DUPLICATE_BOOKING"
            conflictOutcome.reservationCode.shouldBeNull()

            val successReservationCode = outcomes.first { it.second.resultStatus == RequestResultStatus.SUCCESS }.second.reservationCode
            outboundNotificationRepository.findAll().count { it.reservationCode == successReservationCode } shouldBe 1

            // 패자 채널에도 RESERVATION_REJECTED 통보가 정확히 1건 남아야 한다(#32) — 예약 row가 없어
            // reservation_code는 null이고, reservationNo는 payload 안에서 읽는다.
            val rejectedNotifications = outboundNotificationRepository.findAll().filter { it.requestKey == conflictOutcome.requestKey }
            rejectedNotifications shouldHaveSize 1
            val rejectedNotification = rejectedNotifications.first()
            rejectedNotification.eventType shouldBe "RESERVATION_REJECTED"
            rejectedNotification.reservationCode.shouldBeNull()
            objectMapper.readTree(rejectedNotification.payload).get("reservationNo").asText() shouldBe "OTA_BOOKING:$conflictRef"
        }
    }

    feature("CANCEL_CONFIRM 처리") {
        scenario("CONFIRMED 예약에 대한 취소통보는 CANCELLED로 전이하고 알림을 정확히 1건 남긴다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-1", "ROOM-CANCEL-1", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)

            val result = reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-1", "EXT-CANCEL-1"))

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            result.reservationCode shouldBe reservationCode
            checkNotNull(reservationRepository.findByReservationCode(reservationCode)).status shouldBe ReservationStatus.CANCELLED
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_CANCELLED"
            } shouldBe 1
        }

        scenario("PENDING_CANCEL 예약에 대한 취소통보도 CANCELLED로 전이한다") {
            val now = OffsetDateTime.now()
            val pendingReservation =
                reservationRepository.saveAndFlush(
                    ReservationEntity.from(
                        Reservation(
                            reservationCode = "",
                            platformId = "OTA_BOOKING",
                            platformReservationRef = "REF-CANCEL-2",
                            roomId = "ROOM-CANCEL-2",
                            dateRange = ReservationDateRange(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 5)),
                            status = ReservationStatus.PENDING_CANCEL,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                )

            val result = reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-2", "EXT-CANCEL-2"))

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            reservationRepository.findById(pendingReservation.id).orElseThrow().status shouldBe ReservationStatus.CANCELLED
            outboundNotificationRepository.findAll().count { it.reservationCode == pendingReservation.reservationCode } shouldBe 1
        }

        scenario("이미 CANCELLED인 예약에 재통보하면 재전이·재알림 없이 SUCCESS 감사 기록만 추가된다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-3", "ROOM-CANCEL-3", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-3", "EXT-CANCEL-3-FIRST"))
            val versionAfterFirstCancel = checkNotNull(reservationRepository.findByReservationCode(reservationCode)).version

            val secondResult = reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-3", "EXT-CANCEL-3-SECOND"))

            secondResult.resultStatus shouldBe RequestResultStatus.SUCCESS
            val reservationAfterSecondCancel = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            reservationAfterSecondCancel.status shouldBe ReservationStatus.CANCELLED
            reservationAfterSecondCancel.version shouldBe versionAfterFirstCancel
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_CANCELLED"
            } shouldBe 1
            reservationLogRepository.findAll().count {
                it.reservationCode == reservationCode && it.action == ReservationLogAction.CANCEL_CONFIRM
            } shouldBe 2
        }

        scenario("존재하지 않는 예약에 대한 취소통보는 FAILED로 기록되고 알림을 남기지 않는다") {
            val notificationCountBefore = outboundNotificationRepository.findAll().size

            val result = reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-NOT-FOUND", "EXT-CANCEL-NOT-FOUND"))

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_FOUND"
            result.reservationCode.shouldBeNull()
            outboundNotificationRepository.findAll() shouldHaveSize notificationCountBefore
        }

        scenario("같은 externalRequestId로 재요청하면 재처리 없이 이전 결과를 그대로 반환한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-4", "ROOM-CANCEL-4", LocalDate.of(2027, 8, 1), LocalDate.of(2027, 8, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val command = cancelConfirmCommand("REF-CANCEL-4", "EXT-CANCEL-4")

            val firstResult = reservationService.cancelConfirm(command)
            val secondResult = reservationService.cancelConfirm(command)

            secondResult.id shouldBe firstResult.id
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "RESERVATION_CANCELLED"
            } shouldBe 1
            reservationLogRepository.findAll().count { it.requestKey == firstResult.requestKey } shouldBe 1
        }

        scenario("낙관적 락 충돌이 나도 재시도해 결국 성공한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-5", "ROOM-CANCEL-5", LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val reservationId = idOf(reservationCode)

            // 다른 트랜잭션이 먼저 커밋해 version을 올려놓은 상황을 시뮬레이션해, 첫 시도가 반드시
            // 낙관적 락 충돌을 겪게 만든다. 이 테스트 자체는 트랜잭션으로 감싸여 있지 않으므로
            // (@SpringBootTest, @DataJpaTest 아님) 별도 트랜잭션에서 커밋해야 재시도 루프의 새 조회에 보인다.
            transactionTemplate.execute {
                entityManager
                    .createNativeQuery("UPDATE reservations SET version = version + 1 WHERE id = :id")
                    .setParameter("id", reservationId)
                    .executeUpdate()
            }

            val result = reservationService.cancelConfirm(cancelConfirmCommand("REF-CANCEL-5", "EXT-CANCEL-5"))

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            checkNotNull(reservationRepository.findByReservationCode(reservationCode)).status shouldBe ReservationStatus.CANCELLED
        }
    }

    feature("CANCEL_REQUEST 처리") {
        scenario("CONFIRMED 예약에 대한 취소요청은 PENDING_CANCEL로 전이하고 알림을 정확히 1건 남긴다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-1", "ROOM-CANCEL-REQ-1", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val reservationId = idOf(reservationCode)

            val result =
                reservationService.cancelRequest(
                    CancelRequestCommand(reservationId, CancelRequestReason.OVERBOOKING_CLEANUP),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            result.rejectReason shouldBe "OVERBOOKING_CLEANUP"
            checkNotNull(reservationRepository.findByReservationCode(reservationCode)).status shouldBe ReservationStatus.PENDING_CANCEL
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "CANCEL_REQUESTED"
            } shouldBe 1
        }

        scenario("PENDING_CANCEL 상태에서도 같은 room_code·겹치는 날짜의 신규 BOOK은 계속 거부된다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-2", "ROOM-CANCEL-REQ-2", LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 10)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            reservationService.cancelRequest(CancelRequestCommand(idOf(reservationCode), CancelRequestReason.FACILITY_ISSUE))

            val overlappingBookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-2-OVERLAP", "ROOM-CANCEL-REQ-2", LocalDate.of(2028, 2, 5), LocalDate.of(2028, 2, 15)),
                )

            overlappingBookResult.resultStatus shouldBe RequestResultStatus.CONFLICT
        }

        scenario("이미 PENDING_CANCEL인 예약에 (다른 externalRequestId로) 재요청하면 재전이 없이 감사 기록만 추가된다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-3", "ROOM-CANCEL-REQ-3", LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val reservationId = idOf(reservationCode)
            reservationService.cancelRequest(
                CancelRequestCommand(
                    reservationId,
                    CancelRequestReason.OTHER,
                    externalRequestId = "EXT-CANCEL-REQ-3-A"
                ),
            )
            val versionAfterFirstRequest = checkNotNull(reservationRepository.findByReservationCode(reservationCode)).version

            val secondResult =
                reservationService.cancelRequest(
                    CancelRequestCommand(
                        reservationId,
                        CancelRequestReason.OTHER,
                        externalRequestId = "EXT-CANCEL-REQ-3-B"
                    ),
                )

            secondResult.resultStatus shouldBe RequestResultStatus.SUCCESS
            val reservationAfterSecondRequest = checkNotNull(reservationRepository.findByReservationCode(reservationCode))
            reservationAfterSecondRequest.status shouldBe ReservationStatus.PENDING_CANCEL
            reservationAfterSecondRequest.version shouldBe versionAfterFirstRequest
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "CANCEL_REQUESTED"
            } shouldBe 1
            // BOOK 1건 + 서로 다른 externalRequestId를 가진 CANCEL_REQUEST 2건 = 총 3건.
            reservationLogRepository.findAll().count { it.reservationCode == reservationCode } shouldBe 3
        }

        scenario("같은 externalRequestId로 재요청하면 재처리 없이 이전 결과를 그대로 반환한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-IDEMPOTENT", "ROOM-CANCEL-REQ-IDEMPOTENT", LocalDate.of(2028, 6, 1), LocalDate.of(2028, 6, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val command = CancelRequestCommand(
                idOf(reservationCode),
                CancelRequestReason.OTHER,
                externalRequestId = "EXT-CANCEL-REQ-IDEMPOTENT"
            )

            val firstResult = reservationService.cancelRequest(command)
            val secondResult = reservationService.cancelRequest(command)

            secondResult.id shouldBe firstResult.id
            outboundNotificationRepository.findAll().count {
                it.reservationCode == reservationCode && it.eventType == "CANCEL_REQUESTED"
            } shouldBe 1
            reservationLogRepository.findAll().count { it.requestKey == firstResult.requestKey } shouldBe 1
        }

        scenario("이미 CANCELLED인 예약에 취소요청하면 FAILED로 기록되고 알림을 남기지 않는다") {
            val now = OffsetDateTime.now()
            val cancelledReservation =
                reservationRepository.saveAndFlush(
                    ReservationEntity.from(
                        Reservation(
                            reservationCode = "",
                            platformId = "OTA_BOOKING",
                            platformReservationRef = "REF-CANCEL-REQ-4",
                            roomId = "ROOM-CANCEL-REQ-4",
                            dateRange = ReservationDateRange(LocalDate.of(2028, 4, 1), LocalDate.of(2028, 4, 5)),
                            status = ReservationStatus.CANCELLED,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                )

            val result =
                reservationService.cancelRequest(
                    CancelRequestCommand(cancelledReservation.id, CancelRequestReason.OVERBOOKING_CLEANUP),
                )

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "ALREADY_CANCELLED"
            outboundNotificationRepository.findAll().count { it.reservationCode == cancelledReservation.reservationCode } shouldBe 0
        }

        scenario("존재하지 않는 예약에 대한 취소요청은 FAILED로 기록되고 알림을 남기지 않는다") {
            val notificationCountBefore = outboundNotificationRepository.findAll().size

            val result =
                reservationService.cancelRequest(CancelRequestCommand(-1L, CancelRequestReason.OTHER))

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_FOUND"
            result.reservationCode.shouldBeNull()
            outboundNotificationRepository.findAll() shouldHaveSize notificationCountBefore
        }

        scenario("낙관적 락 충돌이 나도 재시도해 결국 PENDING_CANCEL 전이에 성공한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-5", "ROOM-CANCEL-REQ-5", LocalDate.of(2028, 5, 1), LocalDate.of(2028, 5, 5)),
                )
            val reservationCode = requireNotNull(bookResult.reservationCode)
            val reservationId = idOf(reservationCode)

            // 다른 트랜잭션이 먼저 커밋해 version을 올려놓은 상황을 시뮬레이션해, 첫 시도가 반드시 낙관적 락
            // 충돌을 겪게 만든다. 이 테스트는 트랜잭션으로 감싸여 있지 않으므로(@DataJpaTest 아님) 별도
            // 트랜잭션에서 커밋해야 재시도 루프의 새 조회에 보인다.
            transactionTemplate.execute {
                entityManager
                    .createNativeQuery("UPDATE reservations SET version = version + 1 WHERE id = :id")
                    .setParameter("id", reservationId)
                    .executeUpdate()
            }

            val result =
                reservationService.cancelRequest(
                    CancelRequestCommand(reservationId, CancelRequestReason.OVERBOOKING_CLEANUP),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            checkNotNull(reservationRepository.findByReservationCode(reservationCode)).status shouldBe ReservationStatus.PENDING_CANCEL
        }
    }
})
