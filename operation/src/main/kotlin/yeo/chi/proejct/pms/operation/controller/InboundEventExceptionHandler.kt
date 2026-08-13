package yeo.chi.proejct.pms.operation.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 알 수 없는 eventType, payload에 필수 필드(platformId)가 없는 경우 등은 예약 서버가 잘못된
// 요청을 보낸 것이지 operation 서버의 결함이 아니므로 400으로 매핑한다. @Valid 검증 실패
// (필수 필드 blank)는 Spring MVC가 이미 기본으로 400을 반환하므로 별도 처리가 필요 없다.
@RestControllerAdvice
class InboundEventExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<Void> = ResponseEntity.badRequest().build()
}
