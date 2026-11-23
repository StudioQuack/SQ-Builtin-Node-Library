# Clockify

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/clockify/api_spec.json`.

## Overview

Official Clockify API. Header X-Api-Key. API key is user-scoped; workspace admin for project writes.

- **Official API:** Clockify API
- **Region:** config `region_host` default `api.clockify.me`. EU Germany `euc1.clockify.me`; USA `use2.clockify.me`; UK `euw2.clockify.me`; AU `apse2.clockify.me` (https://docs.clockify.me/ · https://clockify.me/help/getting-started/data-regions).
- **Docs:** https://docs.clockify.me/
- **Researched:** 2026-08-22
- **Auth:** `header` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/clockify/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_create` | POST | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects` | Official |
| `project_get` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}` | Official |
| `project_list` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects` | Official |
| `project_update` | PUT | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}` | Official |
| `project_delete` | DELETE | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}` | Official |
| `tag_create` | POST | `https://{region_host}/api/v1/workspaces/{workspace_id}/tags` | Official |
| `tag_list` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/tags` | Official |
| `tag_update` | PUT | `https://{region_host}/api/v1/workspaces/{workspace_id}/tags/{tag_id}` | Official |
| `tag_delete` | DELETE | `https://{region_host}/api/v1/workspaces/{workspace_id}/tags/{tag_id}` | Official |
| `task_create` | POST | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}/tasks` | Official |
| `task_get` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}/tasks/{task_id}` | Official |
| `task_list` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}/tasks` | Official |
| `task_update` | PUT | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}/tasks/{task_id}` | Official |
| `task_delete` | DELETE | `https://{region_host}/api/v1/workspaces/{workspace_id}/projects/{project_id}/tasks/{task_id}` | Official |
| `time_entry_create` | POST | `https://{region_host}/api/v1/workspaces/{workspace_id}/time-entries` | Official |
| `time_entry_get` | GET | `https://{region_host}/api/v1/workspaces/{workspace_id}/time-entries/{id}` | Official |
| `time_entry_update` | PUT | `https://{region_host}/api/v1/workspaces/{workspace_id}/time-entries/{id}` | Official |
| `time_entry_delete` | DELETE | `https://{region_host}/api/v1/workspaces/{workspace_id}/time-entries/{id}` | Official |
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

- Terms: https://clockify.me/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
