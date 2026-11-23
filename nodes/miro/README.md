# Miro

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/miro/api_spec.json`.

## Overview

Official Miro REST v2. App review for production OAuth. Board export may be restricted.

- **Official API:** Miro REST API
- **Docs:** https://developers.miro.com/reference/api-reference
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/miro/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `board_create` | POST | `https://api.miro.com/v2/boards` | Official |
| `board_get` | GET | `https://api.miro.com/v2/boards/{board_id}` | Official |
| `board_update` | PATCH | `https://api.miro.com/v2/boards/{board_id}` | Official |
| `board_copy` | PUT | `https://api.miro.com/v2/boards/{board_id}/copy` | Official |
| `board_list` | GET | `https://api.miro.com/v2/boards` | Official |
| `item_create_shape` | POST | `https://api.miro.com/v2/boards/{board_id}/shapes` | Official |
| `item_get` | GET | `https://api.miro.com/v2/boards/{board_id}/items/{item_id}` | Official |
| `item_list` | GET | `https://api.miro.com/v2/boards/{board_id}/items` | Official |
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

- Terms: https://miro.com/legal/terms-of-service/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
