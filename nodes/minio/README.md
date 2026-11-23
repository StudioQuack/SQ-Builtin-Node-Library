# MinIO

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/minio/`
- **Official API:** https://min.io/docs/minio/linux/developers/minio-drivers.html
- **Auth secret:** `secret_key`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `bucket_create` | PUT | `/{bucket}` |
| `bucket_delete` | DELETE | `/{bucket}` |
| `bucket_list` | GET | `/` |
| `object_get` | GET | `/{bucket}/{key}` |
| `object_put` | PUT | `/{bucket}/{key}` | raw `file` bytes (S3 PutObject) |
| `object_delete` | DELETE | `/{bucket}/{key}` |
| `object_list` | GET | `/{bucket}` | query `list-type` (2), `prefix`, `delimiter`, `continuation-token`, `max-keys` |
| `presign` | GET | `/{bucket}/{key}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `access_key` / `secret_key` (password, SigV4), `endpoint`, `region`, `timeout_seconds`.

## Legal

- Terms: Customer-hosted — no vendor SaaS terms.
- Official-API-first. No scrape. No unofficial web wrapper.
- Customer-hosted S3 API. No vendor SaaS terms. Path-style vs virtual-host is an endpoint choice. Set `endpoint` to the real origin (scheme + host). Auth is AWS SigV4.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
