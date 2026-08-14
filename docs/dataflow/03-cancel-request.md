# CANCEL_REQUEST — 호스트 취소요청 (2단계)

`POST /api/reservations/{reservationId}/cancel-request` → `ReservationService.cancelRequest()`.
기획문서 2.3/4.3 — 호스트는 취소를 **요청**만 할 수 있고, 확정 권한은 없다. 대상 OTA에게 요청이
전달된 뒤 그 OTA의 취소통보([02-cancel-confirm.md](02-cancel-confirm.md))가 돌아와야 비로소
확정된다. 그 전까지 재고는 계속 점유 상태(`PENDING_CANCEL`)로 유지된다.

- 멱등키: `externalRequestId`가 있으면 `reservationId:CANCEL_REQUEST:externalRequestId`, 없으면
  `reservationId:CANCEL_REQUEST:초단위시각`로 폴백
- `PENDING_CANCEL`도 `reservations`의 GIST exclusion 제약 대상에 포함되어 있어, 이 기간 동안도
  같은 room_code·겹치는 날짜의 신규 BOOK은 계속 거부된다(재고 점유 유지)
- 이 액션 자체는 재고를 실제로 바꾸지 않으므로(여전히 점유 상태) `operation`의 채널 팬아웃 대상이
  아니다 — 팬아웃은 `RESERVATION_CONFIRMED`/`RESERVATION_CANCELLED`에서만 발생

## 1단계 — 호스트 취소요청 → PENDING_CANCEL

다이어그램 소스: [`03-cancel-request-phase1.mermaid`](03-cancel-request-phase1.mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor Host as 호스트
    participant RC as ReservationController
    participant RS as ReservationService
    participant RDB as reservation DB
    participant OW as OutboundNotificationDispatchWorker
    participant IS as operation:InboundEventService
    participant ODB as operation DB
    participant XW as OutboxEventDispatchWorker
    actor OTA as 대상 OTA

    Host->>RC: POST /api/reservations/{id}/cancel-request<br/>{reason}
    RC->>RS: cancelRequest(command)
    RS->>RDB: findByRequestKey (멱등 확인) → 없음
    RS->>RDB: findById(reservationId)

    alt 예약 없음
        Note over RS: reservation_requests.platform_id가 NOT NULL이라<br/>감사 로그를 남길 수 없음 — WARN 로그만 남기고<br/>합성 FAILED 결과 반환(row 없음)
        RS-->>Host: 404
    else 이미 CANCELLED
        RS->>RDB: INSERT reservation_requests (FAILED,<br/>ALREADY_CANCELLED)
        RS-->>Host: 409
    else CONFIRMED
        RS->>RDB: UPDATE reservations SET status=PENDING_CANCEL<br/>(재고는 계속 점유)
        RS->>RDB: INSERT reservation_requests (SUCCESS)
        RS->>RDB: INSERT outbound_notifications<br/>(CANCEL_REQUESTED, 같은 트랜잭션)
        RS-->>Host: 200
    else 이미 PENDING_CANCEL
        RS->>RDB: INSERT reservation_requests (SUCCESS,<br/>재전이 없이 감사 기록만)
        RS-->>Host: 200
    end

    Note over OW,OTA: 비동기 — 팬아웃 없음(재고 상태 변화 없음)
    OW->>IS: POST /api/inbound-events (CANCEL_REQUESTED)
    IS->>ODB: INSERT inbound_events + outbox_events<br/>(대상: 원 OTA 채널만)
    XW->>OTA: POST callbackBaseUrl (취소요청 전달)
```

## 2단계 — OTA가 취소통보를 보내야 최종 확정

호스트 취소요청을 전달받은 OTA가 고객과 협의를 마치고 나면, [02-cancel-confirm.md](02-cancel-confirm.md)와
**완전히 동일한 플로우**로 `POST /api/reservations/cancel-confirm`을 호출한다. `attemptCancelConfirm`은
`CONFIRMED`와 `PENDING_CANCEL` 둘 다에서 `CANCELLED`로의 전이를 허용하므로 별도 분기가 필요 없다.

다이어그램 소스: [`03-cancel-request-phase2.mermaid`](03-cancel-request-phase2.mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor OTA as 대상 OTA
    participant RS as ReservationService

    Note over OTA,RS: 호스트 취소요청 이후 임의 시점(협의 완료 후)
    OTA->>RS: cancelConfirm(...) — PENDING_CANCEL → CANCELLED
    Note over RS: 이후 흐름은 02-cancel-confirm.md와 동일<br/>(RESERVATION_CANCELLED 통보 + INVENTORY_REOPENED 팬아웃)
```
