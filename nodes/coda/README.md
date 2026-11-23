# Coda

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/coda/api_spec.json`.

## Overview

Official Coda API v1. Tokens are user-scoped.

- **Official API:** Coda API v1
- **Docs:** https://coda.io/developers/apis/v1
- **Researched:** 2026-08-20
- **Auth:** `bearer` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/coda/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `row_insert` | POST | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows` | Official |
| `row_get` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows/{row_id}` | Official |
| `row_list` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows` | Official |
| `row_delete` | DELETE | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows/{row_id}` | Official |
| `row_update` | PUT | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows/{row_id}` | Official |
| `column_get` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/columns/{column_id}` | Official |
| `column_list` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/columns` | Official |
| `control_get` | GET | `https://coda.io/apis/v1/docs/{doc_id}/controls/{control_id}` | Official |
| `formula_get` | GET | `https://coda.io/apis/v1/docs/{doc_id}/formulas/{formula_id}` | Official |
| `view_get` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{view_id}` | Official |
| `view_list` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables` | Official |
| `view_rows` | GET | `https://coda.io/apis/v1/docs/{doc_id}/tables/{view_id}/rows` | Official |
| `push_button` | POST | `https://coda.io/apis/v1/docs/{doc_id}/tables/{table_id}/rows/{row_id}/buttons/{column_id}` | Official |
| `push_view_button` | POST | `https://coda.io/apis/v1/docs/{doc_id}/tables/{view_id}/rows/{row_id}/buttons/{column_id}` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://coda.io/trust/terms
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Extra provider headers (Notion-Version, Harvest-Account-Id, Basecamp User-Agent) are documented engine limits — do not add Java in this task.
