# CHANGE — 예약 변경

`POST /api/reservations/change` → `ReservationService.change()`. 기획문서 4.4 — "취소+재예약"이
아닌 기존 예약 건에 대한 날짜 변경으로 취급한다. HOST는 시작할 수 없다.

- 멱등키: `externalRequestId`가 있으면 `platformId:externalRequestId`, 없으면
  `platformId:platformReservationRef:CHANGE:초단위시각`로 폴백
- 변경 가능한 상태는 `CONFIRMED`뿐 — `PENDING_CANCEL`/`CANCELLED`는 변경 불가로 거부
- 낙관적 락 충돌(재시도 대상)과 GIST exclusion 위반(진짜 날짜 겹침, 재시도해도 다시 겹침)을 재시도
  루프 안에서 서로 다르게 처리하는 첫 액션 — 겹침은 예약 row가 이미 존재해 `reservation_id`를
  채울 수 있으므로, BOOK과 달리 거부 통보(`RESERVATION_REJECTED`)를 실제 예약에 연결해 남긴다

```mermaid
sequenceDiagram
    autonumber
    actor Client as OTA/자사서비스
    participant RC as ReservationController
    participant RS as ReservationService
    participant RDB as reservation DB
    participant OW as OutboundNotificationDispatchWorker
    participant IS as operation:InboundEventService
    participant XW as OutboxEventDispatchWorker
    actor Ext as 채널

    Client->>RC: POST /api/reservations/change<br/>{newStartDate, newEndDate}
    RC->>RS: change(command)
    RS->>RDB: findByRequestKey (멱등 확인) → 없음
    RS->>RDB: findByPlatformIdAndPlatformReservationRef

    alt 예약 없음
        RS->>RDB: INSERT reservation_requests (FAILED, RESERVATION_NOT_FOUND)
        RS-->>Client: 404
    else PENDING_CANCEL 또는 CANCELLED (변경 불가)
        RS->>RDB: INSERT reservation_requests (FAILED, NOT_CHANGEABLE)
        RS-->>Client: 409
    else CONFIRMED
        RS->>RDB: UPDATE reservations SET date_range=신규 구간<br/>(낙관적 락 + GIST 제약 동시 검사)
        alt 낙관적 락 충돌
            Note over RS: 트랜잭션 밖에서 재시도(최대 3회)
        else GIST 위반 (다른 예약과 진짜 겹침)
            RS->>RDB: INSERT reservation_requests (CONFLICT)
            RS->>RDB: INSERT outbound_notifications<br/>(RESERVATION_REJECTED, 기존 reservation_id 사용)
            RS-->>Client: 409
        else 성공
            RS->>RDB: INSERT reservation_requests (SUCCESS,<br/>old_date_range/new_date_range 둘 다 기록)
            RS->>RDB: INSERT outbound_notifications (RESERVATION_CHANGED)
            RS-->>Client: 200
        end
    end

    Note over OW,Ext: 비동기 (성공/거부 모두 통보됨,<br/>팬아웃은 CONFIRMED/CANCELLED 이벤트에만 적용되므로 여기선 없음)
    OW->>IS: POST /api/inbound-events (RESERVATION_CHANGED 또는 RESERVATION_REJECTED)
    IS->>XW: outbox_events 경유
    XW->>Ext: POST callbackBaseUrl
```
