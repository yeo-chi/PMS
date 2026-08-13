# CANCEL_CONFIRM — OTA 취소통보

`POST /api/reservations/cancel-confirm` → `ReservationService.cancelConfirm()`. 기획문서 2.3/4.2 —
"누가 취소를 시작했든, 최종 확정 시점은 항상 OTA의 취소통보가 도착한 시점". **거부 불가**: 통보이므로
도착 즉시 확정 처리한다.

- 멱등키: `externalRequestId`가 있으면 `platformId:externalRequestId`, 없으면
  `platformId:platformReservationRef:CANCEL_CONFIRM:초단위시각`로 폴백
- 낙관적 락(`version`) 충돌 시 트랜잭션 바깥에서 재시도(최대 3회) — 실패한 트랜잭션의 세션을
  재사용하면 "예외 후 flush" 문제가 생기기 때문

```mermaid
sequenceDiagram
    autonumber
    actor OTA
    participant RC as ReservationController
    participant RS as ReservationService
    participant RDB as reservation DB
    participant OW as OutboundNotificationDispatchWorker
    participant IS as operation:InboundEventService
    participant ODB as operation DB
    participant XW as OutboxEventDispatchWorker
    participant Ext as 다른 채널(같은 room_code 노출 시)

    OTA->>RC: POST /api/reservations/cancel-confirm
    RC->>RS: cancelConfirm(command)
    RS->>RDB: findByRequestKey (멱등 확인) → 없음
    RS->>RDB: findByPlatformIdAndPlatformReservationRef

    alt 예약 없음
        RS->>RDB: INSERT reservation_requests (FAILED,<br/>RESERVATION_NOT_FOUND)
        RS-->>OTA: 404
    else 이미 CANCELLED
        RS->>RDB: INSERT reservation_requests (SUCCESS, 재확인용<br/>감사 기록만, 알림 없음)
        RS-->>OTA: 200 (멱등하게 성공 처리)
    else CONFIRMED 또는 PENDING_CANCEL
        RS->>RDB: UPDATE reservations SET status=CANCELLED<br/>(낙관적 락, 충돌 시 재조회 후 재시도)
        RS->>RDB: INSERT reservation_requests (SUCCESS)
        RS->>RDB: INSERT outbound_notifications<br/>(RESERVATION_CANCELLED, 같은 트랜잭션)
        RS-->>OTA: 200
    end

    Note over OW,Ext: 비동기
    OW->>IS: POST /api/inbound-events (RESERVATION_CANCELLED)
    IS->>ODB: INSERT inbound_events + outbox_events (원 채널)
    opt 같은 room_code를 노출 중인 다른 활성 채널이 있으면
        IS->>ODB: INSERT outbox_events (다른 채널마다,<br/>eventType=INVENTORY_REOPENED)
    end
    XW->>Ext: POST callbackBaseUrl (재고 오픈 통보)
```
