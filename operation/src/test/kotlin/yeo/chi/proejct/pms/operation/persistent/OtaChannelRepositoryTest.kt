package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.OtaChannel
import yeo.chi.proejct.pms.operation.domain.OtaChannelIntegrationMode
import yeo.chi.proejct.pms.operation.domain.OtaChannelStatus
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import yeo.chi.proejct.pms.operation.persistent.repository.OtaChannelRepository

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OtaChannelRepositoryTest(
    private val otaChannelRepository: OtaChannelRepository,
) : MySqlIntegrationTest({

    fun newOtaChannel(platformId: String): OtaChannel =
        OtaChannel(
            id = null,
            platformId = platformId,
            name = "Booking.com",
            integrationMode = OtaChannelIntegrationMode.ASYNC,
            callbackBaseUrl = "https://ota.example.com/callback",
            apiKeyRef = "secret-ref-1",
            status = OtaChannelStatus.ACTIVE,
            createdAt = null,
            updatedAt = null,
        )

    feature("OtaChannel 저장/조회") {
        scenario("저장 후 조회하면 created_at/updated_at이 채워진다") {
            val saved = otaChannelRepository.saveAndFlush(newOtaChannel("OTA_BOOKING").toEntity())

            saved.createdAt.shouldNotBeNull()
            saved.updatedAt.shouldNotBeNull()
        }

        scenario("동일한 platform_id로 재삽입하면 저장이 거부된다") {
            otaChannelRepository.saveAndFlush(newOtaChannel("OTA_DUP").toEntity())

            shouldThrow<DataIntegrityViolationException> {
                otaChannelRepository.saveAndFlush(newOtaChannel("OTA_DUP").toEntity())
            }
        }
    }
})
