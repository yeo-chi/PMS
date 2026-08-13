package yeo.chi.proejct.pms.operation.service

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
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
import yeo.chi.proejct.pms.operation.configuration.OutboxEventDispatchProperties
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.domain.OutboxEvent
import yeo.chi.proejct.pms.operation.domain.OutboxEventStatus
import yeo.chi.proejct.pms.operation.domain.OutboxTargetType
import yeo.chi.proejct.pms.operation.persistent.HostRepository
import yeo.chi.proejct.pms.operation.persistent.MySqlIntegrationTest
import yeo.chi.proejct.pms.operation.persistent.OtaChannelRepository
import yeo.chi.proejct.pms.operation.persistent.OutboxEventRepository
import yeo.chi.proejct.pms.operation.persistent.toEntity
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OutboxEventDispatchWorkerTest(
    @Autowired private val outboxEventRepository: OutboxEventRepository,
    @Autowired private val otaChannelRepository: OtaChannelRepository,
    @Autowired private val hostRepository: HostRepository,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val properties: OutboxEventDispatchProperties,
) : MySqlIntegrationTest({

    fun savedOtaChannel(
        platformId: String,
        callbackBaseUrl: String?,
    ) {
        otaChannelRepository.saveAndFlush(
            OtaChannel(
                id = null,
                platformId = platformId,
                name = "Booking.com",
                integrationMode = OtaChannelIntegrationMode.ASYNC,
                callbackBaseUrl = callbackBaseUrl,
                apiKeyRef = null,
                status = OtaChannelStatus.ACTIVE,
                createdAt = null,
                updatedAt = null,
            ).toEntity(),
        )
    }

    fun savedHost(hostCode: String) {
        hostRepository.saveAndFlush(
            Host(
                id = null,
                hostCode = hostCode,
                name = "호스트 이름",
                contactEmail = null,
                contactPhone = null,
                status = HostStatus.ACTIVE,
                createdAt = null,
                updatedAt = null,
            ).toEntity(),
        )
    }

    fun savedOutboxEventId(
        outboxKey: String,
        targetType: OutboxTargetType,
        targetCode: String,
        retryCount: Int = 0,
        nextRetryAt: LocalDateTime = LocalDateTime.now().minusSeconds(1),
    ): Long =
        outboxEventRepository
            .saveAndFlush(
                OutboxEvent(
                    id = null,
                    outboxKey = outboxKey,
                    targetType = targetType,
                    targetCode = targetCode,
                    reservationNo = "OTA_BOOKING:REF-1",
                    eventType = "RESERVATION_CONFIRMED",
                    payload = """{"roomCode":"ROOM-101"}""",
                    status = OutboxEventStatus.PENDING,
                    retryCount = retryCount,
                    nextRetryAt = nextRetryAt,
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            ).id

    // MockRestServiceServer의 기대(expectation)는 1회성이라, 시나리오마다 새 워커+새 mock 서버를 만든다.
    fun newWorkerWithMockServer(): Pair<OutboxEventDispatchWorker, MockRestServiceServer> {
        val builder = RestClient.builder()
        val mockServer = MockRestServiceServer.bindTo(builder).build()
        val worker =
            OutboxEventDispatchWorker(
                outboxEventRepository,
                otaChannelRepository,
                hostRepository,
                transactionTemplate,
                builder.build(),
                properties,
            )
        return worker to mockServer
    }

    feature("OTA_CHANNEL 정상 발송") {
        scenario("callbackBaseUrl로 발송에 성공하면 SENT로 전이하고 payload를 원본 JSON 그대로 싣는다") {
            savedOtaChannel("OTA_MOCK_1", "http://mock-ota-channel-1/webhook")
            val outboxEventId = savedOutboxEventId("OUTBOX-1", OutboxTargetType.OTA_CHANNEL, "OTA_MOCK_1")
            val (worker, mockServer) = newWorkerWithMockServer()

            mockServer
                .expect(requestTo("http://mock-ota-channel-1/webhook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.outboxKey").value("OUTBOX-1"))
                .andExpect(jsonPath("$.eventType").value("RESERVATION_CONFIRMED"))
                .andExpect(jsonPath("$.payload.roomCode").value("ROOM-101"))
                .andRespond(withSuccess())

            worker.dispatchPendingOutboxEvents()

            mockServer.verify()
            outboxEventRepository.findById(outboxEventId).orElseThrow().status shouldBe OutboxEventStatus.SENT
        }
    }

    feature("발송 실패 시 재시도 갱신") {
        scenario("5xx 응답이면 retry_count가 증가하고 status=FAILED, next_retry_at이 미래로 갱신된다") {
            savedOtaChannel("OTA_MOCK_500", "http://mock-ota-channel-500/webhook")
            val outboxEventId = savedOutboxEventId("OUTBOX-500", OutboxTargetType.OTA_CHANNEL, "OTA_MOCK_500")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-ota-channel-500/webhook")).andRespond(withServerError())

            worker.dispatchPendingOutboxEvents()

            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.FAILED
            updated.retryCount shouldBe 1
            (updated.nextRetryAt.isAfter(LocalDateTime.now())) shouldBe true
        }

        scenario("4xx 응답이면 retry_count가 증가하고 status=FAILED로 갱신된다") {
            savedOtaChannel("OTA_MOCK_400", "http://mock-ota-channel-400/webhook")
            val outboxEventId = savedOutboxEventId("OUTBOX-400", OutboxTargetType.OTA_CHANNEL, "OTA_MOCK_400")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer.expect(requestTo("http://mock-ota-channel-400/webhook")).andRespond(withBadRequest())

            worker.dispatchPendingOutboxEvents()

            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.FAILED
            updated.retryCount shouldBe 1
        }

        scenario("커넥션 실패(타임아웃 등)여도 retry_count가 증가하고 status=FAILED로 갱신된다") {
            savedOtaChannel("OTA_MOCK_IO", "http://mock-ota-channel-io/webhook")
            val outboxEventId = savedOutboxEventId("OUTBOX-IO", OutboxTargetType.OTA_CHANNEL, "OTA_MOCK_IO")
            val (worker, mockServer) = newWorkerWithMockServer()
            mockServer
                .expect(requestTo("http://mock-ota-channel-io/webhook"))
                .andRespond(withException(IOException("connection reset")))

            worker.dispatchPendingOutboxEvents()

            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.FAILED
            updated.retryCount shouldBe 1
        }

        scenario("callbackBaseUrl이 없는 채널이면 HTTP 호출 없이 재시도가 갱신된다") {
            savedOtaChannel("OTA_MOCK_NO_URL", callbackBaseUrl = null)
            val outboxEventId = savedOutboxEventId("OUTBOX-NO-URL", OutboxTargetType.OTA_CHANNEL, "OTA_MOCK_NO_URL")
            val (worker, mockServer) = newWorkerWithMockServer()

            worker.dispatchPendingOutboxEvents()

            mockServer.verify()
            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.FAILED
            updated.retryCount shouldBe 1
        }

        scenario("target_code에 해당하는 채널이 아예 없으면 재시도가 갱신된다") {
            val outboxEventId = savedOutboxEventId("OUTBOX-NO-CHANNEL", OutboxTargetType.OTA_CHANNEL, "OTA_NONEXISTENT")
            val (worker, mockServer) = newWorkerWithMockServer()

            worker.dispatchPendingOutboxEvents()

            mockServer.verify()
            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.FAILED
            updated.retryCount shouldBe 1
        }

        scenario("maxRetryCount에 도달하면 DEAD로 전이하고 이후 폴링에서 제외된다") {
            val outboxEventId =
                savedOutboxEventId(
                    "OUTBOX-DEAD",
                    OutboxTargetType.OTA_CHANNEL,
                    "OTA_NONEXISTENT_DEAD",
                    retryCount = properties.maxRetryCount - 1,
                )
            val (worker, _) = newWorkerWithMockServer()

            worker.dispatchPendingOutboxEvents()

            val updated = outboxEventRepository.findById(outboxEventId).orElseThrow()
            updated.status shouldBe OutboxEventStatus.DEAD
            updated.retryCount shouldBe properties.maxRetryCount

            val (rePollWorker, _) = newWorkerWithMockServer()
            rePollWorker.dispatchPendingOutboxEvents()
            outboxEventRepository.findById(outboxEventId).orElseThrow().status shouldBe OutboxEventStatus.DEAD
        }
    }

    feature("HOST 스텁") {
        scenario("HOST 대상이면 실제 HTTP 호출 없이 즉시 SENT로 전이한다") {
            savedHost("HOST-STUB-1")
            val outboxEventId = savedOutboxEventId("OUTBOX-HOST-1", OutboxTargetType.HOST, "HOST-STUB-1")
            val (worker, mockServer) = newWorkerWithMockServer()

            worker.dispatchPendingOutboxEvents()

            mockServer.verify()
            outboxEventRepository.findById(outboxEventId).orElseThrow().status shouldBe OutboxEventStatus.SENT
        }
    }

    feature("동시 워커 폴링 (SKIP LOCKED)") {
        scenario("두 워커가 동시에 폴링해도 같은 row를 중복으로 집어가지 않는다") {
            savedHost("HOST-CONCURRENT")
            (1..10).forEach { savedOutboxEventId("OUTBOX-CONCURRENT-$it", OutboxTargetType.HOST, "HOST-CONCURRENT") }

            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)
            // 두 트랜잭션이 각자 SKIP LOCKED로 조회한 뒤에도 잠시 커밋을 미뤄, 두 락이 동시에 걸려 있는
            // 구간을 강제로 만든다.
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
                                    val batch = outboxEventRepository.findBatchForDispatch(LocalDateTime.now(), 5)
                                    holdLatch.countDown()
                                    holdLatch.await(5, TimeUnit.SECONDS)
                                    batch.map { event -> event.id }
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
