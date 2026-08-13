package yeo.chi.proejct.pms.reservation.service

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import yeo.chi.proejct.pms.reservation.configuration.OutboundNotificationDispatchProperties
import yeo.chi.proejct.pms.reservation.domain.OutboundNotification
import yeo.chi.proejct.pms.reservation.domain.OutboundNotificationStatus
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.persistent.OutboundNotificationRepository
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.toEntity
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OutboundNotificationDispatchWorkerTest(
    @Autowired private val outboundNotificationRepository: OutboundNotificationRepository,
    @Autowired private val reservationRepository: ReservationRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val properties: OutboundNotificationDispatchProperties,
) : PostgresIntegrationTest({

    fun savedReservationId(platformReservationRef: String, roomCode: String): Long {
        val now = OffsetDateTime.now()
        return reservationRepository
            .saveAndFlush(
                Reservation
                    .createNew(
                        platformId = "OTA_BOOKING",
                        platformReservationRef = platformReservationRef,
                        roomCode = roomCode,
                        dateRange = ReservationDateRange(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 5)),
                        createdAt = now,
                    ).toEntity(),
            ).id
    }

    fun savedNotificationId(
        reservationId: Long,
        notificationKey: String,
        retryCount: Int = 0,
        nextRetryAt: OffsetDateTime = OffsetDateTime.now().minusSeconds(1),
    ): Long {
        val now = OffsetDateTime.now()
        return outboundNotificationRepository
            .saveAndFlush(
                OutboundNotification(
                    id = null,
                    notificationKey = notificationKey,
                    reservationId = reservationId,
                    requestId = null,
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"reservationNo":"OTA_BOOKING:REF"}""",
                    status = OutboundNotificationStatus.PENDING,
                    retryCount = retryCount,
                    nextRetryAt = nextRetryAt,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            ).id
    }

    // MockRestServiceServer의 기대(expectation)는 1회성이라, 시나리오마다 새 워커+새 mock 서버를 만든다.
    fun newWorkerWithMockServer(): Pair<OutboundNotificationDispatchWorker, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl("http://mock-operation")
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val worker =
            OutboundNotificationDispatchWorker(
                outboundNotificationRepository,
                reservationRepository,
                transactionTemplate,
                builder.build(),
                properties,
            )
        return worker to mockServer
    }

    feature("정상 발송") {
        scenario("PENDING 알림은 발송에 성공하면 SENT로 전이하고 payload를 원본 JSON 그대로 싣는다") {
            val reservationId = savedReservationId("REF-DISPATCH-1", "ROOM-DISPATCH-1")
            val notificationId = savedNotificationId(reservationId, "NOTIFY-DISPATCH-1")
            val (worker, mockServer) = newWorkerWithMockServer()

            mockServer
                .expect(requestTo("http://mock-operation/api/inbound-events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.notificationKey").value("NOTIFY-DISPATCH-1"))
                .andExpect(jsonPath("$.eventType").value("RESERVATION_CONFIRMED"))
                .andExpect(jsonPath("$.payload.reservationNo").value("OTA_BOOKING:REF"))
                .andRespond(withSuccess())

            worker.dispatchPendingNotifications()

            mockServer.verify()
            outboundNotificationRepository.findById(notificationId).orElseThrow().status shouldBe OutboundNotificationStatus.SENT
        }
    }

    feature("발송 실패 시 재시도 갱신") {
        scenario("5xx 응답이면 retry_count가 증가하고 status=FAILED, next_retry_at이 미래로 갱신된다") {
            val reservationId = savedReservationId("REF-DISPATCH-500", "ROOM-DISPATCH-500")
            val notificationId = savedNotificationId(reservationId, "NOTIFY-DISPATCH-500")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-operation/api/inbound-events")).andRespond(withServerError())

            worker.dispatchPendingNotifications()

            val updated = outboundNotificationRepository.findById(notificationId).orElseThrow()
            updated.status shouldBe OutboundNotificationStatus.FAILED
            updated.retryCount shouldBe 1
            (updated.nextRetryAt.isAfter(OffsetDateTime.now())) shouldBe true
        }

        scenario("4xx 응답이면 retry_count가 증가하고 status=FAILED, next_retry_at이 미래로 갱신된다") {
            val reservationId = savedReservationId("REF-DISPATCH-400", "ROOM-DISPATCH-400")
            val notificationId = savedNotificationId(reservationId, "NOTIFY-DISPATCH-400")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-operation/api/inbound-events")).andRespond(withBadRequest())

            worker.dispatchPendingNotifications()

            val updated = outboundNotificationRepository.findById(notificationId).orElseThrow()
            updated.status shouldBe OutboundNotificationStatus.FAILED
            updated.retryCount shouldBe 1
            (updated.nextRetryAt.isAfter(OffsetDateTime.now())) shouldBe true
        }

        scenario("커넥션 실패(타임아웃 등)여도 retry_count가 증가하고 status=FAILED로 갱신된다") {
            val reservationId = savedReservationId("REF-DISPATCH-IO", "ROOM-DISPATCH-IO")
            val notificationId = savedNotificationId(reservationId, "NOTIFY-DISPATCH-IO")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-operation/api/inbound-events")).andRespond(withException(IOException("connection reset")))

            worker.dispatchPendingNotifications()

            val updated = outboundNotificationRepository.findById(notificationId).orElseThrow()
            updated.status shouldBe OutboundNotificationStatus.FAILED
            updated.retryCount shouldBe 1
        }

        scenario("maxRetryCount에 도달하면 DEAD로 전이하고 이후 폴링에서 제외된다") {
            val reservationId = savedReservationId("REF-DISPATCH-DEAD", "ROOM-DISPATCH-DEAD")
            val notificationId =
                savedNotificationId(reservationId, "NOTIFY-DISPATCH-DEAD", retryCount = properties.maxRetryCount - 1)
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-operation/api/inbound-events")).andRespond(withServerError())

            worker.dispatchPendingNotifications()

            val updated = outboundNotificationRepository.findById(notificationId).orElseThrow()
            updated.status shouldBe OutboundNotificationStatus.DEAD
            updated.retryCount shouldBe properties.maxRetryCount

            val (rePollWorker, _) = newWorkerWithMockServer()
            rePollWorker.dispatchPendingNotifications()
            outboundNotificationRepository.findById(notificationId).orElseThrow().status shouldBe OutboundNotificationStatus.DEAD
        }
    }

    feature("폴링 대상 선정") {
        scenario("next_retry_at이 미래인 FAILED 건은 이번 폴링에서 제외된다") {
            val reservationId = savedReservationId("REF-DISPATCH-FUTURE", "ROOM-DISPATCH-FUTURE")
            val notificationId =
                savedNotificationId(
                    reservationId,
                    "NOTIFY-DISPATCH-FUTURE",
                    nextRetryAt = OffsetDateTime.now().plusHours(1),
                )

            val batch = outboundNotificationRepository.findBatchForDispatch(OffsetDateTime.now(), properties.batchSize)

            batch.map { it.id } shouldNotContain notificationId
        }
    }

    feature("동시 워커 폴링 (SKIP LOCKED)") {
        scenario("두 워커가 동시에 폴링해도 같은 row를 중복으로 집어가지 않는다") {
            val reservationId = savedReservationId("REF-DISPATCH-CONCURRENT", "ROOM-DISPATCH-CONCURRENT")
            (1..10).forEach { savedNotificationId(reservationId, "NOTIFY-DISPATCH-CONCURRENT-$it") }

            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)
            // 두 트랜잭션이 각자 SKIP LOCKED로 조회한 뒤에도 잠시 커밋을 미뤄, 두 락이 동시에 걸려 있는
            // 구간을 강제로 만든다 (그래야 SKIP LOCKED가 실제로 겹침을 막는지 검증할 수 있다).
            val holdLatch = CountDownLatch(2)
            val executor = Executors.newFixedThreadPool(2)

            val results: List<List<Long>>
            try {
                val futures =
                    (1..2).map {
                        executor.submit<List<Long>> {
                            readyLatch.countDown()
                            startLatch.await()
                            checkNotNull(
                                transactionTemplate.execute {
                                    val batch = outboundNotificationRepository.findBatchForDispatch(OffsetDateTime.now(), 5)
                                    holdLatch.countDown()
                                    holdLatch.await(5, TimeUnit.SECONDS)
                                    batch.map { notification -> notification.id }
                                },
                            )
                        }
                    }
                readyLatch.await()
                startLatch.countDown()
                results = futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                executor.shutdown()
            }

            val (firstBatch, secondBatch) = results
            firstBatch.shouldNotBeEmpty()
            secondBatch.shouldNotBeEmpty()
            (firstBatch intersect secondBatch.toSet()).shouldBeEmpty()
        }
    }
})
