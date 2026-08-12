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
