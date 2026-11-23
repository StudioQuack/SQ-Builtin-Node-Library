# Box

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/box/`
- **Researched:** 2026-08-20
- **Official API:** Box Platform API 2.0 (OpenAPI version **2024.0**)
- **Docs:** https://developer.box.com/reference/
- **Auth:** Bearer `access_token` minted outside StudioQuack (OAuth, JWT, or CCG). Engine does not exchange client_id / client_secret / JWT.

## Scope

One node for Box files and folders over the **official Box REST API**. JSON calls use `https://api.box.com/2.0`. Uploads use `https://upload.box.com/api/2.0` (not the API host). Auth is a Bearer access token minted outside StudioQuack. JDK HTTP only — do not ship the Box Java SDK.

Simple upload (`POST /files/content`) is for files **≤ 50 MB** (Box: “over 50MB we recommend Chunk Upload”). Chunked upload sessions (`POST /files/upload_sessions`) require a **minimum 20 MB** file; use them for every upload **> 50 MB**. `listen` is the official **Webhooks v2** API (`POST /webhooks`) pointing at StudioQuack webhook ingress — not event long-poll scraping.

Downloaded bytes on OUTPUT are type `file` (platform ref-id `sq-file:` / `sq-ref:gridfs:`), never inline bytes.

**Do not build:** Box Sign / Relay / AI as this node; scraping box.com; using developer tokens in production.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `copy_file` | POST | `https://api.box.com/2.0/files/{file_id}/copy` | Copy; body `{ parent: { id }, name? }`. |
| `delete_file` | DELETE | `https://api.box.com/2.0/files/{file_id}` | Move file to trash. |
| `download` | GET | `https://api.box.com/2.0/files/{file_id}/content` | Follow redirect; store body as `file` ref-id. |
| `get_file` | GET | `https://api.box.com/2.0/files/{file_id}` | Metadata only (no bytes). |
| `search` | GET | `https://api.box.com/2.0/search?query=` | Full-text / name search. Extra limits apply (see Legal). |
| `share_file` | PUT | `https://api.box.com/2.0/files/{file_id}?fields=shared_link` | Set `shared_link` on the File resource (`access`: `open` \| `company` \| `collaborators`). Optional `POST /collaborations` for user/group invites. |
| `upload` | POST | `https://upload.box.com/api/2.0/files/content` (≤50 MB) or `POST …/files/upload_sessions` then PUT parts + POST `…/commit` | Multipart: `attributes` JSON part **before** `file` part. |
| `create_folder` | POST | `https://api.box.com/2.0/folders` | Body `{ name, parent: { id } }`. Root parent id is `"0"`. |
| `get_folder` | GET | `https://api.box.com/2.0/folders/{folder_id}` | Folder metadata + optional item page. |
| `delete_folder` | DELETE | `https://api.box.com/2.0/folders/{folder_id}?recursive=true\|false` | Trash folder. `recursive=true` required if not empty. |
| `share_folder` | PUT | `https://api.box.com/2.0/folders/{folder_id}?fields=shared_link` | Same `shared_link` object as files. |
| `update_folder` | PUT | `https://api.box.com/2.0/folders/{folder_id}` | Rename/move (`name`, `parent`). |
| `listen` | POST | `https://api.box.com/2.0/webhooks` | Subscribe Box → StudioQuack ingress. Triggers e.g. `FILE.UPLOADED`, `FILE.DELETED`, `FOLDER.CREATED`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get_file` | no | Chooses the row above. |
| `access_token` | password | yes | — | **yes** | Bearer token minted outside StudioQuack. |
| `webhook_address` | string | `listen` | — | no | HTTPS URL of the StudioQuack engine's message-event ingress. |
| `webhook_triggers` | string | `listen` | `FILE.UPLOADED,FILE.DELETED,FOLDER.CREATED` | no | Comma-separated Box trigger names. |
| `timeout_seconds` | number | no | `60` | no | JSON calls. |
| `upload_timeout_seconds` | number | no | `300` | no | Upload / chunked parts. |

Downloaded bytes use OUTPUT type `file` (platform ref-id), never inline bytes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. `file` is a ref-id. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or JWT assertions. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = copy_file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Source file id. |
| `parent_folder_id` | string | yes | Destination folder id (`"0"` = root). |
| `name` | string | no | New name. |

### `operation = delete_file` / `get_file` / `download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Box file id. |
| `filename` | string | no | `download` only: name on the platform file record. |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query. |
| `limit` | number | no | Page size (API default 30, max 200). |
| `offset` | number | no | Offset pagination. |
| `ancestor_folder_ids` | string | no | Comma-separated folder ids to scope. |
| `content_types` | string | no | e.g. `name,description`. |

### `operation = share_file` / `share_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | `share_file` | File to share. |
| `folder_id` | string | `share_folder` | Folder to share. |
| `access` | select | no | `open` \| `company` \| `collaborators`. Omit to use enterprise default (`{ "shared_link": {} }`). |
| `password` | password | no | Optional shared-link password (paid features). Still a secret — do not log. Prefer config if used repeatedly. |
| `unshared_at` | string | no | RFC3339 expiry. |
| `can_download` | boolean | no | Shared-link permission. |
| `invite_email` | string | no | If set, also `POST /collaborations` with `accessible_by` login + `role`. |
| `role` | string | if invite | `editor` \| `viewer` \| `previewer` \| `uploader` \| `previewer uploader` \| `viewer uploader` \| `co-owner`. |

### `operation = upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id. Handler streams bytes from the file store. |
| `parent_folder_id` | string | yes | Destination folder (`"0"` = root). |
| `filename` | string | yes | Box file name (`attributes.name`). |
| `mime_type` | string | no | Companion; multipart file part Content-Type. |
| `size` | number | no | If omitted, use file-store size. Required internally for session create. |
| `content_modified_at` | string | no | Optional RFC3339 in `attributes`. |

### `operation = create_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Folder name. |
| `parent_folder_id` | string | yes | Parent id (`"0"` = root). |

### `operation = get_folder` / `delete_folder` / `update_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `folder_id` | string | yes | Folder id. |
| `recursive` | boolean | `delete_folder` of non-empty | Default `false`. Set `true` to trash contents. |
| `name` | string | `update_folder` if renaming | New name. |
| `parent_folder_id` | string | `update_folder` if moving | New parent. |
| `marker` | string | no | `get_folder` items pagination (`usemarker=true`). |
| `limit` | number | no | Item page size. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target_type` | select | yes | `file` \| `folder`. |
| `target_id` | string | yes | File or folder id to attach the webhook. |
| `address` | string | no | Override `webhook_address`. Must be HTTPS. |
| `triggers` | string | no | Override config trigger list. |

Inbound Box POSTs are correlated by StudioQuack message events, not by a second node. Verify `Box-Signature-Primary` / `Box-Signature-Secondary` (HMAC-SHA256 of body + timestamp) using the webhook auth keys from the Box app. Respond 2xx within 30 seconds or Box retries (up to 12 times / ~2 hours — Box docs).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | `Box API Error: HTTP {code}` + `code`/`message` from JSON. No tokens. |
| `http_status` | number | HTTP error | Vendor status. |
| `request_id` | string | error JSON | Box `request_id` for support. Safe to log. |
| `id` | string | file/folder success | Box object id. |
| `type` | string | metadata | `file` \| `folder`. |
| `name` | string | metadata | Item name. |
| `parent_id` | string | metadata | Parent folder id. |
| `sha1` | string | file metadata | Content hash. |
| `size` | number | file metadata / download | Bytes. |
| `file` | file | `download` | Platform ref-id. |
| `filename` | string | `download` | Companion name. |
| `mime_type` | string | `download` | From `content_type` or Content-Type. |
| `shared_link_url` | string | share_* | `shared_link.url`. |
| `shared_link_download_url` | string | share_* | If present. |
| `entries` | array | `get_folder` / `search` | `{ id, type, name, size? }` — no bytes. |
| `total_count` | number | search / folder items | Result count when returned. |
| `webhook_id` | string | `listen` | Created webhook id. Persist to delete later. |

## Legal

- Box Platform terms (OpenAPI `termsOfService`): https://cloud.app.box.com/s/rmwxu64h1ipr41u49w3bbuvbsa29wku9
- Box.com legal index: https://www.box.com/legal
- API versioning: https://developer.box.com/guides/api-calls/api-versioning-strategy
- Rate limits: https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits — general **~1000 requests/minute/user**; Search **6/s/user**, **60/min/user**, **12/s/enterprise**. HTTP 429 with `retry-after`.
- JWT service accounts see no user files until collaborated. Enterprise GGS/admin policies apply.
- File contents are personal data. Bytes leave the daemon to Box. PHI only with a customer BAA/DPA covering Box.
- Official-API-first: no Box web-app scrape.

## Implementation notes

- JDK HTTP only. Engine does not mint OAuth/JWT tokens; put a Bearer `access_token` minted outside StudioQuack.
- `Authorization: Bearer {token}`. JSON `Content-Type: application/json`.
- Upload ≤50 MB: multipart to `https://upload.box.com/api/2.0/files/content`. Part order: `attributes` then file. Over 50 MB: POST `https://upload.box.com/api/2.0/files/upload_sessions` `{ folder_id, file_size, file_name }` → PUT parts with `Digest: sha=BASE64(SHA1(part))` and `Content-Range` → POST `…/commit` with `{ parts: […] }`. Session lifetime 7 days. Do not chunk files < 20 MB (API rejects).
- Download: GET content; follow 202/302; stream into file store; OUTPUT `file` ref-id only.
- Timeouts: `timeout_seconds` (60) JSON; `upload_timeout_seconds` (300) bytes. Cap to daemon file-store max.
- Retry: 429 honor `retry-after` + jitter; 5xx exponential backoff cap 32s; max 4 attempts. Do not retry 400/401/403/404.
- HTTP 4xx/5xx → `{ error, http_status, request_id? }` on OUTPUT. Never throw past dispatch.
- Logs: method, path template, status, sizes. Never log tokens, JWT, shared-link passwords, or bytes.
- Egress: `api.box.com`, `upload.box.com`, plus Box download redirect hosts (follow Location; still subject to daemon SSRF allowlist — operator must permit them).
- `listen`: register webhook; inbound path is StudioQuack ingress. Verify signatures; never log the HMAC keys.
