-- =====================================================================
-- 운영 서버 (Operation Server) - MySQL 8.0+ DDL
-- 역할: 호스트/OTA/방(상품) 마스터 데이터 관리
--       예약 서버로부터 받은 이벤트를 Transactional Outbox 패턴으로
--       OTA/호스트에 신뢰성 있게 통보
--
-- [ID 설계 원칙]
-- 모든 테이블은 내부 기술용 PK(BIGINT AUTO_INCREMENT)를 가진다.
-- 이 PK는 오직 테이블 내부 조인/성능 목적으로만 쓰이며, 값 자체에 업무적 의미는 없다.
-- 이와 별도로, 업무적으로 유일함이 보장되는 "논리적 유니크 ID" 컬럼(UNIQUE)을 두어
-- 외부 시스템과의 통신, 로그 추적, 예약 서버와의 참조에는 이 논리적 ID를 사용한다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. hosts : 호스트(숙소 운영 주체) 마스터
-- ---------------------------------------------------------------------
CREATE TABLE hosts (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,     -- 내부 기술 PK (애플리케이션에서는 참조하지 않음)

    -- 논리적 유니크 ID이자 애플리케이션이 실제로 사용하는 유일한 식별자.
    -- 사업자등록번호 등 업무적으로 유일함이 보장되는 호스트 사업자 코드
    host_id              VARCHAR(64) NOT NULL,

    name                 VARCHAR(200) NOT NULL,
    contact_email        VARCHAR(200),
    contact_phone        VARCHAR(50),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_host_id (host_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------
-- 2. ota_channels : 연동 채널(OTA/자사 서비스) 마스터
-- ---------------------------------------------------------------------
CREATE TABLE ota_channels (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,     -- 내부 기술 PK

    -- 논리적 유니크 ID: 채널 자체를 나타내는 코드. 예약 서버의 platform_id 와 동일한 값을 그대로 사용한다.
    platform_id          VARCHAR(64) NOT NULL,

    name                  VARCHAR(200) NOT NULL,                -- 예: 'Booking.com', '자사 서비스'

    -- SYNC: 우리 응답을 기다림 (요청 즉시 확정/거부 응답)
    -- ASYNC: 통보형, 우리 응답을 기다리지 않고 자체 확정 후 결과만 통보
    integration_mode     VARCHAR(10) NOT NULL DEFAULT 'ASYNC'
                         CHECK (integration_mode IN ('SYNC', 'ASYNC')),

    -- 외부 채널로 통보를 보낼 실제 엔드포인트/설정
    callback_base_url    VARCHAR(500),
    api_key_ref           VARCHAR(200),                         -- 시크릿 매니저 참조 키 (평문 저장 금지)

    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    created_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_platform_id (platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------
-- 3. rooms : 상품(객실) 마스터. 예약 서버의 reservations.room_code 가 이 room_id 를 참조 (교차 DB, FK 미설정)
-- ---------------------------------------------------------------------
CREATE TABLE rooms (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,     -- 내부 기술 PK (애플리케이션에서는 참조하지 않음)

    -- 논리적 유니크 ID이자 애플리케이션이 실제로 사용하는 유일한 식별자.
    -- 호스트 내에서 방을 구분하는 코드를 조합한 전역 유니크 코드
    -- 예: host_id + '-' + 호스트가 관리하는 방 고유 코드(room_local_code)
    room_id               VARCHAR(80) NOT NULL,

    host_id                BIGINT NOT NULL,                     -- 내부 FK (조인용)
    name                    VARCHAR(200) NOT NULL,
    address                  VARCHAR(500),
    capacity                  INT,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_room_id (room_id),
    CONSTRAINT fk_rooms_host FOREIGN KEY (host_id) REFERENCES hosts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rooms_host_id ON rooms (host_id);


-- ---------------------------------------------------------------------
-- 4. room_channel_listings : 방 - 채널 노출 매핑 (어느 방이 어느 OTA에 등록되어 있는지)
-- ---------------------------------------------------------------------
CREATE TABLE room_channel_listings (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,   -- 내부 기술 PK

    -- 논리적 유니크 ID: "채널ID:그 채널에서 이 방을 부르는 상품ID" 조합에서 파생
    listing_key             VARCHAR(200)
                         GENERATED ALWAYS AS (CONCAT(platform_id, ':', external_product_id)) STORED,

    room_id                  VARCHAR(80) NOT NULL,               -- rooms.room_id 참조 (기술 PK가 아닌 논리적 ID로 조인)
    ota_channel_id             BIGINT NOT NULL,                 -- 내부 FK (조인용)
    platform_id                 VARCHAR(64) NOT NULL,
    external_product_id           VARCHAR(128) NOT NULL,        -- 해당 채널에서 이 방을 부르는 상품 ID
    status                        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         CHECK (status IN ('ACTIVE', 'INACTIVE')),

    created_at                     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_listing_key (listing_key),
    CONSTRAINT fk_listing_room FOREIGN KEY (room_id) REFERENCES rooms(room_id),
    CONSTRAINT fk_listing_channel FOREIGN KEY (ota_channel_id) REFERENCES ota_channels(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------
-- 5. inbound_events : 예약 서버로부터 수신한 이벤트 기록 (수신 측 멱등성 보장)
--    예약 서버가 API 호출 시 동일 notification_key 로 재시도할 수 있으므로,
--    운영 서버도 이미 처리한 요청인지 먼저 확인해야 함
-- ---------------------------------------------------------------------
CREATE TABLE inbound_events (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY, -- 내부 기술 PK

    -- 논리적 유니크 ID: 예약 서버가 보낸 outbound_notifications.notification_key 그대로 사용
    notification_key           VARCHAR(256) NOT NULL,

    reservation_no               VARCHAR(200) NOT NULL,         -- 예약 서버 쪽 reservation_no (문자열로 보관, 교차 DB)
    event_type                    VARCHAR(40) NOT NULL,
    payload                        JSON NOT NULL,
    received_at                     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_notification_key (notification_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------
-- 6. outbox_events : OTA/호스트로의 최종 통보를 위한 Transactional Outbox
--    inbound_events 저장과 반드시 같은 트랜잭션에서 함께 INSERT
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY, -- 내부 기술 PK

    -- 논리적 유니크 ID이자 발송 멱등키. 외부에도 그대로 전달하여 외부의 중복수신 방어를 지원.
    -- 예: reservation_no + ':' + target_type + ':' + target_code + ':' + event_type
    outbox_key                   VARCHAR(300) NOT NULL,

    -- 통보 대상: OTA_CHANNEL 또는 HOST (대상 테이블이 둘로 갈리는 다형 참조라 단일 FK 대신 논리코드로 참조)
    target_type                   VARCHAR(20) NOT NULL
                         CHECK (target_type IN ('OTA_CHANNEL', 'HOST')),
    target_code                    VARCHAR(64) NOT NULL,        -- ota_channels.platform_id 또는 hosts.host_id

    reservation_no                  VARCHAR(200) NOT NULL,      -- 예약 서버 쪽 reservation_no (문자열 보관, 교차 DB)

    -- 원 채널 대상: RESERVATION_CONFIRMED, RESERVATION_REJECTED, CANCEL_REQUESTED,
    -- RESERVATION_CANCELLED, RESERVATION_CHANGED (그대로 전달)
    -- 다른 채널 팬아웃 대상: INVENTORY_CLOSED, INVENTORY_REOPENED, INVENTORY_CHANGED
    -- 호스트 대상: RESERVATION_CHANGED
    event_type                       VARCHAR(40) NOT NULL,
    payload                           JSON NOT NULL,

    status                            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    retry_count                        INT NOT NULL DEFAULT 0,
    next_retry_at                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    created_at                           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uq_outbox_key (outbox_key),
    KEY idx_outbox_polling (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------
-- 참고: 폴링 워커 조회 쿼리 예시 (MySQL 8.0+, 여러 워커 동시 실행 시 중복 방지)
-- ---------------------------------------------------------------------
-- SELECT * FROM outbox_events
-- WHERE status IN ('PENDING', 'FAILED') AND next_retry_at <= NOW(6)
-- ORDER BY created_at
-- LIMIT 100
-- FOR UPDATE SKIP LOCKED;