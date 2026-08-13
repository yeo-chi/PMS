package yeo.chi.proejct.pms.reservation.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.service.CancelRequestCommand
import yeo.chi.proejct.pms.reservation.service.ReservationService

// ReservationService가 이미 4개 액션(book/cancelRequest/cancelConfirm/change)을 한 클래스에
// 모아둔 것과 대칭을 맞춘다. URL도 전부 /api/reservations 하위라 자연스럽게 리소스 하나로 묶인다.
@RestController
@RequestMapping("/api/reservations")
class ReservationController(
    private val reservationService: ReservationService,
) {

    @PostMapping
    fun book(
        @Valid @RequestBody request: BookReservationRequest,
    ): ResponseEntity<ReservationRequestResponse> {
        val result = reservationService.book(request.toCommand())
        // BOOK(SUCCESS)만 신규 자원을 만드는 유일한 액션이라 201로 별도 처리한다. 예약 조회 GET
        // 엔드포인트가 없어 가리킬 대상이 없으므로 Location 헤더는 붙이지 않는다.
        val status = if (result.resultStatus == RequestResultStatus.SUCCESS) HttpStatus.CREATED else result.toHttpStatus()
        return ResponseEntity.status(status).body(result.toResponse())
    }

    @PostMapping("/{reservationId}/cancel-request")
    fun cancelRequest(
        @PathVariable reservationId: Long,
        @Valid @RequestBody request: CancelRequestRequestBody,
    ): ResponseEntity<ReservationRequestResponse> {
        val result =
            reservationService.cancelRequest(
                CancelRequestCommand(reservationId, requireNotNull(request.reason)),
            )
        return ResponseEntity.status(result.toHttpStatus()).body(result.toResponse())
    }

    @PostMapping("/cancel-confirm")
    fun cancelConfirm(
        @Valid @RequestBody request: CancelConfirmRequest,
    ): ResponseEntity<ReservationRequestResponse> {
        val result = reservationService.cancelConfirm(request.toCommand())
        return ResponseEntity.status(result.toHttpStatus()).body(result.toResponse())
    }

    @PostMapping("/change")
    fun change(
        @Valid @RequestBody request: ChangeReservationRequest,
    ): ResponseEntity<ReservationRequestResponse> {
        val result = reservationService.change(request.toCommand())
        return ResponseEntity.status(result.toHttpStatus()).body(result.toResponse())
    }
}
