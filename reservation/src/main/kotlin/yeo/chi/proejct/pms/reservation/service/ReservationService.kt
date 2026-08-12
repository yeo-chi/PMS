package yeo.chi.proejct.pms.reservation.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.persistent.ReservationRepository
import yeo.chi.proejct.pms.reservation.persistent.toDomain
import yeo.chi.proejct.pms.reservation.persistent.toEntity

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val platformSyncClient: PlatformSyncClient,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ReservationService::class.java)
    }

    @Transactional
    fun createReservation(reservation: Reservation): Reservation {
        val existing = reservationRepository.findByPlatformCodeAndCode(
            reservation.platformCode,
            reservation.code,
        )
        if (existing != null) {
            logger.info(
                "Reservation already exists for platformCode={}, code={} - skip duplicate save/sync",
                reservation.platformCode,
                reservation.code,
            )
            return existing.toDomain()
        }

        val saved = reservationRepository.save(reservation.toEntity()).toDomain()

        platformSyncClient.syncReservationCreated(saved.toSyncRequest())

        return saved
    }

    private fun Reservation.toSyncRequest(): PlatformSyncRequest = PlatformSyncRequest(
        platformCode = platformCode,
        roomId = roomId,
        reservationCode = code,
        userIdentifyCode = userIdentifyCode,
        startDate = startDate,
        endDate = endDate,
        reservedAt = reservedAt,
        status = status.name,
    )
}
