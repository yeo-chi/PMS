package yeo.chi.proejct.pms.reservation.persistent

import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRepository : JpaRepository<ReservationEntity, Long>
