# Nuclino

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/nuclino/api_spec.json`.

## Overview

Official Nuclino REST (api.nuclino.com/v0). Team-scoped API key in `Authorization` with no Bearer prefix.

- **Official API:** Nuclino API
- **Docs:** https://help.nuclino.com/d3a29686-api
- **Researched:** 2026-08-22
- **Auth:** `header` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/nuclino/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `item_create` | POST | `https://api.nuclino.com/v0/items` | Official |
| `item_get` | GET | `https://api.nuclino.com/v0/items/{itemId}` | Official |
| `item_update` | PUT | `https://api.nuclino.com/v0/items/{itemId}` | Official |
| `item_list` | GET | `https://api.nuclino.com/v0/items` | Official |
| `item_search` | GET | `https://api.nuclino.com/v0/items` | Official |
| `collection_list` | GET | `https://api.nuclino.com/v0/workspaces` | Official |
| `user_list` | GET | `https://api.nuclino.com/v0/users` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.nuclino.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
