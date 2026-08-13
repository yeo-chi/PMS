package yeo.chi.proejct.pms.reservation.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.ReservationRequestRepository
import yeo.chi.proejct.pms.reservation.persistent.toEntity
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ReservationServiceTest(
    @Autowired private val reservationService: ReservationService,
    @Autowired private val reservationRepository: ReservationRepository,
    @Autowired private val reservationRequestRepository: ReservationRequestRepository,
    @Autowired private val outboundNotificationRepository: OutboundNotificationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val entityManager: EntityManager,
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
            roomCode = roomCode,
            dateRange = ReservationDateRange(startDate, endDate),
            initiatedBy = initiatedBy,
        )

    feature("BOOK 처리 성공") {
        scenario("신규 BOOK 요청은 예약을 확정하고 알림을 정확히 1건 남긴다") {
            val command = bookCommand("REF-BOOK-1", "ROOM-BOOK-1", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 5))

            val result = reservationService.book(command)

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            result.rejectReason.shouldBeNull()
            val reservationId = requireNotNull(result.reservationId) { "SUCCESS 결과는 reservationId를 가져야 합니다" }

            val savedReservation = reservationRepository.findById(reservationId).orElseThrow()
            savedReservation.status shouldBe ReservationStatus.CONFIRMED

            val notifications = outboundNotificationRepository.findAll().filter { it.reservationId == reservationId }
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
            secondResult.reservationId shouldBe firstResult.reservationId
            reservationRepository.findAll().count { it.roomCode == "ROOM-BOOK-2" } shouldBe 1
            reservationRequestRepository.findAll().count { it.requestKey == firstResult.requestKey } shouldBe 1
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
    }

    feature("동시 겹침 요청") {
        scenario("같은 room_code에 겹치는 날짜로 동시에 요청하면 정확히 하나만 성공한다") {
            val roomCode = "ROOM-BOOK-CONCURRENT"
            val startDate = LocalDate.of(2027, 4, 1)
            val endDate = LocalDate.of(2027, 4, 10)
            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)

            val outcomes: List<ReservationRequest>
            try {
                val futures =
                    listOf("REF-CONCURRENT-A", "REF-CONCURRENT-B").map { platformReservationRef ->
                        executor.submit<ReservationRequest> {
                            readyLatch.countDown()
                            startLatch.await()
                            reservationService.book(bookCommand(platformReservationRef, roomCode, startDate, endDate))
                        }
                    }
                readyLatch.await()
                startLatch.countDown()
                outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                executor.shutdown()
            }

            val successCount = outcomes.count { it.resultStatus == RequestResultStatus.SUCCESS }
            val conflictCount = outcomes.count { it.resultStatus == RequestResultStatus.CONFLICT }

            successCount shouldBe 1
            conflictCount shouldBe 1
            outcomes.first { it.resultStatus == RequestResultStatus.CONFLICT }.rejectReason shouldBe "DUPLICATE_BOOKING"
            outcomes.first { it.resultStatus == RequestResultStatus.CONFLICT }.reservationId.shouldBeNull()

            val successReservationId = outcomes.first { it.resultStatus == RequestResultStatus.SUCCESS }.reservationId
            outboundNotificationRepository.findAll().count { it.reservationId == successReservationId } shouldBe 1
        }
    }

    feature("CANCEL_REQUEST 처리") {
        scenario("CONFIRMED 예약에 대한 취소요청은 PENDING_CANCEL로 전이하고 알림을 정확히 1건 남긴다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-1", "ROOM-CANCEL-REQ-1", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 5)),
                )
            val reservationId = requireNotNull(bookResult.reservationId)

            val result =
                reservationService.cancelRequest(
                    CancelRequestCommand(reservationId, CancelRequestReason.OVERBOOKING_CLEANUP),
                )

            result.resultStatus shouldBe RequestResultStatus.SUCCESS
            result.rejectReason shouldBe "OVERBOOKING_CLEANUP"
            reservationRepository.findById(reservationId).orElseThrow().status shouldBe ReservationStatus.PENDING_CANCEL
            outboundNotificationRepository.findAll().count {
                it.reservationId == reservationId && it.eventType == "CANCEL_REQUESTED"
            } shouldBe 1
        }

        scenario("PENDING_CANCEL 상태에서도 같은 room_code·겹치는 날짜의 신규 BOOK은 계속 거부된다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-2", "ROOM-CANCEL-REQ-2", LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 10)),
                )
            val reservationId = requireNotNull(bookResult.reservationId)
            reservationService.cancelRequest(CancelRequestCommand(reservationId, CancelRequestReason.FACILITY_ISSUE))

            val overlappingBookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-2-OVERLAP", "ROOM-CANCEL-REQ-2", LocalDate.of(2028, 2, 5), LocalDate.of(2028, 2, 15)),
                )

            overlappingBookResult.resultStatus shouldBe RequestResultStatus.CONFLICT
        }

        scenario("이미 PENDING_CANCEL인 예약에 재요청하면 재전이 없이 감사 기록만 추가된다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-3", "ROOM-CANCEL-REQ-3", LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 5)),
                )
            val reservationId = requireNotNull(bookResult.reservationId)
            reservationService.cancelRequest(CancelRequestCommand(reservationId, CancelRequestReason.OTHER))
            val versionAfterFirstRequest = reservationRepository.findById(reservationId).orElseThrow().version

            val secondResult =
                reservationService.cancelRequest(CancelRequestCommand(reservationId, CancelRequestReason.OTHER))

            secondResult.resultStatus shouldBe RequestResultStatus.SUCCESS
            val reservationAfterSecondRequest = reservationRepository.findById(reservationId).orElseThrow()
            reservationAfterSecondRequest.status shouldBe ReservationStatus.PENDING_CANCEL
            reservationAfterSecondRequest.version shouldBe versionAfterFirstRequest
            outboundNotificationRepository.findAll().count {
                it.reservationId == reservationId && it.eventType == "CANCEL_REQUESTED"
            } shouldBe 1
            reservationRequestRepository.findAll().count { it.reservationId == reservationId } shouldBe 2
        }

        scenario("이미 CANCELLED인 예약에 취소요청하면 FAILED로 기록되고 알림을 남기지 않는다") {
            val now = OffsetDateTime.now()
            val cancelledReservation =
                reservationRepository.saveAndFlush(
                    Reservation(
                        id = null,
                        reservationNo = null,
                        platformId = "OTA_BOOKING",
                        platformReservationRef = "REF-CANCEL-REQ-4",
                        roomCode = "ROOM-CANCEL-REQ-4",
                        dateRange = ReservationDateRange(LocalDate.of(2028, 4, 1), LocalDate.of(2028, 4, 5)),
                        status = ReservationStatus.CANCELLED,
                        version = 1,
                        createdAt = now,
                        updatedAt = now,
                    ).toEntity(),
                )

            val result =
                reservationService.cancelRequest(
                    CancelRequestCommand(cancelledReservation.id, CancelRequestReason.OVERBOOKING_CLEANUP),
                )

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "ALREADY_CANCELLED"
            outboundNotificationRepository.findAll().count { it.reservationId == cancelledReservation.id } shouldBe 0
        }

        scenario("존재하지 않는 예약에 대한 취소요청은 FAILED로 기록되고 알림을 남기지 않는다") {
            val notificationCountBefore = outboundNotificationRepository.findAll().size

            val result =
                reservationService.cancelRequest(CancelRequestCommand(-1L, CancelRequestReason.OTHER))

            result.resultStatus shouldBe RequestResultStatus.FAILED
            result.rejectReason shouldBe "RESERVATION_NOT_FOUND"
            result.reservationId.shouldBeNull()
            outboundNotificationRepository.findAll() shouldHaveSize notificationCountBefore
        }

        scenario("낙관적 락 충돌이 나도 재시도해 결국 PENDING_CANCEL 전이에 성공한다") {
            val bookResult =
                reservationService.book(
                    bookCommand("REF-CANCEL-REQ-5", "ROOM-CANCEL-REQ-5", LocalDate.of(2028, 5, 1), LocalDate.of(2028, 5, 5)),
                )
            val reservationId = requireNotNull(bookResult.reservationId)

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
            reservationRepository.findById(reservationId).orElseThrow().status shouldBe ReservationStatus.PENDING_CANCEL
        }
    }
})
