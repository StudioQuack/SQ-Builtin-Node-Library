# Toggl

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/toggl/api_spec.json`.

## Overview

Official Toggl Track API v9. Basic auth: username is the API token, password is the literal 'api_token'.

- **Official API:** Toggl Track API v9
- **Docs:** https://engineering.toggl.com/docs/track/api/time_entries/
- **Researched:** 2026-08-22
- **Auth:** `basic` bound to configuration `api_token_password` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/toggl/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `time_entry_create` | POST | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries` | Official — JSON body must include `created_with` |
| `time_entry_get` | GET | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries/{time_entry_id}` | Official |
| `time_entry_update` | PUT | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries/{time_entry_id}` | Official |
| `time_entry_delete` | DELETE | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries/{time_entry_id}` | Official |
| `time_entry_start` | POST | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries` | Official |
| `time_entry_stop` | PATCH | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/time_entries/{time_entry_id}/stop` | Official |
| `project_list` | GET | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/projects` | Official |
| `client_list` | GET | `https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/clients` | Official |
| `workspace_list` | GET | `https://api.track.toggl.com/api/v9/workspaces` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://toggl.com/legal/terms/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
