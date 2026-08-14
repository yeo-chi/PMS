-- reservation_code(reservations의 논리 유니크 ID)를 reservation_requests/outbound_notifications의
-- 외래키로 사용하도록 전환한다. 원래는 내부 PK(BIGINT)를 참조했으나(V1 주석: "조인 성능을 위해 내부
-- id 사용"), reservation_code가 이미 유니크가 보장되는 논리 키이므로 그대로 FK로 써도 무방하다고
-- 판단해 전환한다. outbound_notifications.request_id도 같은 이유로 reservation_requests.request_key를
-- 참조하도록 바꾼다.

ALTER TABLE reservations RENAME COLUMN reservation_no TO reservation_code;
ALTER TABLE reservations RENAME CONSTRAINT uq_reservation_no TO uq_reservation_code;

DROP INDEX idx_reservation_requests_reservation_id;
ALTER TABLE reservation_requests DROP CONSTRAINT reservation_requests_reservation_id_fkey;
ALTER TABLE reservation_requests DROP COLUMN reservation_id;
ALTER TABLE reservation_requests ADD COLUMN reservation_code VARCHAR(200) REFERENCES reservations (reservation_code);
CREATE INDEX idx_reservation_requests_reservation_code ON reservation_requests (reservation_code);

ALTER TABLE outbound_notifications DROP CONSTRAINT outbound_notifications_reservation_id_fkey;
ALTER TABLE outbound_notifications DROP COLUMN reservation_id;
ALTER TABLE outbound_notifications ADD COLUMN reservation_code VARCHAR(200) REFERENCES reservations (reservation_code);

ALTER TABLE outbound_notifications DROP CONSTRAINT outbound_notifications_request_id_fkey;
ALTER TABLE outbound_notifications DROP COLUMN request_id;
ALTER TABLE outbound_notifications ADD COLUMN request_key VARCHAR(256) REFERENCES reservation_requests (request_key);
