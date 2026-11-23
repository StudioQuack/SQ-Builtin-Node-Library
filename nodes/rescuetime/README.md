# RescueTime

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/rescuetime/api_spec.json`.

## Overview

Official RescueTime analytic API. Recommended auth is `Authorization: Bearer` (https://help.rescuetime.com/article/464-getting-your-api-key). Personal productivity / PII — GDPR minimize; never log raw timelines.

- **Official API:** RescueTime Developers API
- **Docs:** https://help.rescuetime.com/article/464-getting-your-api-key
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `api_key` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/rescuetime/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `analytic_data` | GET | `https://www.rescuetime.com/anapi/data` | Official |
| `daily_summary` | GET | `https://www.rescuetime.com/anapi/daily_summary_feed` | Official |
| `alerts_list` | GET | `https://www.rescuetime.com/anapi/alerts_feed` | Official |
| `highlights_list` | GET | `https://www.rescuetime.com/anapi/highlights_feed` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.rescuetime.com/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
