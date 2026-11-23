# Google Tasks

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/google-tasks/api_spec.json`.

## Overview

Official Tasks API v1. No first-party webhooks; listen is ingress-only (BPMN message event).

- **Official API:** Google Tasks API v1
- **Docs:** https://developers.google.com/workspace/tasks/reference/rest
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/google-tasks/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `add_task` | POST | `https://tasks.googleapis.com/tasks/v1/lists/{tasklist}/tasks` | Official |
| `get_task` | GET | `https://tasks.googleapis.com/tasks/v1/lists/{tasklist}/tasks/{task}` | Official |
| `list_tasks` | GET | `https://tasks.googleapis.com/tasks/v1/lists/{tasklist}/tasks` | Official |
| `update_task` | PATCH | `https://tasks.googleapis.com/tasks/v1/lists/{tasklist}/tasks/{task}` | Official |
| `delete_task` | DELETE | `https://tasks.googleapis.com/tasks/v1/lists/{tasklist}/tasks/{task}` | Official |
| `list_tasklists` | GET | `https://tasks.googleapis.com/tasks/v1/users/@me/lists` | Official |
| `create_tasklist` | POST | `https://tasks.googleapis.com/tasks/v1/users/@me/lists` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://developers.google.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
