# Wrike

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/wrike/api_spec.json`.

## Overview

Official Wrike API v4. OAuth app must be registered with Wrike.

- **Official API:** Wrike API v4
- **Datacenter:** config `datacenter` (default `www.wrike.com`; EU `app-eu.wrike.com`; US2 `app-us2.wrike.com`) — https://developers.wrike.com/docs/faq
- **Docs:** https://developers.wrike.com/api/v4/
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/wrike/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `folder_create` | POST | `https://{datacenter}/api/v4/folders/{folderId}/folders` | Official |
| `folder_delete` | DELETE | `https://{datacenter}/api/v4/folders/{folderId}` | Official |
| `task_create` | POST | `https://{datacenter}/api/v4/folders/{folderId}/tasks` | Official |
| `task_get` | GET | `https://{datacenter}/api/v4/tasks/{taskId}` | Official |
| `task_find` | GET | `https://{datacenter}/api/v4/tasks` | Official |
| `task_list_in_folder` | GET | `https://{datacenter}/api/v4/folders/{folderId}/tasks` | Official |
| `task_delete` | DELETE | `https://{datacenter}/api/v4/tasks/{taskId}` | Official |
| `task_update` | PUT | `https://{datacenter}/api/v4/tasks/{taskId}` | Official |
| `timelog_create` | POST | `https://{datacenter}/api/v4/tasks/{taskId}/timelogs` | Official |
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

- Terms: https://www.wrike.com/security/terms-of-service/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
