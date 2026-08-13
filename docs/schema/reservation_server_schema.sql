-- =====================================================================
-- 예약 서버 (Reservation Server) - PostgreSQL DDL
-- 역할: 호스트/OTA로부터의 예약·취소·취소요청 인입 처리
--       재고 정합성(중복 방지)과 예약 상태/이력 관리
--
-- [ID 설계 원칙]
-- 모든 테이블은 내부 기술용 PK(BIGINT IDENTITY, auto-increment)를 가진다.
-- 이 PK는 오직 테이블 내부 조인/성능 목적으로만 쓰이며, 값 자체에 업무적 의미는 없다.
-- 이와 별도로, 업무적으로 유일함이 보장되는 "논리적 유니크 ID" 컬럼을 두어
-- 외부 시스템과의 통신, 로그 추적, 사람이 읽는 식별에는 이 논리적 ID를 사용한다.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;


-- ---------------------------------------------------------------------
-- 1. reservations : 예약의 "현재 상태" (예약 1건 = row 1개, UPDATE로 상태 전이)
-- ---------------------------------------------------------------------
CREATE TABLE reservations (
    -- 내부 기술 PK (조인/성능용, 업무적 의미 없음)
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 논리적 유니크 ID: "채널ID:채널이 발급한 예약참조번호" 조합에서 파생.
    -- 외부 통신, 로그 추적, 타 서버(운영 서버)와의 참조에는 항상 이 값을 사용한다.
    reservation_no               VARCHAR(200)
                                 GENERATED ALWAYS AS (platform_id || ':' || platform_reservation_ref) STORED,

    -- 채널 식별 (자사 서비스도 하나의 채널로 취급, 예: 'OTA_BOOKING', 'OTA_AGODA', 'SELF_SERVICE')
    platform_id                 VARCHAR(64) NOT NULL,

    -- 외부 채널이 발급한 예약 참조번호. 채널 내에서 유일함을 채널 쪽에서 보장한다고 가정.
    platform_reservation_ref    VARCHAR(128) NOT NULL,

    -- 운영 서버가 관리하는 상품(방)의 논리적 코드 (room_code). 교차 DB 참조이므로 FK 제약 없음.
    room_code                   VARCHAR(64) NOT NULL,

    -- 예약 기간 (겹침 판정에 사용)
    date_range                  DATERANGE NOT NULL,

    -- CONFIRMED: 정상 확정
    -- PENDING_CANCEL: 호스트가 취소를 요청했으나 OTA 최종 취소통보 대기 중 (재고는 계속 점유)
    -- CANCELLED: 최종 취소 확정 (재고 오픈)
    status                      VARCHAR(20) NOT NULL
                                 CHECK (status IN ('CONFIRMED', 'PENDING_CANCEL', 'CANCELLED')),

    -- 낙관적 락 (동일 예약 건에 대한 동시 변경/취소 요청 충돌 방지)
    version                     INT NOT NULL DEFAULT 1,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reservation_no UNIQUE (reservation_no),

    -- 핵심 제약: 같은 room_code에 대해 CONFIRMED 또는 PENDING_CANCEL 상태의 예약끼리는
    -- 날짜 범위가 겹칠 수 없다. (PENDING_CANCEL도 재고를 점유 중이므로 포함)
    CONSTRAINT excl_room_date_overlap EXCLUDE USING GIST (
        room_code WITH =,
        date_range WITH &&
    ) WHERE (status IN ('CONFIRMED', 'PENDING_CANCEL'))
);

CREATE INDEX idx_reservations_room_code ON reservations (room_code);
CREATE INDEX idx_reservations_platform ON reservations (platform_id, status);
CREATE INDEX idx_reservations_status_pending ON reservations (status) WHERE status = 'PENDING_CANCEL';


-- ---------------------------------------------------------------------
-- 2. reservation_requests : 인입된 "신청건" 단위 이력 (append-only, 멱등성 보장 테이블)
--    - 예약 1건에 대해 BOOK/CHANGE/CANCEL 요청이 여러 번 있을 수 있음
-- ---------------------------------------------------------------------
CREATE TABLE reservation_requests (
    -- 내부 기술 PK
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 논리적 유니크 ID이자 멱등키.
    -- BOOK: platform_id + ':' + product_code + ':' + start_date + ':' + end_date + ':BOOK'
    -- CHANGE/CANCEL: platform_id + ':' + external_request_id (OTA가 요청 단위로 발급한 값이 있는 경우)
    --                또는 reservation_no + ':' + action + ':' + 요청시각(초단위)
    request_key                 VARCHAR(256) NOT NULL,

    -- 대상 예약 (내부 PK 참조, BOOK 성공 시 채워짐. 조인 성능을 위해 내부 id 사용)
    reservation_id              BIGINT REFERENCES reservations(id),

    platform_id                 VARCHAR(64) NOT NULL,

    -- BOOK, CHANGE, CANCEL_REQUEST(호스트/OTA의 취소요청), CANCEL_CONFIRM(OTA의 최종 취소통보)
    action                      VARCHAR(30) NOT NULL
                                 CHECK (action IN ('BOOK', 'CHANGE', 'CANCEL_REQUEST', 'CANCEL_CONFIRM')),

    -- 누가 이 신청을 시작했는지 (OTA, HOST, SELF_SERVICE)
    initiated_by                 VARCHAR(20) NOT NULL
                                 CHECK (initiated_by IN ('OTA', 'HOST', 'SELF_SERVICE')),

    room_code                    VARCHAR(64),
    old_date_range                DATERANGE,   -- CHANGE/CANCEL 일 때만 값 존재
    new_date_range                DATERANGE,   -- BOOK/CHANGE 일 때만 값 존재

    -- SUCCESS, CONFLICT(겹침으로 거부), FAILED(기타 오류)
    result_status                 VARCHAR(20) NOT NULL
                                 CHECK (result_status IN ('SUCCESS', 'CONFLICT', 'FAILED')),
    reject_reason                  VARCHAR(50),  -- 예: 'DUPLICATE_BOOKING', 'ROOM_UNAVAILABLE'

    requested_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_request_key UNIQUE (request_key)
);

CREATE INDEX idx_reservation_requests_reservation_id ON reservation_requests (reservation_id);
CREATE INDEX idx_reservation_requests_platform ON reservation_requests (platform_id, action);


-- ---------------------------------------------------------------------
-- 3. outbound_notifications : 운영 서버로의 통보를 위한 아웃박스
--    (예약 서버 -> 운영 서버 API 호출의 신뢰성 보장용, Transactional Outbox)
--    예약 상태 변경 트랜잭션과 반드시 같은 트랜잭션에서 INSERT 되어야 함
-- ---------------------------------------------------------------------
CREATE TABLE outbound_notifications (
    -- 내부 기술 PK
    id                             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 논리적 유니크 ID이자 발송 멱등키.
    -- 예: reservation_no + ':' + event_type (+ 재시도가 아닌 "새로운 사건" 구분이 필요하면 발생시각 초단위 추가)
    -- 운영 서버로 API 호출 시 이 값을 그대로 전달하여, 운영 서버도 동일한 값으로 수신 멱등성을 보장하게 한다.
    notification_key               VARCHAR(256) NOT NULL,

    reservation_id                 BIGINT NOT NULL REFERENCES reservations(id),
    request_id                     BIGINT REFERENCES reservation_requests(id),

    -- RESERVATION_CONFIRMED, RESERVATION_REJECTED(중복예약 등으로 거부),
    -- CANCEL_REQUESTED(호스트발 취소요청 전달), RESERVATION_CANCELLED(최종 취소 확정)
    event_type                     VARCHAR(40) NOT NULL,

    payload                         JSONB NOT NULL,

    -- PENDING, SENT, FAILED, DEAD
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    retry_count                     INT NOT NULL DEFAULT 0,
    next_retry_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_notification_key UNIQUE (notification_key)
);

CREATE INDEX idx_outbound_notifications_polling
    ON outbound_notifications (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');


-- ---------------------------------------------------------------------
-- 참고: 폴링 워커 조회 쿼리 예시 (여러 워커 인스턴스 동시 실행 시 중복 방지)
-- ---------------------------------------------------------------------
-- SELECT * FROM outbound_notifications
-- WHERE status IN ('PENDING', 'FAILED') AND next_retry_at <= now()
-- ORDER BY created_at
-- LIMIT 100
-- FOR UPDATE SKIP LOCKED;