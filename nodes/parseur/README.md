# Parseur

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/parseur/`
- **Official API:** https://developer.parseur.com/
- **Auth secret:** `api_token`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `mailbox_create` | POST | `/parser` |
| `mailbox_get` | GET | `/parser/{id}` |
| `mailbox_list` | GET | `/parser` |
| `mailbox_delete` | DELETE | `/parser/{id}` |
| `document_get` | GET | `/document/{id}` |
| `document_delete` | DELETE | `/document/{id}` |
| `document_reprocess` | POST | `/document/{id}/process` |
| `document_search` | GET | `/document` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | `operation`, `id`, `name`, `search`, pagination. AI Agent tools: map DuckTape `request` fields to these shortCodes (not only `operation`). Nested `{ "input": { ... } }` payloads are flattened by the handler. Aliases: `parser_id` / `mailbox_id` / `document_id` → `id`. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `timeout_seconds`.

## Legal

- Terms: https://parseur.com/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Email/PDF extraction. Data residency is in the vendor cloud. Auth: Authorization header (legacy `Token ` prefix still accepted).
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
