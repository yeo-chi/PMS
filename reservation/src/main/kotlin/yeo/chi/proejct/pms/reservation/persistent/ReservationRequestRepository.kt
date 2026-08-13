package yeo.chi.proejct.pms.reservation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRequestRepository : JpaRepository<ReservationRequestEntity, Long> {
    fun findByRequestKey(requestKey: String): ReservationRequestEntity?
}
