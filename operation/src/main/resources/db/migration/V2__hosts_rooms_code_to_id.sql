-- 애플리케이션이 hosts.id(기술 PK)를 전혀 참조하지 않고, 논리적 유니크 키만으로 hosts를
-- 식별하기로 결정 — 그 컬럼명을 host_code에서 host_id로 통일한다(HostEntity.hostId와 맞춘다).
ALTER TABLE hosts
    DROP INDEX uq_host_code,
    CHANGE COLUMN host_code host_id VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uq_host_id (host_id);

-- 애플리케이션이 rooms.id(기술 PK)를 전혀 참조하지 않고, 논리적 유니크 키만으로 rooms를
-- 식별/조인하기로 결정 — 그 컬럼명을 room_code에서 room_id로 통일한다(Kotlin 도메인/엔티티의
-- roomId 필드명과 맞춘다).

ALTER TABLE rooms
    DROP INDEX uq_room_code,
    CHANGE COLUMN room_code room_id VARCHAR(80) NOT NULL,
    ADD UNIQUE KEY uq_room_id (room_id);

-- room_channel_listings.room_id는 기존엔 rooms.id(BIGINT)를 가리키는 FK였다. rooms.id를 더 이상
-- 쓰지 않기로 했으므로, rooms.room_id(VARCHAR, 위에서 재명명)를 가리키도록 타입과 FK 대상을 바꾼다.
ALTER TABLE room_channel_listings
    DROP FOREIGN KEY fk_listing_room;

ALTER TABLE room_channel_listings
    MODIFY COLUMN room_id VARCHAR(80) NOT NULL;

ALTER TABLE room_channel_listings
    ADD CONSTRAINT fk_listing_room FOREIGN KEY (room_id) REFERENCES rooms (room_id);
