package yeo.chi.proejct.pms.reservation.persistent

import org.springframework.data.jpa.repository.JpaRepository
import yeo.chi.proejct.pms.reservation.domain.Reservation

interface ReservationRepository : JpaRepository<Reservation, Long> {

    fun existsByPlatformCodeAndCode(platformCode: String, code: String): Boolean

    fun findByPlatformCodeAndCode(platformCode: String, code: String): Reservation?
}
