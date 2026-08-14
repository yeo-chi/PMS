# BOOK — 예약 요청

`POST /api/reservations` → `ReservationService.book()`. 기획문서 4.1(OTA 예약 요청).

- 멱등키: `platformId:platformReservationRef:roomCode:startDate:endDate:BOOK` (`buildBookRequestKey`)
- 동시성 방어: 애플리케이션 선점 조회 없이, `reservations`의 `EXCLUDE USING GIST(room_code, date_range)` 제약 위반(`DataIntegrityViolationException`)을 그대로 신뢰
- HOST는 BOOK을 시작할 수 없음(`initiatedBy == HOST`면 즉시 `IllegalArgumentException`)

## 성공 케이스

다이어그램 소스: [`01-book-success.mermaid`](01-book-success.mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor Client as OTA/자사서비스
    participant RC as ReservationController
    participant RS as ReservationService
    participant RDB as reservation DB
    participant OW as OutboundNotificationDispatchWorker
    participant IS as operation:InboundEventService
    participant ODB as operation DB
    participant XW as OutboxEventDispatchWorker
    participant Ext as 원 채널 / 다른 채널(같은 room_code 노출 시)

    Client->>RC: POST /api/reservations
    RC->>RS: book(command)
    RS->>RDB: findByRequestKey (멱등 확인) → 없음
    RS->>RDB: INSERT reservations (status=CONFIRMED)<br/>GIST 제약 통과
    RS->>RDB: INSERT reservation_requests (SUCCESS)
    RS->>RDB: INSERT outbound_notifications<br/>(RESERVATION_CONFIRMED, 같은 트랜잭션)
    RS-->>RC: SUCCESS
    RC-->>Client: 201 Created

    Note over OW,Ext: 비동기 (최대 poll-interval 지연)
    OW->>RDB: SKIP LOCKED 배치 조회
    OW->>IS: POST /api/inbound-events (RESERVATION_CONFIRMED)
    IS->>ODB: INSERT inbound_events (멱등)
    IS->>ODB: INSERT outbox_events (원 채널 대상)
    opt 같은 room_code를 노출 중인 다른 활성 채널이 있으면
        IS->>ODB: INSERT outbox_events (다른 채널마다,<br/>eventType=INVENTORY_CLOSED)
    end
    IS-->>OW: 200
    OW->>RDB: outbound_notifications.status=SENT

    XW->>ODB: SKIP LOCKED 배치 조회
    XW->>Ext: POST callbackBaseUrl (원 채널: RESERVATION_CONFIRMED,<br/>다른 채널: INVENTORY_CLOSED)
    Ext-->>XW: 200
    XW->>ODB: outbox_events.status=SENT
```

## 중복예약 거부 케이스 (오버부킹)

같은 `room_code`·겹치는 날짜로 두 요청이 동시에 들어와 GIST 제약을 어느 한쪽이 위반하는 경우.

다이어그램 소스: [`01-book-conflict.mermaid`](01-book-conflict.mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor Loser as 늦게 도착한 채널
    participant RS as ReservationService
    participant RDB as reservation DB
    participant OW as OutboundNotificationDispatchWorker
    participant Ext as Loser 채널(운영 서버 경유)

    Loser->>RS: book(command)
    RS->>RDB: INSERT reservations 시도
    RDB--x RS: DataIntegrityViolationException<br/>(excl_room_date_overlap 위반)
    RS->>RDB: INSERT reservation_requests (CONFLICT,<br/>reject_reason=DUPLICATE_BOOKING)
    RS->>RDB: INSERT outbound_notifications<br/>(RESERVATION_REJECTED, reservation_id=null,<br/>같은 트랜잭션)
    RS-->>Loser: CONFLICT (409)

    Note over OW,Ext: 비동기
    OW->>Ext: (operation 경유) RESERVATION_REJECTED 통보<br/>"중복예약으로 인한 취소"
```
