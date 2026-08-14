package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.persistent.entity.toEntity
import yeo.chi.proejct.pms.operation.persistent.repository.HostRepository

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HostRepositoryTest(
    private val hostRepository: HostRepository,
    private val entityManager: EntityManager,
) : MySqlIntegrationTest({

    fun newHost(hostCode: String): Host =
        Host(
            id = null,
            hostId = hostCode,
            name = "호스트 이름",
            contactEmail = "host@example.com",
            contactPhone = "010-0000-0000",
            status = HostStatus.ACTIVE,
            createdAt = null,
            updatedAt = null,
        )

    feature("Host 저장/조회") {
        scenario("저장 후 조회하면 created_at/updated_at이 DB에 의해 자동으로 채워진다") {
            val saved = hostRepository.saveAndFlush(newHost("HOST-1").toEntity())

            saved.createdAt.shouldNotBeNull()
            saved.updatedAt.shouldNotBeNull()
        }

        scenario("동일한 host_code로 재삽입하면 저장이 거부된다") {
            hostRepository.saveAndFlush(newHost("HOST-DUP").toEntity())

            shouldThrow<DataIntegrityViolationException> {
                hostRepository.saveAndFlush(newHost("HOST-DUP").toEntity())
            }
        }

        scenario("다른 컬럼이 갱신되면 updated_at은 바뀌고 created_at은 그대로 유지된다") {
            val saved = hostRepository.saveAndFlush(newHost("HOST-UPDATE").toEntity())

            entityManager
                .createNativeQuery("UPDATE hosts SET name = :name WHERE id = :id")
                .setParameter("name", "바뀐 이름")
                .setParameter("id", saved.id)
                .executeUpdate()
            entityManager.clear()

            val reloaded = hostRepository.findById(saved.id).orElseThrow()
            reloaded.createdAt shouldBe saved.createdAt
            reloaded.updatedAt shouldNotBe saved.updatedAt
        }
    }
})
