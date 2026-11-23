# PandaDoc

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/pandadoc/`
- **Official API:** https://developers.pandadoc.com/
- **Auth secret:** `api_key`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `document_create_from_template` | POST | `/documents` |
| `document_create_from_file` | POST | `/documents` |
| `document_send` | POST | `/documents/{id}/send` |
| `document_get` | GET | `/documents/{id}` |
| `document_list` | GET | `/documents` |
| `document_download` | GET | `/documents/{id}/download` |
| `document_delete` | DELETE | `/documents/{id}` |
| `template_list` | GET | `/templates` |
| `session_create` | POST | `/documents/{id}/session` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `timeout_seconds`.

## Legal

- Terms: https://www.pandadoc.com/terms-of-use/
- Official-API-first. No scrape. No unofficial web wrapper.
- Templates are this node, not a second node. Rate limits per action. Customer is the e-sign sender.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
