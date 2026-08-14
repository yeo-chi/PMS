package yeo.chi.proejct.pms.operation.domain

import java.time.LocalDateTime

data class InboundEvent(
    val notificationKey: String,
    // 예약 서버 쪽 reservation_no를 문자열로만 보관한다(교차 DB, FK 없음).
    val reservationNo: String,
    val eventType: String,
    val payload: String,
    val receivedAt: LocalDateTime?,
)
