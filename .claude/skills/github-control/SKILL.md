---
name: github-control
description: Manage GitHub Pull Requests, reviews, issues, and Projects (v2) using the GitHub CLI (gh). Use this for creating PRs, performing code reviews, managing project boards/tickets, and general repository automation.
---

# GitHub Control

## Overview

This skill enables agents to interact with GitHub services through the `gh` CLI. It covers repository management, Pull
Request workflows, issue tracking, and advanced Project (v2) management.

## Authentication

Agents must ensure `gh` is authenticated. Run `gh auth status` to check. If needed, inform the user to run
`gh auth login`.

---

## Core Capabilities

### 1. Repository Management (`gh repo`)

- **Clone**: `gh repo clone <OWNER/REPO>`
- **Create**: `gh repo create [<NAME>]`
- **List**: `gh repo list [<OWNER>]`
- **View**: `gh repo view [<OWNER/REPO>]`

### 2. Pull Requests (`gh pr`)

- **Create**: `gh pr create --title <title> --body <body> [--draft]`
- **List**: `gh pr list [--state open|closed|merged|all]`
- **Status**: `gh pr status`
- **View/Diff**: `gh pr view <number>` / `gh pr diff <number>`
- **Review**: `gh pr review <number> --approve|--comment|--request-changes`
- **Merge**: `gh pr merge <number> [--merge|--squash|--rebase]`

### 3. Issues (`gh issue`)

- **Create**: `gh issue create --title <title> --body <body>`
- **List**: `gh issue list [--state open|closed|all]`
- **View**: `gh issue view <number>`
- **Comment**: `gh issue comment <number> --body <body>`

### 4. Projects (v2) (`gh project`)

- **List Projects**: `gh project list [--owner <org|user>]`
- **View Project**: `gh project view <number> [--owner <org|user>]`
- **List Items**: `gh project item-list <number> [--owner <org|user>]`
- **Add Item**: `gh project item-add <number> --owner <org|user> --url <url>`
- **Edit Item**: `gh project item-edit --id <id> --field <name> --value <value>`

### 5. Search (`gh search`)

- **Code**: `gh search code <query>`
- **Issues**: `gh search issues <query>`
- **PRs**: `gh search prs <query>`
- **Repos**: `gh search repos <query>`

### 6. Status (`gh status`)

- **Report**: `gh status` (Shows assigned issues, PRs, review requests, and notifications)

---

## Best Practices

- **JSON Output**: For programmatic data parsing, always append `--json <field1,field2,...>` (e.g.,
  `gh pr list --json number,title,author`).
- **Context Awareness**: Use `gh pr status` and `gh issue list` to understand the current repository state before taking
  action.
- **Handling Hyphenated Queries**: When searching with negative qualifiers (e.g., exclude label "bug"), use `--` to
  avoid flag parsing issues: `gh search issues -- "query -label:bug"`.
