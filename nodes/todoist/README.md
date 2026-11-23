# Todoist

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/todoist/api_spec.json`.

## Overview

Official Todoist API as of 2026-08-20 uses https://api.todoist.com/api/v1 (unified REST; rest/v2 is the prior surface). Bearer personal token or OAuth access token.

- **Official API:** Todoist API v1
- **Docs:** https://developer.todoist.com/
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/todoist/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create` | POST | `https://api.todoist.com/api/v1/tasks` | Official |
| `get` | GET | `https://api.todoist.com/api/v1/tasks/{id}` | Official |
| `list` | GET | `https://api.todoist.com/api/v1/tasks` | Official |
| `update` | POST | `https://api.todoist.com/api/v1/tasks/{id}` | Official |
| `close` | POST | `https://api.todoist.com/api/v1/tasks/{id}/close` | Official |
| `reopen` | POST | `https://api.todoist.com/api/v1/tasks/{id}/reopen` | Official |
| `delete` | DELETE | `https://api.todoist.com/api/v1/tasks/{id}` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.| Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.todoist.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- `list` with `filter` (or `label` only) calls `GET /api/v1/tasks/filter?query=…` (v1 removed `filter` from `GET /tasks`).
- Singular INPUT `label` on create/update is promoted to `labels: [name]`. `priority` must be 1–4 when set (rejects coercion of invalid values).
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
