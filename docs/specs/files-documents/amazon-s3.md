# Amazon S3

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/amazon-s3/`
- **Researched:** 2026-08-20
- **Official API:** Amazon S3 REST API version `2006-03-01` (current REST surface; XML namespace `http://s3.amazonaws.com/doc/2006-03-01/`)
- **Docs:** [API Welcome](https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html) · [PutObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html) · [GetObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html) · [ListObjectsV2](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html) · [CreateBucket](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucket.html) · [PutBucketNotificationConfiguration](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutBucketNotificationConfiguration.html) · [SigV4](https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html) · [Quotas](https://docs.aws.amazon.com/general/latest/gr/s3.html) · [Multipart limits](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html)
- **Auth:** `access_keys` | `iam_role` — secrets: `secret_access_key`, `session_token` (when present). Signing: AWS Signature Version 4 (JDK `Mac` HmacSHA256 + `MessageDigest` SHA-256). **No AWS SDK.**

## Scope

One node for Amazon S3 object storage. Callers pick the verb with `operation`. Traffic is the official REST API over HTTPS using virtual-hosted-style URLs by default (`https://{bucket}.s3.{region}.amazonaws.com/{key}`). `us-east-1` may use `https://{bucket}.s3.amazonaws.com/{key}`. Path-style (`https://s3.{region}.amazonaws.com/{bucket}/{key}`) is opt-in for compatibility.

S3 has **no real folders**. `folder_*` maps to the prefix + delimiter convention: a 0-byte object whose key ends in `/`, plus `ListObjectsV2` with `delimiter=/`.

`listen` **cannot** long-poll S3. Event Notifications only publish to SNS, SQS, Lambda, or EventBridge. This node’s `listen` **subscribes** via `PutBucketNotificationConfiguration`. Inbound object events reach the workflow through the StudioQuack engine's message-event ingress after the customer points SNS HTTP(S) or EventBridge API Destination at that ingress. Do not scrape the AWS console. Directory buckets do **not** support this notification API.

There is no `SearchBuckets` / bucket-contents search RPC. Catalog `bucket_search` maps to `ListObjectsV2` with `prefix` (and optional `start-after`). S3 Select (`SelectObjectContent`) is a different API and is **out of this node**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `bucket_create` | PUT | `/{bucket}` (host `{bucket}.s3.{region}.amazonaws.com`) body `CreateBucketConfiguration` | Create a general-purpose bucket. `us-east-1` omits `LocationConstraint`. |
| `bucket_delete` | DELETE | `/{bucket}` | Delete an **empty** bucket. |
| `bucket_list` | GET | `/` on the regional service host `s3.{region}.amazonaws.com` | `ListBuckets`. |
| `bucket_search` | GET | `/?list-type=2&prefix=&start-after=&max-keys=` | Prefix filter of object keys in one bucket (not a dedicated search API). |
| `file_copy` | PUT | `/{destKey}` + header `x-amz-copy-source: /{srcBucket}/{srcKey}` | Server-side copy (`CopyObject`). URL-encode the source. |
| `file_delete` | DELETE | `/{key}` | `DeleteObject`. Optional `versionId`. |
| `file_download` | GET | `/{key}` | `GetObject`. Body streamed to a platform `file` ref. Optional `Range`. |
| `file_list` | GET | `/?list-type=2` | `ListObjectsV2` (not ListObjects v1). |
| `file_upload` | PUT or multipart | `/{key}` or `/?uploads` + `UploadPart` + `CompleteMultipartUpload` | `PutObject` ≤ 5 GiB; multipart required above 5 GiB (max object 48.8 TiB). |
| `folder_create` | PUT | `/{prefix}/` `Content-Length: 0` | 0-byte folder marker key ending `/`. |
| `folder_delete` | DELETE (+ optional POST `?delete`) | marker key and/or `DeleteObjects` under prefix | Delete marker; optional recursive delete of keys with that prefix (≤ 1000 keys per `DeleteObjects`). |
| `folder_list` | GET | `/?list-type=2&delimiter=/&prefix=` | Common prefixes as “folders”. |
| `listen` | PUT | `/?notification` | `PutBucketNotificationConfiguration` (replace-all). Does not poll. |

Retired / unofficial verbs (ListObjects v1, website hosting, console scrape) are omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `file_list` | no | Chooses the row above |
| `access_key_id` | string | if `access_keys` | | no | `AWSAccessKeyId` |
| `secret_access_key` | password | if `access_keys` | | **yes** | Secret key for SigV4 |
| `session_token` | password | if STS/temp | | **yes** | `x-amz-security-token` |
| `region` | string | yes | | no | Signing + endpoint region (`us-east-1`, …) |
| `iam_role_arn` | string | if `iam_role` | | no | `sts:AssumeRole` target. Daemon host must be able to assume it (instance profile / IRSA / supplied base keys). |
| `sts_external_id` | password | no | | **yes** | Optional `ExternalId` for AssumeRole |
| `endpoint` | string | no | regional AWS | no | Override host (VPC endpoint / dual-stack). HTTPS only. |
| `addressing_style` | select (`virtual_hosted` / `path`) | no | `virtual_hosted` | no | URL layout |
| `bucket` | string | most object ops | | no | Default bucket; INPUT may override |
| `force_multipart_bytes` | number | no | `104857600` (100 MiB) | no | Start multipart at this size; always multipart if size > 5 GiB |
| `presign_expires_seconds` | number | no | `900` | no | Used only if a future catalog adds `presign`. Max SigV4 query auth: **604800** (7 days). |
| `timeout_seconds` | number | no | `60` | no | Per-request read timeout. Large GET/PUT should raise this. |
| `max_retries` | number | no | `3` | no | Retries on 408/429/500/502/503/504 + S3 `InternalError`/`SlowDown` |

File payloads use type `file` (platform ref-id), never inline bytes, on this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets, signatures, or file bytes |

FUNCTION ports: none.

## Input fields (by operation)

Shared (when the op needs them): `bucket` (string), `key` (string, object key), `prefix` (string), `region` override (string).

### `operation = bucket_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | DNS-compliant bucket name |
| `location_constraint` | string | if not `us-east-1` | XML `LocationConstraint` (usually equals `region`) |
| `object_ownership` | string | no | Header `x-amz-object-ownership` (`BucketOwnerEnforced` recommended; disables ACLs) |
| `acl` | string | no | `x-amz-acl` — omit when Object Ownership is `BucketOwnerEnforced` |

### `operation = bucket_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | Must be empty (no objects / no incomplete multipart) |

### `operation = bucket_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Lists buckets for the signed account |

### `operation = bucket_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | Bucket to list |
| `prefix` | string | no | Key prefix filter (this is the “search”) |
| `start_after` | string | no | `start-after` |
| `max_keys` | number | no | 1–1000 (S3 max per page) |
| `continuation_token` | string | no | Next page |

### `operation = file_copy`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `source_bucket` | string | yes | Source bucket |
| `source_key` | string | yes | Source key (encode `/` and special chars in `x-amz-copy-source`) |
| `destination_bucket` | string | yes | Dest bucket (may equal source) |
| `destination_key` | string | yes | Dest key |
| `metadata_directive` | string | no | `COPY` (default) or `REPLACE` |

Copy of objects > 5 GiB must use multipart copy (`UploadPartCopy`) — same size rule as upload.

### `operation = file_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `key` | string | yes | |
| `version_id` | string | no | Versioned delete |

### `operation = file_download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `key` | string | yes | |
| `version_id` | string | no | |
| `range` | string | no | HTTP `Range` (e.g. `bytes=0-1048575`) |

### `operation = file_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `prefix` | string | no | |
| `delimiter` | string | no | e.g. `/` |
| `max_keys` | number | no | 1–1000 |
| `continuation_token` | string | no | |
| `fetch_owner` | boolean | no | `fetch-owner=true` |

### `operation = file_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `key` | string | yes | Object key |
| `file` | **file** | yes | Platform ref-id; stream bytes. Never base64 in the graph. |
| `content_type` | string | no | `Content-Type` companion |
| `content_disposition` | string | no | Companion |
| `filename` | string | no | Companion only; does not replace `key` |
| `server_side_encryption` | string | no | `AES256` or `aws:kms` (`x-amz-server-side-encryption`) |
| `sse_kms_key_id` | string | if KMS | `x-amz-server-side-encryption-aws-kms-key-id` |
| `storage_class` | string | no | `x-amz-storage-class` |

Multipart (handler-internal, not extra operations): `POST /{key}?uploads` → `PUT /{key}?partNumber=&uploadId=` → `POST /{key}?uploadId=` with `<CompleteMultipartUpload>` XML. Part size 5 MiB–5 GiB (last part may be smaller). Max 10_000 parts. Abort on failure: `DELETE /{key}?uploadId=`.

### `operation = folder_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `folder_key` | string | yes | Prefix; handler ensures trailing `/`. PUT 0-byte object. |

### `operation = folder_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `folder_key` | string | yes | Prefix ending `/` |
| `recursive` | boolean | no | default `false`: delete only the 0-byte marker. `true`: list+`DeleteObjects` all keys under prefix (paginate until empty). |

### `operation = folder_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | |
| `prefix` | string | no | Parent prefix (include trailing `/`) |
| `max_keys` | number | no | 1–1000 |
| `continuation_token` | string | no | |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bucket` | string | yes | General-purpose bucket only (not directory buckets) |
| `listen_action` | select (`put_config` / `get_config` / `clear_config`) | yes | Write, read, or empty `<NotificationConfiguration/>` |
| `sns_topic_arn` | string | if SNS dest | `TopicConfiguration.Topic`. Customer SNS topic with HTTPS subscription to StudioQuack ingress. |
| `sqs_queue_arn` | string | if SQS dest | `QueueConfiguration.Queue` — daemon does **not** long-poll SQS unless a future catalog says so; prefer SNS/EventBridge → HTTP. |
| `enable_eventbridge` | boolean | no | Include empty `<EventBridgeConfiguration/>` |
| `events` | list of string | if putting dest | e.g. `s3:ObjectCreated:*`, `s3:ObjectRemoved:*` |
| `filter_prefix` / `filter_suffix` | string | no | `S3Key` filter rules |
| `notification_id` | string | no | Configuration `Id` |
| `skip_destination_validation` | boolean | no | Header `x-amz-skip-destination-validation` |

The node does **not** receive S3 events itself. After `put_config`, the BPMN message start/catch is bound to webhook ingress. Customer owns SNS/EventBridge wiring and bucket policy (`s3:PutBucketNotification`).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | object | failure | `{ code, message, http_status, request_id }` — no secrets |
| `buckets` | list | `bucket_list` | `{ name, creation_date }[]` |
| `bucket` | string | create/delete | Echo |
| `location` | string | `bucket_create` | `Location` header |
| `keys` | list | list/search | `{ key, size, etag, last_modified, storage_class }[]` |
| `common_prefixes` | list | `folder_list` / list with delimiter | Prefix strings |
| `is_truncated` | boolean | list ops | |
| `next_continuation_token` | string | list ops | Pagination |
| `etag` | string | upload/copy | Object ETag |
| `version_id` | string | upload/copy/delete | When versioning on |
| `file` | **file** | `file_download` | Platform ref-id of stored object bytes |
| `filename` | string | `file_download` | Companion from key / `Content-Disposition` |
| `mime_type` | string | `file_download` | `Content-Type` |
| `size` | number | `file_download` | Bytes |
| `copy_source_version_id` | string | `file_copy` | |
| `deleted` | boolean | delete ops | |
| `deleted_keys` | list | recursive folder delete | Keys removed this invocation (may need another page) |
| `notification` | object | `listen` get/put | Sanitized config (ARNs ok; no credentials) |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [Acceptable Use Policy](https://aws.amazon.com/aup/)
- Operator accepts AWS terms; StudioQuack does not. IAM least-privilege is **customer-owned** (bucket policy + identity policy). Typical actions: `s3:CreateBucket`, `s3:DeleteBucket`, `s3:ListAllMyBuckets`, `s3:ListBucket`, `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`, `s3:CopyObject` (`s3:GetObject`+`s3:PutObject` on dest), `s3:PutBucketNotification`, `s3:GetBucketNotification`. Never ship a wildcard `s3:*` in examples.
- **PII / residency:** object bytes and keys leave the daemon to the chosen AWS region. Keys and user metadata are not a substitute for encryption. SSE-S3/SSE-KMS is customer-configured. PHI requires a BAA with AWS, not with this node.
- **Presigned URLs expire** (SigV4 query string, max 7 days). Do not log query signatures.
- Official-API-first: REST only. No console HTML, no undocumented endpoints.
- Rate / quota (published, confirm at build): ~3,500 PUT/COPY/POST/DELETE and ~5,500 GET/HEAD per prefix per second (scales by prefix); 10,000 general-purpose buckets/account (adjustable); 100 event-notification configs/bucket; PutObject max **5 GB**; multipart max object **48.8 TiB**; 10,000 parts; part 5 MiB–5 GiB.

## Implementation notes

- **JDK HTTP only.** SigV4: canonical request → string-to-sign → `HMAC-SHA256` signing key `AWS4`+secret / date / region / `s3` / `aws4_request`. Header `Authorization: AWS4-HMAC-SHA256 Credential=…/s3/aws4_request, SignedHeaders=…, Signature=…`. Payload hash: stream SHA-256 or `UNSIGNED-PAYLOAD` only if the endpoint documents it (AWS S3 HTTPS SigV4 typically signs `x-amz-content-sha256`). Temporary creds: `x-amz-security-token`.
- **`iam_role`:** `POST` STS `AssumeRole` (`https://sts.{region}.amazonaws.com/`, `Action=AssumeRole&Version=2011-06-15`). If the daemon is on AWS and `iam_role_arn` is empty, IMDSv2 (`PUT` token TTL, then `GET` credentials) is allowed. If IMDS is unreachable, fail `{ error }` — do not scrape credentials from disk/env except daemon secret store.
- **Streaming:** resolve `file` ref-id to an InputStream; write GetObject to the file store; never hold the whole object in a `byte[]` / never persist base64.
- **Timeouts:** connect ≤ 10s; read = `timeout_seconds`. Multipart: timeout per part.
- **Retries:** exponential backoff + full jitter; cap `max_retries`. Retry 429/`SlowDown`/`ServiceUnavailable`/`InternalError`. Do not retry 403/404. `file_upload` PutObject retries are safe only if the handler re-sends the same body (stream must be replayable or use multipart).
- **Error mapping:** XML `<Error><Code/><Message/><RequestId/>` → OUTPUT `{ error }`. Never throw past dispatch.
- **SSRF:** `endpoint` must be HTTPS and pass daemon egress allowlist. Block link-local except the explicit IMDS path for `iam_role`.
- **Checksum:** optional `x-amz-checksum-sha256` / `Content-MD5` via JDK `MessageDigest`; do not require AWS CRT.
- **DO NOT BUILD:** long-poll GetObject/ListObjects as `listen`; S3 Select as `bucket_search`; AWS SDK; putting file bytes on INPUT as string/base64; directory-bucket notification config.
