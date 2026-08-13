package yeo.chi.proejct.pms.reservation.persistent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest(
    private val reservationRepository: ReservationRepository,
    private val entityManager: EntityManager,
) : PostgresIntegrationTest({

    fun newReservation(
        platformReservationRef: String,
        roomCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
    ): Reservation {
        val now = OffsetDateTime.now()
        return Reservation(
            id = null,
            reservationNo = null,
            platformId = "OTA_BOOKING",
            platformReservationRef = platformReservationRef,
            roomCode = roomCode,
            dateRange = ReservationDateRange(startDate, endDate),
            status = status,
            version = 1,
            createdAt = now,
            updatedAt = now,
        )
    }

    feature("Reservation 저장/조회") {
        scenario("저장 후 reservation_no가 platform_id:platform_reservation_ref 형태로 채워진다") {
            val saved =
                reservationRepository.saveAndFlush(
                    newReservation("REF-1", "ROOM-101", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)).toEntity(),
                )

            saved.reservationNo shouldBe "OTA_BOOKING:REF-1"
        }

        scenario("신규 저장 시 version은 1로 저장된다") {
            val saved =
                reservationRepository.saveAndFlush(
                    newReservation("REF-2", "ROOM-102", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5)).toEntity(),
                )

            saved.version shouldBe 1
        }
    }

    feature("같은 room_code에 대한 날짜 겹침 방지 (excl_room_date_overlap)") {
        scenario("CONFIRMED 상태끼리 room_code가 같고 날짜가 겹치면 저장이 거부된다") {
            reservationRepository.saveAndFlush(
                newReservation("REF-3", "ROOM-201", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)).toEntity(),
            )

            shouldThrow<DataIntegrityViolationException> {
                reservationRepository.saveAndFlush(
                    newReservation("REF-4", "ROOM-201", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15)).toEntity(),
                )
            }
        }

        scenario("CONFIRMED와 PENDING_CANCEL도 겹치면 저장이 거부된다") {
            reservationRepository.saveAndFlush(
                newReservation("REF-5", "ROOM-202", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10)).toEntity(),
            )

            shouldThrow<DataIntegrityViolationException> {
                reservationRepository.saveAndFlush(
                    newReservation(
                        "REF-6",
                        "ROOM-202",
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2026, 4, 15),
                        status = ReservationStatus.PENDING_CANCEL,
                    ).toEntity(),
                )
            }
        }

        scenario("날짜가 겹치지 않으면 같은 room_code라도 저장에 성공한다") {
            reservationRepository.saveAndFlush(
                newReservation("REF-7", "ROOM-203", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)).toEntity(),
            )

            val saved =
                reservationRepository.saveAndFlush(
                    newReservation("REF-8", "ROOM-203", LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 10)).toEntity(),
                )

            saved.id shouldBe saved.id
        }

        scenario("CANCELLED 상태끼리는 같은 room_code에 날짜가 겹쳐도 저장에 성공한다") {
            reservationRepository.saveAndFlush(
                newReservation(
                    "REF-9",
                    "ROOM-204",
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 10),
                    status = ReservationStatus.CANCELLED,
                ).toEntity(),
            )

            val saved =
                reservationRepository.saveAndFlush(
                    newReservation(
                        "REF-10",
                        "ROOM-204",
                        LocalDate.of(2026, 6, 3),
                        LocalDate.of(2026, 6, 12),
                        status = ReservationStatus.CANCELLED,
                    ).toEntity(),
                )

            saved.id shouldBe saved.id
        }
    }

    feature("reservation_no 유일성 (uq_reservation_no)") {
        scenario("동일한 platform_id + platform_reservation_ref로 재삽입하면 저장이 거부된다") {
            reservationRepository.saveAndFlush(
                newReservation("REF-DUP", "ROOM-301", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5)).toEntity(),
            )

            shouldThrow<DataIntegrityViolationException> {
                reservationRepository.saveAndFlush(
                    newReservation("REF-DUP", "ROOM-302", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5)).toEntity(),
                )
            }
        }
    }

    feature("낙관적 락 (version)") {
        scenario("로드 이후 다른 트랜잭션이 먼저 커밋하면, 로드 시점의 stale version으로 시도한 업데이트는 실패한다") {
            val saved =
                reservationRepository.saveAndFlush(
                    newReservation("REF-LOCK", "ROOM-401", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)).toEntity(),
                )

            val staleLoad = reservationRepository.findById(saved.id).orElseThrow()

            // 다른 트랜잭션이 이 row를 먼저 갱신해 version을 올린 상황을 네이티브 쿼리로 시뮬레이션한다.
            // (Hibernate 세션을 거치지 않으므로 staleLoad의 메모리 상 version은 갱신되지 않는다.)
            entityManager
                .createNativeQuery("UPDATE reservations SET version = version + 1 WHERE id = :id")
                .setParameter("id", saved.id)
                .executeUpdate()

            staleLoad.status = ReservationStatus.CANCELLED
            shouldThrow<ObjectOptimisticLockingFailureException> {
                reservationRepository.saveAndFlush(staleLoad)
            }
        }
    }
})
