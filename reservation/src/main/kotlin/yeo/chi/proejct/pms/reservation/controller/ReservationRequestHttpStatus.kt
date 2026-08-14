package yeo.chi.proejct.pms.reservation.controller

import org.springframework.http.HttpStatus
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.ReservationLog

// rejectReason은 String(enum 아님)이라 컴파일러가 분기 누락을 잡아주지 못한다 — else를 반드시 두고,
// 알려지지 않은 사유는 500(서버 결함처럼 보임)이 아니라 409(요청이 지금 상태에서 처리될 수 없음)로
// 안전하게 기본 처리한다.
fun ReservationLog.toHttpStatus(): HttpStatus =
    when (resultStatus) {
        RequestResultStatus.SUCCESS -> HttpStatus.OK
        RequestResultStatus.CONFLICT -> HttpStatus.CONFLICT
        RequestResultStatus.FAILED ->
            when (rejectReason) {
                "RESERVATION_NOT_FOUND" -> HttpStatus.NOT_FOUND
                "ALREADY_CANCELLED" -> HttpStatus.CONFLICT
                "RESERVATION_NOT_CHANGEABLE" -> HttpStatus.CONFLICT
                else -> HttpStatus.CONFLICT
            }
    }
