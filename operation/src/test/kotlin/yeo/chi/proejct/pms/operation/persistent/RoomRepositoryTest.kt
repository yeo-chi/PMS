package yeo.chi.proejct.pms.operation.persistent

import io.kotest.assertions.throwables.shouldThrow
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import yeo.chi.proejct.pms.operation.domain.Host
import yeo.chi.proejct.pms.operation.domain.HostStatus
import yeo.chi.proejct.pms.operation.domain.Room
import yeo.chi.proejct.pms.operation.domain.RoomStatus

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomRepositoryTest(
    private val hostRepository: HostRepository,
    private val roomRepository: RoomRepository,
) : MySqlIntegrationTest({

    fun savedHostId(): Long =
        hostRepository
            .saveAndFlush(
                Host(
                    id = null,
                    hostCode = "HOST-ROOM-TEST",
                    name = "호스트 이름",
                    contactEmail = null,
                    contactPhone = null,
                    status = HostStatus.ACTIVE,
                    createdAt = null,
                    updatedAt = null,
                ).toEntity(),
            ).id

    fun newRoom(
        roomCode: String,
        hostId: Long,
    ): Room =
        Room(
            id = null,
            roomCode = roomCode,
            hostId = hostId,
            name = "디럭스 룸",
            address = "서울시 어딘가",
            capacity = 2,
            status = RoomStatus.ACTIVE,
            createdAt = null,
            updatedAt = null,
        )

    feature("Room 저장/조회") {
        scenario("동일한 room_code로 재삽입하면 저장이 거부된다") {
            val hostId = savedHostId()
            roomRepository.saveAndFlush(newRoom("ROOM-DUP", hostId).toEntity())

            shouldThrow<DataIntegrityViolationException> {
                roomRepository.saveAndFlush(newRoom("ROOM-DUP", hostId).toEntity())
            }
        }

        scenario("존재하지 않는 host_id로 저장하면 FK 제약 위반으로 거부된다") {
            shouldThrow<DataIntegrityViolationException> {
                roomRepository.saveAndFlush(newRoom("ROOM-NO-HOST", hostId = -1L).toEntity())
            }
        }
    }
})
