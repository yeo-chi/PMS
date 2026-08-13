package yeo.chi.proejct.pms.reservation.service

import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.ReservationDateRange

data class BookReservationCommand(
    val platformId: String,
    val platformReservationRef: String,
    val roomCode: String,
    val dateRange: ReservationDateRange,
    val initiatedBy: RequestInitiator,
)

// platformReservationRef를 포함해 "같은 채널 예약 참조번호로 재시도"만 멱등 처리 대상으로 삼는다.
// 스키마 주석의 예시 포맷(platform_id+product_code+날짜)은 platformReservationRef가 달라도 같은
// room_code·날짜를 요청하는 서로 다른 예약 시도까지 같은 요청으로 묶어버려, uq_request_key 충돌로
// 실제 겹침(GIST exclusion) 경로를 타지 못하고 먼저 성공한 쪽 결과가 그대로 반환되는 문제가 있었다.
fun buildBookRequestKey(
    platformId: String,
    platformReservationRef: String,
    roomCode: String,
    dateRange: ReservationDateRange,
): String = "$platformId:$platformReservationRef:$roomCode:${dateRange.startDate}:${dateRange.endDate}:BOOK"
