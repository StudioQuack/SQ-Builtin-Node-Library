# Cloudflare R2

S3-compatible R2 API via `OfficialApiNode` + `api_spec.json`.

Provider docs: [S3 API compatibility](https://developers.cloudflare.com/r2/api/s3/api/)

Auth: AWS SigV4 (`access_key_id` + `secret_access_key`). Host `https://{account_id}.{jurisdiction}` (default `r2.cloudflarestorage.com`).

`object_put` sends INPUT (or config) `file` as the raw PutObject body and optional official headers: Content-Type, Cache-Control, Content-Disposition, Content-Encoding, Content-Language, Expires, Content-MD5, x-amz-storage-class, If-Match, If-None-Match.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `bucket_create` | PUT | `/{bucket}` | CreateBucket |
| `bucket_list` | GET | `/` | ListBuckets |
| `object_put` | PUT | `/{bucket}/{key}` | PutObject; body is `file` |
| `object_get` | GET | `/{bucket}/{key}` | GetObject; optional Range / versionId |
| `object_delete` | DELETE | `/{bucket}/{key}` | DeleteObject |
| `object_list` | GET | `/{bucket}` | ListObjectsV2 (`list-type=2`) |
| `presign` | GET | `/{bucket}/{key}` | Not a real presigned URL (engine cannot mint SigV4 query auth) |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields including `file` for uploads. No secrets. |
| `output` | OUTPUT | `{status, http_status, data, file?}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Configuration

`operation` (select), `access_key_id` / `secret_access_key` (password, SigV4), `account_id`, `jurisdiction`, `region` (default `auto`), `file` (fallback PutObject body), `timeout_seconds`.

## Legal

Terms: https://www.cloudflare.com/service-specific-terms-developer-platform/
Official S3-compatible API only. Never log file bytes or secrets.
