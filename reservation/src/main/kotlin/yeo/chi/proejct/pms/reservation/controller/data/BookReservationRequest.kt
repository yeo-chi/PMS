package yeo.chi.proejct.pms.reservation.controller.data

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.Reservation
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange
import yeo.chi.proejct.pms.reservation.domain.ReservationStatus
import java.time.LocalDate

data class BookReservationRequest(
    @field:NotBlank val platformId: String,
    @field:NotBlank val platformReservationRef: String,
    @field:NotBlank val roomId: String,
    @field:NotNull val startDate: LocalDate?,
    @field:NotNull val endDate: LocalDate?,
    @field:NotNull val initiatedBy: RequestInitiator?,
) {
    fun toDomain() = Reservation(
        platformId = platformId,
        platformReservationRef = platformReservationRef,
        roomId = roomId,
        dateRange = ReservationDateRange(requireNotNull(startDate), requireNotNull(endDate)),
        status = ReservationStatus.CONFIRMED,
        initiatedBy = requireNotNull(initiatedBy),
    )
}
