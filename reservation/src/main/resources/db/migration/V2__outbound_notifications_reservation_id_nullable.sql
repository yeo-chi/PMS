-- BOOK이 excl_room_date_overlap 위반으로 거부된 경우 예약 row가 없어 reservation_id를 채울 수
-- 없다. FK 제약(REFERENCES reservations(id))과 인덱스는 그대로 둔다 — nullable FK는 Postgres에서
-- 정상 동작한다(NULL은 FK 검사 대상이 아님).
ALTER TABLE outbound_notifications ALTER COLUMN reservation_id DROP NOT NULL;
