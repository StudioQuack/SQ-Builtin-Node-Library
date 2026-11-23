# Hive

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/hive/api_spec.json`.

## Overview

Official Hive HQ REST v2 (app.hive.com/api/v2) — not TheHive incident-response product. API access is often plan-gated.

- **Official API:** Hive HQ API v2
- **Docs:** https://developers.hive.com/reference/introduction
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/hive/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `action_create` | POST | `https://app.hive.com/api/v2/actions` | Official |
| `action_get` | GET | `https://app.hive.com/api/v2/actions/{action_id}` | Official |
| `action_list` | GET | `https://app.hive.com/api/v2/actions` | Official |
| `action_update` | PUT | `https://app.hive.com/api/v2/actions/{action_id}` | Official |
| `action_delete` | DELETE | `https://app.hive.com/api/v2/actions/{action_id}` | Official |
| `project_create` | POST | `https://app.hive.com/api/v2/projects` | Official |
| `project_get` | GET | `https://app.hive.com/api/v2/projects/{project_id}` | Official |
| `project_list` | GET | `https://app.hive.com/api/v2/projects` | Official |
| `project_delete` | DELETE | `https://app.hive.com/api/v2/projects/{project_id}` | Official |
| `message_create` | POST | `https://app.hive.com/api/v2/messages` | Official |
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

- Terms: https://hive.com/terms/
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
