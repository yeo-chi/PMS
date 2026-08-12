# PMS Project Configuration

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
