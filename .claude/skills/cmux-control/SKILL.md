---
name: cmux-control
description: Control and interact with CMUX (advanced terminal multiplexer with Unix socket API), including window/pane management, status & progress metadata updates for sidebar/logs, and headful/headless browser control.
---

# CMUX Control

## Overview
This skill enables agents to interact with and control the CMUX environment using its Unix socket API command-line interface (`cmux`). CMUX is an advanced terminal multiplexer featuring customizable sidebars (status, progress, logging), workspace/pane layouts, and a built-in interactive browser automation engine.

## Environment Variables & Execution Context
- **CMUX_WORKSPACE_ID**: Default workspace ID. Auto-set in CMUX terminals.
- **CMUX_SURFACE_ID**: Default surface ID. Auto-set in CMUX terminals.
- **CMUX_SOCKET_PATH**: Overrides the Unix socket path. Defaults to `~/Library/Application Support/cmux/cmux.sock`.

All `cmux` commands should run directly in the shell.

---

## Core Capabilities

### 1. Workspace & Window Management
Manage workspaces, windows, panes, and surfaces:
- **List/Identify**:
  - `cmux list-windows` / `cmux list-workspaces` / `cmux list-panes`
  - `cmux identify [--workspace <id|ref>] [--surface <id|ref>]`
  - `cmux tree [--all]` (Displays layout hierarchy of windows, panes, and surfaces)
- **Creation & Navigation**:
  - `cmux new-window`
  - `cmux new-workspace [--cwd <path>] [--command <text>]`
  - `cmux new-split <left|right|up|down> [--workspace <id>] [--surface <id>]`
  - `cmux focus-pane --pane <id|ref>`
- **Renaming**:
  - `cmux rename-workspace [--workspace <id>] <title>`
  - `cmux rename-window [--workspace <id>] <title>`

### 2. Screen & Input Interaction
Read terminal buffers and send user inputs/commands:
- **Reading Output**:
  - `cmux read-screen [--workspace <id>] [--surface <id>] [--scrollback] [--lines <n>]`
  - `cmux capture-pane [--workspace <id>] [--surface <id>] [--scrollback] [--lines <n>]`
- **Sending Inputs**:
  - `cmux send [--workspace <id>] [--surface <id>] <text>` (Sends raw text command/keystrokes)
  - `cmux send-key [--workspace <id>] [--surface <id>] <key>` (Sends special key, e.g., `C-c`, `Enter`)

### 3. Sidebar Metadata (Agent Status Reporting)
Use these commands to give visual feedback to the user on progress, state, and logs inside the CMUX sidebar:
- **Status Key-Values**:
  - `cmux set-status <key> <value> [--icon <name>] [--color <#hex>] [--workspace <id>]`
    - *Example*: `cmux set-status "Agent Status" "Running Tests" --icon "play" --color "#34C759"`
  - `cmux clear-status <key> [--workspace <id>]`
- **Progress bar**:
  - `cmux set-progress <0.0-1.0> [--label <text>] [--workspace <id>]`
    - *Example*: `cmux set-progress 0.45 --label "Refactoring Code"`
  - `cmux clear-progress [--workspace <id>]`
- **Logging**:
  - `cmux log [--level <info|warn|error>] [--source <name>] "--" <message>`
    - *Example*: `cmux log --level info --source "agent" -- "Found 3 issues"`
  - `cmux clear-log [--workspace <id>]`

### 4. Interactive Browser Automation
CMUX contains a high-performance, built-in browser engine for automated web interaction:
- **Open & Navigate**:
  - `cmux browser open [url]` (Creates a browser surface split)
  - `cmux browser goto <url>` (Navigates browser to URL)
- **Retrieve Content**:
  - `cmux browser snapshot [--interactive] [--compact]` (Gets DOM representation or accessibility tree)
  - `cmux browser screenshot [--out <path>]` (Takes a screenshot)
  - `cmux browser get <url|title|text|html|value|attr|count>` (Extracts data)
- **User Interactions**:
  - `cmux browser click <selector>`
  - `cmux browser type <selector> <text>`
  - `cmux browser fill <selector> [text]`
  - `cmux browser scroll [--dx <n>] [--dy <n>]`
- **Waiting**:
  - `cmux browser wait [--selector <css>] [--text <text>] [--timeout-ms <ms>]`

---

## Standard Workflows & Best Practices

### Agent Status Communication (Strict Rule)
Whenever performing long-running commands, tests, or multi-step tasks within a CMUX session:
1. Initialize progress: `cmux set-progress 0.0 --label "Starting <Task Name>"`
2. Log key milestones: `cmux log --level info --source "agent" -- "Initiating phase X..."`
3. Update progress incrementally (e.g. `0.25`, `0.50`, `0.75`) with appropriate descriptive labels.
4. Set status indicator: `cmux set-status "Mode" "Working" --icon "gear" --color "#FF9500"`
5. Upon successful completion, set status to green (e.g. `cmux set-status "Mode" "Idle" --icon "check" --color "#34C759"`) and clear the progress bar: `cmux clear-progress`.

### Interactive Web Research
When instructed to search or research a website using CMUX browser:
1. Launch/navigate: `cmux browser open "https://example.com"`
2. Wait for loading: `cmux browser wait --load-state complete`
3. Take snapshot: `cmux browser snapshot` to understand DOM elements.
4. Interact: use `click` and `fill`/`type` to navigate.
5. Extract relevant content via `browser get text` or similar.
