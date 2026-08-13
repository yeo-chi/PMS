# PMS Project Configuration

## Domain Source of Truth
- `docs/기획문서.md` — 숙박 예약 플랫폼 기획 문서 (액터, 시나리오, 상태 모델, 신뢰성 원칙). Before planning or implementing any reservation/operation ticket, read this first.
- `docs/schema/reservation_server_schema.sql` — `reservation` 모듈의 대상 DB(PostgreSQL) 스키마. `reservations`(GIST exclusion으로 날짜 겹침 방지, optimistic lock), `reservation_requests`(요청 단위 멱등성 로그), `outbound_notifications`(operation 서버로의 Transactional Outbox).
- `docs/schema/operation_server_schema.sql` — `operation` 모듈의 대상 DB(MySQL 8+) 스키마. `hosts`, `ota_channels`, `rooms`, `room_channel_listings`, `inbound_events`(수신 멱등성), `outbox_events`(OTA/호스트로의 Transactional Outbox).
- This is a from-scratch rebuild against these docs (git history was reset). The two modules stay independently deployable Spring Boot apps communicating only over HTTP — no Gradle compile dependency between them (see `reservation/configuration/RestClientConfiguration.kt` for the existing HTTP client scaffolding to reuse).

## Agent Workflow
- **planner** subagent (`.claude/agents/planner.md`): analyzes GitHub issues and creates plan files in `plan/`.
- **developer** subagent (`.claude/agents/developer.md`): implements requirements from plan files and submits PRs.

## Skills
- **github-control**: All GitHub interactions (PRs, Issues, Projects) via `gh` CLI.
- **cmux-control**: CMUX terminal environment control and sidebar status/progress reporting.
- **kotlin-springboot**: Spring Boot and Kotlin development best practices.

## Workflow Conventions
- Always check the `plan/` directory for existing technical plans before starting development.
- Use `cmux-control` to report status via sidebar progress bars and logs for long-running tasks.
- All GitHub interactions MUST go through `github-control` skill workflows.

## Coding Conventions
- **Package layout**: every feature module (`reservation`, `operation`, ...) uses exactly 5 top-level packages — `controller`, `service`, `domain`, `persistent`, `configuration`. No `dto`, `repository`, `client`, `api`, or `config` packages.
- **Domain vs Entity**: `domain` holds plain Kotlin classes with no persistence annotations (the actual domain model, e.g. `Reservation`). JPA `@Entity` classes live in `persistent` alongside their `Repository`, suffixed `...Entity` (e.g. `ReservationEntity`), with `toDomain()`/`toEntity()` mapping functions. Domain code must never depend on `persistent`.
- **Kotest spec style**: use `FeatureSpec` (`feature { scenario { ... } }`) for every Kotest test class — not `StringSpec`, not a plain JUnit5 `@Test` class. Applies to unit tests, mapper tests, and `@SpringBootTest`/`@DataJpaTest` context tests alike. Full rationale/examples: `.claude/skills/kotlin-springboot/SKILL.md`.
