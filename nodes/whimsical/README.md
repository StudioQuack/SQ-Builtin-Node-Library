# Whimsical

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/whimsical/api_spec.json`.

## Overview

Official Whimsical API is limited beta, read-only, OAuth 2.1, support approval required. Catalog card_create/card_update are not on the published surface — omitted. Endpoints are RPC-style POST.

- **Official API:** Whimsical API (beta, read-only)
- **Docs:** https://whimsical.com/learn/integrations/api
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/whimsical/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `board_get` | POST | `https://whimsical.com/api/v1/files.get` | Official |
| `board_list` | POST | `https://whimsical.com/api/v1/files.list` | Official |
| `users_get` | POST | `https://whimsical.com/api/v1/users.get` | Official |
| `teams_list` | POST | `https://whimsical.com/api/v1/teams.list` | Official |
| `comments_list` | POST | `https://whimsical.com/api/v1/comments.list` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://whimsical.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
