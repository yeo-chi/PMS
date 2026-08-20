# PMS — 숙박 예약 플랫폼

자사 숙박 상품을 자사 서비스와 다수의 OTA(Booking.com, Agoda 등)에 동시 노출/판매하는 시스템.
동일 상품이 여러 채널에서 동시에 예약될 수 있으므로, 예약·취소·변경 요청을 **멱등하게, 중복/누락
없이** 처리하고 채널 간 재고 정합성을 보장하는 것이 핵심 과제다. 전체 도메인 설계는
[`docs/기획문서.md`](docs/기획문서.md), 실제 흐름은 [`docs.dataflow/`](docs/dataflow/README.md)의
시퀀스 다이어그램을 참고.

## 기술 스택

- **언어/런타임**: Kotlin 2.0, Java 21 (JVM 툴체인)
- **프레임워크**: Spring Boot 3.5 (Spring Web, Spring Data JPA/Hibernate)
- **빌드**: Gradle(Kotlin DSL) 멀티모듈 — 루트는 순수 애그리게이터, `reservation`/`operation`이 각자
  독립 배포 가능한 Spring Boot 앱
- **DB**: `reservation`은 PostgreSQL 16(`daterange` + GIST exclusion 제약 활용), `operation`은
  MySQL 8(생성 컬럼, `ON UPDATE CURRENT_TIMESTAMP` 활용) — 두 모듈이 서로 다른 RDBMS를 쓰며
  Gradle 컴파일 의존 없이 HTTP로만 통신
- **스키마 관리**: 마이그레이션 도구 없이 `docs/schema/*.sql`을 유일한 DDL 소스로 관리한다.
  운영 DB는 DBA가 이 SQL로 직접 스키마를 관리하고, 애플리케이션은 `ddl-auto: validate`로 매핑만
  검증한다 — exclusion 제약·생성 컬럼처럼 JPA 매핑만으로 표현 불가능한 것들이 있어서 Hibernate
  자동 생성에 맡기지 않는다.
- **날짜 범위 매핑**: `hypersistence-utils`(`PostgreSQLRangeType`)로 PostgreSQL `daterange` ↔
  Kotlin `Range<LocalDate>` 매핑
- **테스트**: Kotest(`FeatureSpec`) + MockK, 로컬에 미리 띄워둔 실제 PostgreSQL/MySQL(`reservation_test`
  / `operation_test`)에 직접 연결해 통합 테스트 — DB 벤더 종속 기능은 인메모리 DB로 재현 불가능하므로
- **로컬 인프라**: 프로젝트가 DB 컨테이너를 소유하지 않는다. 개발자가 로컬에 PostgreSQL 16 /
  MySQL 8을 직접 준비(`docker run` 등 무엇이든)하고 `docs/schema/*.sql`을 한 번 적용해두면 됨

## 동시성 처리 — "다른 채널이 이미 그 방을 잡았는가"

같은 방·겹치는 날짜에 대해 여러 채널(OTA A, OTA B, 자사 서비스)이 동시에 예약을 시도할 수 있다.
이걸 애플리케이션 코드로 "먼저 조회해서 비어있으면 저장"하는 방식(TOCTOU)으로 막으면 동시 요청
사이에 반드시 경쟁 구간이 남는다. 대신 **DB 제약 자체를 신뢰의 경계로 삼았다**:

- `reservations` 테이블에 PostgreSQL `EXCLUDE USING GIST (room_code WITH =, date_range WITH &&) WHERE (status IN ('CONFIRMED','PENDING_CANCEL'))` 제약을 걸어, 같은 방·겹치는 날짜의 두 번째 확정 시도는 **DB 레벨에서 원자적으로** 거부된다. 애플리케이션은 이 제약 위반(`DataIntegrityViolationException`)을 잡아 "중복예약으로 거부"로 변환만 하면 된다 — 별도의 락 조회 로직이 없다.
- `PENDING_CANCEL`(호스트가 취소 요청했지만 아직 OTA가 최종 확정하지 않은 상태)도 이 제약의 보호 대상에 포함시켜, 취소 협의 중에 재고가 새로 팔리는 모순을 막았다.
- 상태 변경(예: `CONFIRMED → CANCELLED`)에는 낙관적 락(`@Version`)을 적용해 동시 업데이트 충돌을 감지하고, 충돌 시 트랜잭션 밖에서 최신 상태를 다시 읽어 재시도한다(최대 3회) — 실패한 트랜잭션의 세션을 그대로 재사용하면 "예외 이후 flush" 문제가 생기기 때문에 반드시 새 트랜잭션에서 재조회한다.
- 두 서버 간 이벤트 전달(Outbox → 상대 서버)은 여러 워커 인스턴스가 동시에 폴링해도 같은 행을 중복 처리하지 않도록 `SELECT ... FOR UPDATE SKIP LOCKED`로 배치를 나눠 갖는다. MySQL(`operation`)에서는 기본 격리수준(REPEATABLE READ)이 `SKIP LOCKED` + `ORDER BY LIMIT` 조합과 만나 갭 락으로 잠기지 않은 행을 누락시키는 사례가 실제로 재현되어, 이 경로의 트랜잭션 격리수준을 READ COMMITTED로 낮췄다(InnoDB의 큐 폴링 패턴 권장 사항과도 일치).

## 멱등성 처리 — "같은 요청이 두 번 오면"

네트워크 재시도, 통보형(Async) OTA의 중복 전송 등으로 동일 요청이 여러 번 도착할 수 있다. 모든
인입 지점에 **요청 단위 멱등키**를 두고, 처리 전 먼저 조회해 이미 처리된 요청이면 재처리 없이
이전 결과를 그대로 반환한다.

- **예약 요청(BOOK)**: `platformId:platformReservationRef:roomCode:startDate:endDate` 조합으로 키를 만든다 — 채널이 발급한 예약 참조값까지 포함해야 "같은 초에 들어온 서로 다른 두 예약 시도"가 하나로 뭉개지지 않는다(초기 구현은 이 필드를 빠뜨려 두 개의 서로 다른 동시 예약이 충돌 대신 하나가 다른 하나의 성공 결과를 그대로 돌려받는 버그가 있었고, 테스트로 재현해 수정했다).
- **취소통보/예약변경(CANCEL_CONFIRM/CHANGE)**: 채널이 요청 단위로 발급하는 `externalRequestId`가 있으면 그 값으로 정확히 식별하고, 없으면 초 단위 타임스탬프로 폴백한다.
- **호스트 취소요청(CANCEL_REQUEST)**: 호스트에게는 OTA의 `externalRequestId` 같은 참조값이 원래 없어 초 단위 폴백이 기본이지만, 재요청 테스트를 결정적으로 만들기 위해 선택적 `externalRequestId`를 추가했다. 초 경계 충돌로 멱등키가 겹쳐도 안전하도록, 상태 기반 멱등성(이미 `PENDING_CANCEL`/`CANCELLED`면 재전이 없이 감사 기록만 추가)을 최종 방어선으로 둔다.
- **서버 간 이벤트 전달(Transactional Outbox)**: 예약 서버의 상태 변경과 `outbound_notifications` insert가 항상 같은 트랜잭션으로 커밋된다. 운영 서버는 이 이벤트를 `notification_key` UNIQUE 제약으로 수신측 멱등성을 보장하며 받아, `inbound_events`에 먼저 기록한 뒤 외부 채널로 보낼 `outbox_events`를 만든다. 두 방향 모두 실패 시 지수 백오프로 재시도하고, `maxRetryCount` 초과 시 DEAD로 전이해 무한 재시도를 막는다.
- **통보 대상 팬아웃**: 운영 서버는 이벤트 종류에 따라 통보 대상을 나눈다 — 예약 확정/취소확정/변경은 원 채널뿐 아니라 같은 방을 노출 중인 다른 활성 채널에도 재고 변경(마감/오픈/변경) 통보를 함께 발행하고, 예약 변경은 방을 소유한 호스트에게도 별도 통보를 만든다. 세 대상 모두 원본 통보와 같은 트랜잭션에서 `outbox_events`에 append되므로, 한쪽만 기록되고 나머지가 유실되는 상태는 발생하지 않는다.
