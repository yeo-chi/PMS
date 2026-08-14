package yeo.chi.proejct.pms.reservation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationEntity

interface ReservationRepository : JpaRepository<ReservationEntity, Long> {
    fun findByPlatformIdAndPlatformReservationRef(
        platformId: String,
        platformReservationRef: String,
    ): ReservationEntity?

    fun findByReservationCode(reservationCode: String): ReservationEntity?
}
