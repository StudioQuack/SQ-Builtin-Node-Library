# Google Cloud Storage

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/google-cloud-storage/`
- **Official API:** https://cloud.google.com/storage/docs/json_api
- **Auth secret:** `access_token`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloads land on OUTPUT as type `file` (platform file ref-id). Never inline bytes. Mint the OAuth access token outside StudioQuack.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `bucket_create` | POST | `/b` |
| `bucket_delete` | DELETE | `/b/{bucket}` |
| `bucket_get` | GET | `/b/{bucket}` |
| `bucket_list` | GET | `/b` |
| `bucket_update` | PATCH | `/b/{bucket}` |
| `object_create` | POST | `/b/{bucket}/o` | JSON metadata (`name`, `contentType`, `metadata`). Media bytes need the upload host + raw body (not chained here). |
| `object_delete` | DELETE | `/b/{bucket}/o/{object}` |
| `object_get` | GET | `/b/{bucket}/o/{object}` |
| `object_list` | GET | `/b/{bucket}/o` |
| `object_update` | PATCH | `/b/{bucket}/o/{object}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. Errors raise a BPMN error; secrets are redacted. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_token` (password; minted outside StudioQuack), `project`, `bucket`, `timeout_seconds`.

## Legal

- Terms: https://cloud.google.com/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- Uploads: https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o. Downloads: alt=media. Uniform bucket-level access; CMEK if the customer requires it.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures raise a BPMN error. Never put `{error}` on the output port.
