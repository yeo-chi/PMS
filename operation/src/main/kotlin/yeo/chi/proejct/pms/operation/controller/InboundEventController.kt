package yeo.chi.proejct.pms.operation.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import yeo.chi.proejct.pms.operation.service.InboundEventService

@RestController
@RequestMapping("/api/inbound-events")
class InboundEventController(
    private val inboundEventService: InboundEventService,
) {

    // 호출 측(reservation의 OutboundNotificationDispatchWorker)은 toBodilessEntity()로 상태 코드만
    // 보므로 응답 바디는 만들지 않는다 — 신규 처리든 멱등 재처리든 항상 200.
    @PostMapping
    fun receive(
        @Valid @RequestBody request: InboundEventRequest,
    ): ResponseEntity<Void> {
        inboundEventService.receive(request)
        return ResponseEntity.ok().build()
    }
}
