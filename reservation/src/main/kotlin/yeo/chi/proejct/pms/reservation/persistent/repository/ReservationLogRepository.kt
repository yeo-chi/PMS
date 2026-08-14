package yeo.chi.proejct.pms.reservation.persistent.repository

import org.springframework.data.jpa.repository.JpaRepository
import yeo.chi.proejct.pms.reservation.persistent.entity.ReservationLogEntity

interface ReservationLogRepository : JpaRepository<ReservationLogEntity, Long> {
    fun findByRequestKey(requestKey: String): ReservationLogEntity?
}
