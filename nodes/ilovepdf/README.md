# iLovePDF

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/ilovepdf/`
- **Official API:** https://developer.ilovepdf.com/docs/api-reference
- **Auth secret:** `public_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Download/convert results use type `file` on **OUTPUT** (Platform file ref-id
(sq-file: / sq-ref:gridfs:). Never inline bytes or base64 as the stored type.).
Auth `public_key` is a Bearer JWT minted outside StudioQuack.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `merge` | GET | `/start/merge` |
| `split` | GET | `/start/split` |
| `compress` | GET | `/start/compress` |
| `pdfjpg` | GET | `/start/pdfjpg` |
| `imagepdf` | GET | `/start/imagepdf` |
| `unlock` | GET | `/start/unlock` |
| `protect` | GET | `/start/protect` |
| `rotate` | GET | `/start/rotate` |
| `repair` | GET | `/start/repair` |
| `watermark` | GET | `/start/watermark` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `public_key` (password — Bearer JWT minted outside StudioQuack), `timeout_seconds`.

## Legal

- Terms: https://www.ilovepdf.com/legal/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Flow: GET /v1/start/{tool} → upload → process → download. Auth is JWT Bearer from POST /v1/auth (or a self-signed JWT). Files go to iLovePDF cloud. ToS + retention; DPA for PHI.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never throw past dispatch.
