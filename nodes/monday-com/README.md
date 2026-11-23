# Monday.com

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/monday-com/api_spec.json`.

## Overview

Official monday.com GraphQL (POST api.monday.com/v2). Each operation sends a GraphQL query/mutation in body field query (and optional variables). Marketplace ToS if an OAuth app is published. extraHeaders pin `API-Version` from config `api_version` (default `2026-07`, Current until 2026-10-01; then `2026-10`). https://developer.monday.com/api-reference/docs/api-versioning

- **Official API:** monday.com GraphQL API
- **Docs:** https://developer.monday.com/api-reference/docs/api-versioning
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/monday-com/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `board_create` | POST | `https://api.monday.com/v2` | Official |
| `board_get` | POST | `https://api.monday.com/v2` | Official |
| `board_list` | POST | `https://api.monday.com/v2` | Official |
| `board_archive` | POST | `https://api.monday.com/v2` | Official |
| `column_create` | POST | `https://api.monday.com/v2` | Official |
| `column_list` | POST | `https://api.monday.com/v2` | Official |
| `group_create` | POST | `https://api.monday.com/v2` | Official |
| `group_list` | POST | `https://api.monday.com/v2` | Official |
| `group_delete` | POST | `https://api.monday.com/v2` | Official |
| `item_create` | POST | `https://api.monday.com/v2` | Official |
| `item_get` | POST | `https://api.monday.com/v2` | Official |
| `item_list` | POST | `https://api.monday.com/v2` | Official |
| `item_delete` | POST | `https://api.monday.com/v2` | Official |
| `item_by_column` | POST | `https://api.monday.com/v2` | Official |
| `column_change` | POST | `https://api.monday.com/v2` | Official |
| `columns_change` | POST | `https://api.monday.com/v2` | Official |
| `item_move` | POST | `https://api.monday.com/v2` | Official |
| `item_update_add` | POST | `https://api.monday.com/v2` | Official |
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

- Terms: https://monday.com/l/legal/tos/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
