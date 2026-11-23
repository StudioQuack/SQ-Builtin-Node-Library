# Wasabi

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/wasabi/`
- **Official API:** https://docs.wasabi.com/docs/wasabi-api
- **Auth secret:** `secret_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Auth is AWS SigV4 (`access_key` + `secret_key`). Host is `https://s3.{region}.wasabisys.com`.
OUTPUT `file` is a platform file ref-id (`sq-file:` / `sq-ref:gridfs:`). Never inline bytes or base64.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `bucket_create` | PUT | `/{bucket}` |
| `bucket_list` | GET | `/` |
| `object_put` | PUT | `/{bucket}/{key}` |
| `object_get` | GET | `/{bucket}/{key}` |
| `object_delete` | DELETE | `/{bucket}/{key}` |
| `object_list` | GET | `/{bucket}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields (`bucket`, `key`, list filters). No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_key` (password), `secret_key` (password), `region` (host + SigV4), `timeout_seconds`.

## Legal

- Terms: https://wasabi.com/legal/terms-of-service
- Official-API-first. No scrape. No unofficial web wrapper.
- S3-compatible. Use the region host (s3.<region>.wasabisys.com); generic s3.wasabisys.com may allow GET but not PUT/DELETE. Do not claim unexpected egress pricing in product copy.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.

## Object upload

`object_put` is S3 PUT /{bucket}/{key} with `bodyFormat: raw` and `bodyParam: file` (same pattern as amazon-s3 file_upload). Docs: https://docs.wasabi.com/docs/wasabi-api
