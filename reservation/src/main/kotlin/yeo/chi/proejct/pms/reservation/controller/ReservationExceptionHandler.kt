package yeo.chi.proejct.pms.reservation.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// BOOK/CHANGE의 require(initiatedBy != HOST) { ... }가 던지는 IllegalArgumentException을 400으로
// 변환한다 — operation의 InboundEventExceptionHandler와 동일 패턴. @Valid 검증 실패(blank/null)는
// Spring MVC가 기본으로 400을 반환하므로 별도 처리가 필요 없다.
@RestControllerAdvice
class ReservationExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<Void> = ResponseEntity.badRequest().build()
}
