# Microsoft To Do

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/microsoft-to-do/api_spec.json`.

## Overview

Official Graph To Do v1.0. Tasks.Read / Tasks.ReadWrite. App-only uses /users/{id} — this spec uses /me (delegated).

- **Official API:** Microsoft Graph To Do
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/todo-overview?view=graph-rest-1.0
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/microsoft-to-do/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `task_create` | POST | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks` | Official |
| `task_get` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}` | Official |
| `task_list` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks` | Official |
| `task_update` | PATCH | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}` | Official |
| `task_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}` | Official |
| `list_create` | POST | `https://graph.microsoft.com/v1.0/me/todo/lists` | Official |
| `list_get` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}` | Official |
| `list_list` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists` | Official |
| `list_update` | PATCH | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}` | Official |
| `list_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}` | Official |
| `linked_resource_create` | POST | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}/linkedResources` | Official |
| `linked_resource_get` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}/linkedResources/{linked_id}` | Official |
| `linked_resource_list` | GET | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}/linkedResources` | Official |
| `linked_resource_update` | PATCH | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}/linkedResources/{linked_id}` | Official |
| `linked_resource_delete` | DELETE | `https://graph.microsoft.com/v1.0/me/todo/lists/{list_id}/tasks/{task_id}/linkedResources/{linked_id}` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
