# Google Cloud Storage

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/google-cloud-storage/`
- **Researched:** 2026-08-20
- **Official API:** Cloud Storage JSON API **v1**
- **Docs:** [JSON API overview](https://cloud.google.com/storage/docs/json_api) · [v1 methods](https://cloud.google.com/storage/docs/json_api/v1) · [objects.insert](https://cloud.google.com/storage/docs/json_api/v1/objects/insert) · [objects.get](https://cloud.google.com/storage/docs/json_api/v1/objects/get) · [OAuth scopes](https://cloud.google.com/storage/docs/oauth-scopes) · [Uniform bucket-level access](https://cloud.google.com/storage/docs/using-uniform-bucket-level-access) · [Quotas](https://cloud.google.com/storage/quotas) · [Request rates](https://cloud.google.com/storage/docs/request-rate)
- **Auth:** Bearer `access_token` minted outside StudioQuack. **No Google Cloud client SDK.**

JSON metadata host: `https://storage.googleapis.com/storage/v1`. Uploads: `https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o`. Downloads of bytes: same objects.get URL with `alt=media`.

## Scope

One node for Google Cloud Storage. Catalog verbs map 1:1 onto JSON API **Buckets** and **Objects** methods (insert/get/list/patch/delete). Hierarchical-namespace **Folders** / **ManagedFolders** APIs are **not** catalog operations — do not build them here.

**Uniform bucket-level access:** when `iamConfiguration.uniformBucketLevelAccess.enabled` is true, ACL query params (`predefinedAcl`, object `acl[]`) fail with HTTP 400. Prefer IAM on the bucket; do not send ACL bodies.

**CMEK:** object insert may set `kmsKeyName` (Cloud KMS resource name). Customer-managed keys stay in the customer’s KMS project; this node only passes the name. CSEK (`X-Goog-Encryption-Key`) is optional and a secret — never log it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `bucket_create` | POST | `/b?project={project}` | Buckets.insert. JSON body `{ name, location, storageClass, iamConfiguration }`. |
| `bucket_delete` | DELETE | `/b/{bucket}` | Empty bucket only. |
| `bucket_get` | GET | `/b/{bucket}` | Bucket metadata. |
| `bucket_list` | GET | `/b?project={project}` | Buckets.list. |
| `bucket_update` | PATCH | `/b/{bucket}` | Partial metadata (prefer PATCH over PUT replace-all). |
| `object_create` | POST | `https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o` | objects.insert (`uploadType=media` or `multipart`). |
| `object_delete` | DELETE | `/b/{bucket}/o/{object}` | Path-encode object name. |
| `object_get` | GET | `/b/{bucket}/o/{object}` | Metadata (default) or bytes with `?alt=media`. |
| `object_list` | GET | `/b/{bucket}/o` | prefix / delimiter / pageToken. |
| `object_update` | PATCH | `/b/{bucket}/o/{object}` | Metadata only (not bytes). |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `object_list` | no | Chooses the row above |
| `access_token` | password | yes | | **yes** | OAuth access token minted outside StudioQuack |
| `bucket` | string | most ops | | no | Default bucket |
| `location` | string | no | | no | Default create location (`US`, `EU`, `us-central1`, …) |
| `uniform_bucket_level_access` | boolean | no | `true` | no | Set on `bucket_create` / `bucket_update` |
| `timeout_seconds` | number | no | `60` | no | Per-request |
| `max_retries` | number | no | `3` | no | 429/500/503 |

File payloads use type `file` (platform ref-id), never inline bytes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No secrets, no key JSON, no bytes |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = bucket_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | Globally unique name |
| `project_id` | string | yes | Query `project` |
| `location` | string | no | e.g. `US`, `europe-west1` |
| `storage_class` | string | no | `STANDARD`, `NEARLINE`, `COLDLINE`, `ARCHIVE` |
| `uniform_bucket_level_access` | boolean | no | `iamConfiguration.uniformBucketLevelAccess.enabled` |
| `versioning_enabled` | boolean | no | `versioning.enabled` |

### `operation = bucket_delete` / `bucket_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |

### `operation = bucket_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `project_id` | string | yes | |
| `prefix` | string | no | Bucket-name prefix |
| `page_token` | string | no | |
| `max_results` | number | no | Page size |

### `operation = bucket_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `storage_class` | string | no | |
| `uniform_bucket_level_access` | boolean | no | PATCH `iamConfiguration` |
| `versioning_enabled` | boolean | no | |
| `labels` | object | no | String map |
| `default_kms_key_name` | string | no | Bucket default CMEK (`encryption.defaultKmsKeyName`) |

### `operation = object_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `name` | string | yes | Object name (query `name` or multipart metadata) |
| `file` | **file** | yes | Platform ref-id; stream. Never base64 as stored type. |
| `content_type` | string | no | Companion MIME |
| `filename` | string | no | Companion only |
| `cache_control` | string | no | Metadata |
| `metadata` | object | no | Custom key/value (`metadata`) |
| `kms_key_name` | string | no | CMEK: `projects/…/locations/…/keyRings/…/cryptoKeys/…` |
| `if_generation_match` | string | no | `"0"` = create only if absent |
| `upload_type` | select (`media` / `multipart` / `resumable`) | no | `media` if no metadata; `multipart` if metadata; `resumable` if size ≥ 5 MiB or unknown |

Simple `uploadType=media`: `POST …/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}` with `Content-Type` of the file. Multipart: RFC 2387 related with JSON metadata part + media part. Resumable: initial POST `uploadType=resumable` then PUT session URI (follow `Location`; host may be `storage.googleapis.com`). Max object size **5 TiB**.

### `operation = object_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `name` | string | yes | Path-encode |
| `generation` | string | no | Delete a generation |

### `operation = object_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `name` | string | yes | |
| `alt` | select (`json` / `media`) | no | `json` = metadata; `media` = bytes → OUTPUT `file` |
| `generation` | string | no | |
| `range` | string | no | HTTP `Range` (media only) |

### `operation = object_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `prefix` | string | no | |
| `delimiter` | string | no | `/` for “folders” |
| `page_token` | string | no | |
| `max_results` | number | no | |
| `versions` | boolean | no | List generations |

### `operation = object_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `name` | string | yes | |
| `content_type` | string | no | |
| `metadata` | object | no | Replace custom metadata keys sent |
| `cache_control` | string | no | |
| `event_based_hold` | boolean | no | |
| `temporary_hold` | boolean | no | |

Does not rewrite object bytes. To replace data, use `object_create` (overwrite) with `ifGenerationMatch` as needed.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | object | failure | `{ code, message, http_status }` from JSON `error` |
| `bucket` | object | bucket get/create/update | Metadata (no IAM policy secrets) |
| `buckets` | list | `bucket_list` | `{ name, location, storageClass, timeCreated }[]` |
| `next_page_token` | string | list | |
| `object` | object | object create/update/get-json | `{ name, bucket, generation, size, contentType, md5Hash, crc32c, kmsKeyName, timeCreated }` |
| `items` | list | `object_list` | Object metadata rows |
| `prefixes` | list | list + delimiter | |
| `file` | **file** | `object_get` + `alt=media` | Platform ref-id |
| `filename` | string | media get | Companion |
| `mime_type` | string | media get | |
| `size` | number | media get | |

## Legal

- [Google Cloud Terms of Service](https://cloud.google.com/terms)
- [Google APIs Terms of Service](https://developers.google.com/terms)
- [Data Processing](https://cloud.google.com/terms/data-processing-addendum) (customer ↔ Google)
- Operator holds the GCP project and accepts Google terms. Object bytes leave the daemon to Google in the bucket location. PHI/health data requires an appropriate Google Cloud BAA/HIPAA configuration — not this node’s contract.
- **Residency:** choose `location` on create; this node does not move buckets (`buckets.relocate` is out of catalog).
- Official-API-first: JSON API v1 only (not XML API, not console).
- **Rates (published):** ~1000 object writes/s and ~5000 object reads/s initial per bucket (auto-scales; ramp if bursting). JSON batch payload &lt; 10 MiB. Object max **5 TiB**. Name/metadata size limits per quotas page.

## Implementation notes

- **JDK HTTP only.** Service account: RS256 JWT (`iss`=`client_email`, `scope`, `aud`=`https://oauth2.googleapis.com/token`, `exp` ≤ 1h) then `POST https://oauth2.googleapis.com/token` `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`. OAuth2: `refresh_token` grant to the same token endpoint. Header `Authorization: Bearer {access_token}`.
- **Encoding:** object names in the path must be percent-encoded (including `/` as `%2F`) per [Encoding URI path parts](https://cloud.google.com/storage/docs/request-endpoints#encoding).
- **Streaming:** `file` ref → stream for insert; `alt=media` response → file store. Never buffer 5 TiB in heap; use resumable for large/unknown size.
- **Retries:** honor `Retry-After`; backoff on 429/500/502/503. GCS recommends truncated exponential backoff. Retry resumable PUTs from the last committed offset (query status with `Content-Range: bytes */*`).
- **Timeouts:** connect ≤ 10s; read = `timeout_seconds` per chunk on resumable.
- **Errors:** JSON `{ error: { code, message, status } }` → OUTPUT `{ error }`. Never throw past dispatch. Never echo `private_key`.
- **Uniform ACL:** if insert/update includes `predefinedAcl` and the bucket is uniform, map 400 to a clear error: disable ACL fields.
- **CMEK:** pass `kmsKeyName` only; do not call Cloud KMS except GCS’s own use of the key. CSEK headers are secrets.
- **DO NOT BUILD:** XML API dual-stack as a second dialect; Folders API; storing SA JSON on INPUT; Google client libraries.
