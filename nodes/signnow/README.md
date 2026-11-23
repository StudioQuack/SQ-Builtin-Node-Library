# SignNow

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/signnow/`
- **Official API:** https://docs.signnow.com/
- **Auth secret:** `access_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded bytes appear on OUTPUT `file` as a platform file ref-id
(sq-file: / sq-ref:gridfs:). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `document_upload` | POST | `/document` |
| `invite_send` | POST | `/document/{document_id}/invite` |
| `document_get` | GET | `/document/{document_id}` |
| `document_download` | GET | `/document/{document_id}/download` |
| `template_create` | POST | `/template` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_token` (password; OAuth access token minted outside StudioQuack), `timeout_seconds`.

## Legal

- Terms: https://www.signnow.com/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Customer is the e-sign sender. US vs EU datacenter: use the regional API host. document_download writes OUTPUT `file`.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
