# FigJam

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/figjam/api_spec.json`.

## Overview

Official Figma REST. Canvas writes are plugin/widget only — this node is file metadata + comments. webhook_listen is ingress (BPMN), not a socket.

- **Official API:** Figma REST API (FigJam files)
- **Docs:** https://www.figma.com/developers/api
- **Researched:** 2026-08-20
- **Auth:** `header` bound to configuration `personal_access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/figjam/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `file_get` | GET | `https://api.figma.com/v1/files/{file_key}` | Official |
| `comments_list` | GET | `https://api.figma.com/v1/files/{file_key}/comments` | Official |
| `comment_post` | POST | `https://api.figma.com/v1/files/{file_key}/comments` | Official |
| `comment_delete` | DELETE | `https://api.figma.com/v1/files/{file_key}/comments/{comment_id}` | Official |
| `project_files` | GET | `https://api.figma.com/v1/projects/{project_id}/files` | Official |
| `webhook_listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.figma.com/legal/developer-terms/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
