package yeo.chi.proejct.pms.reservation.domain

import java.time.LocalDate

// PostgreSQL daterange의 [startDate, endDate) 반개구간 의미론: endDate(체크아웃일)는 exclusive.
data class ReservationDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        // startDate >= endDate면 Range.closedOpen()이 빈 daterange를 만들어 excl_room_date_overlap과
        // 절대 겹치지 않으므로, 0박/음수박 "예약"이 제약을 우회해 그대로 저장돼버린다.
        require(startDate < endDate) {
            "startDate는 endDate보다 이전이어야 합니다: startDate=$startDate, endDate=$endDate"
        }
    }
}
