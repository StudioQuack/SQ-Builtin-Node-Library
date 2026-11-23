# Dropbox

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/dropbox/`
- **Researched:** 2026-08-20
- **Official API:** Dropbox API v2 (HTTP RPC + content-upload + content-download)
- **Docs:** https://www.dropbox.com/developers/documentation/http/documentation
- **Auth:** `bearer` via `access_token` minted outside StudioQuack. Engine does not refresh OAuth.

## Scope

One node for Dropbox file and folder operations over the **official Dropbox HTTP API v2**. RPC metadata calls go to `https://api.dropboxapi.com/2/…`. Upload and download of file bytes go to `https://content.dropboxapi.com/2/…` (never the RPC host). `listen` uses the official longpoll host `https://notify.dropboxapi.com/2/files/list_folder/longpoll` (no Bearer header; the folder cursor is the credential). JDK `HttpClient` only — do not ship the Dropbox Java SDK.

Simple `files/upload` is limited to **150 MiB**. Larger files must use `upload_session/start` → `upload_session/append_v2` → `upload_session/finish` on the content domain. File bytes on ports and config are the platform `file` type (`sq-file:` / `sq-ref:gridfs:`), never inline bytes or base64 as the schema type.

**Do not build:** Dropbox Paper/API v1; scraping dropbox.com; team admin endpoints unless a later catalog row asks; treating longpoll as a fake HTML poll of the web UI.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `copy_file` | POST | `https://api.dropboxapi.com/2/files/copy_v2` | Copy one file. JSON `{ from_path, to_path, autorename }`. |
| `delete_file` | POST | `https://api.dropboxapi.com/2/files/delete_v2` | Delete a file at `path`. Same RPC as folder delete. |
| `download` | POST | `https://content.dropboxapi.com/2/files/download` | Download bytes. Args in `Dropbox-API-Arg`; body is empty; metadata in `Dropbox-API-Result`. Store bytes as `file` ref-id. |
| `move_file` | POST | `https://api.dropboxapi.com/2/files/move_v2` | Move/rename a file. |
| `upload` | POST | `https://content.dropboxapi.com/2/files/upload` (≤150 MiB) or `…/upload_session/start` + `append_v2` + `finish` | Upload from a `file` ref-id. Args in `Dropbox-API-Arg`; body is octet-stream. |
| `copy_folder` | POST | `https://api.dropboxapi.com/2/files/copy_v2` | Copy a folder (same RPC as `copy_file`; path must be a folder). |
| `create_folder` | POST | `https://api.dropboxapi.com/2/files/create_folder_v2` | Create a folder at `path`. |
| `delete_folder` | POST | `https://api.dropboxapi.com/2/files/delete_v2` | Delete a folder (non-empty allowed). |
| `list_folder` | POST | `https://api.dropboxapi.com/2/files/list_folder` then `/files/list_folder/continue` while `has_more` | List entries; persist `cursor`. |
| `move_folder` | POST | `https://api.dropboxapi.com/2/files/move_v2` | Move/rename a folder. |
| `search` | POST | `https://api.dropboxapi.com/2/files/search_v2` then `/files/search/continue_v2` | Filename/content search (`query`). |
| `listen` | POST | `https://notify.dropboxapi.com/2/files/list_folder/longpoll` then `list_folder/continue` | Official change wait. Timeout 30–480s plus ≤90s jitter. If `changes=true`, fetch entries via continue. |

Retired `/copy` `/delete` `/move` `/create_folder` (non-`_v2`) and `/search` v1 are omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list_folder` | no | Chooses the row above. |
| `access_token` | password | yes | — | **yes** | OAuth access token minted outside StudioQuack. Bearer. Never on INPUT. |
| `root_namespace_id` | string | no | — | no | Optional `Dropbox-API-Path-Root` JSON `{".tag":"namespace_id","namespace_id":"…"}` for team spaces. |
| `upload_chunk_bytes` | number | no | `8388608` | no | Session part size (8 MiB). Must be ≤150 MiB per request. |
| `timeout_seconds` | number | no | `60` | no | Connect+read for RPC. |
| `content_timeout_seconds` | number | no | `300` | no | Upload/download/session parts. |
| `listen_timeout_seconds` | number | no | `30` | no | Longpoll `timeout` (min 30, max 480). HTTP client timeout must be this + 120s. |

File payloads (upload, download) use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Paths and query only; OfficialApiNode does not stream upload bytes. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens, cursors in logs, or file bytes. |

FUNCTION ports: none.

## Input fields (by operation)

Shared path fields: Dropbox paths are `/`-prefixed (`""` or `"id:…"` / `"rev:…"` also accepted by the API). Never send account passwords on INPUT.

### `operation = copy_file` / `copy_folder` / `move_file` / `move_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from_path` | string | yes | Source path or `id:…`. |
| `to_path` | string | yes | Destination path. |
| `autorename` | boolean | no | Default `false`. If `true`, Dropbox renames on conflict. |
| `allow_ownership_transfer` | boolean | no | `move_*` only. Default `false`. |
| `allow_shared_folder` | boolean | no | Copy/move of shared folders. Default `false`. |

### `operation = delete_file` / `delete_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | File or folder to delete. |

### `operation = download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | File path or `id:…`. |
| `filename` | string | no | Name stored on the platform file record; default from `Dropbox-API-Result.name`. |

### `operation = upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id of bytes to send. Handler streams from the file store. |
| `path` | string | yes | Destination Dropbox path including filename. |
| `filename` | string | no | Override last path segment if `path` is a folder. |
| `mime_type` | string | no | Companion only (Dropbox does not require it). |
| `mode` | select | no | `add` (default) \| `overwrite` \| `update`. |
| `autorename` | boolean | no | Default `false`. |
| `mute` | boolean | no | Default `false`. Suppress user notification. |
| `strict_conflict` | boolean | no | Default `false`. |

### `operation = create_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Folder path to create. |
| `autorename` | boolean | no | Default `false`. |

### `operation = list_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Folder (`""` = app/user root depending on permission). |
| `recursive` | boolean | no | Default `false`. |
| `limit` | number | no | Cap on entries per page (API max 2000). |
| `cursor` | string | no | Resume via `list_folder/continue`. Treat as sensitive. |
| `include_deleted` | boolean | no | Default `false`. |
| `include_mounted_folders` | boolean | no | Default `true`. |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search string (`search_v2.query`). |
| `path` | string | no | Restrict to this folder. |
| `max_results` | number | no | Default 100. |
| `filename_only` | boolean | no | Default `false` (`options.filename_only`). |
| `cursor` | string | no | `search/continue_v2`. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes (first cursor) | Folder to watch. Handler calls `list_folder` or `list_folder/get_latest_cursor` then longpoll. |
| `cursor` | string | no | Existing cursor from a previous list/listen. Required after the first run. |
| `recursive` | boolean | no | Default `true` for listen so nested changes surface. |

This is **not** Dropbox app webhooks (those exist at https://www.dropbox.com/developers/reference/webhooks and notify that *something* changed, not which file). Catalog `listen` is official **longpoll**. Do not scrape the website. StudioQuack webhook ingress is unused for this verb unless the operator separately registers Dropbox app webhooks to the message-event URL.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Validation or `Dropbox API Error: HTTP {code}` + `error_summary` (no tokens). |
| `http_status` | number | HTTP error | Vendor status (409 conflict, 429, …). |
| `error_tag` | string | API error JSON | `.tag` from `error` union when present. |
| `path` | string | copy/move/create/upload/delete | Result `path_display` or requested path. |
| `id` | string | metadata success | Dropbox file/folder id. |
| `rev` | string | file metadata | Content revision. |
| `name` | string | metadata | Name. |
| `size` | number | file metadata / download | Bytes. |
| `is_downloadable` | boolean | download metadata | From `Dropbox-API-Result`. |
| `file` | file | `download` | Platform ref-id of stored content. |
| `filename` | string | `download` | Companion filename. |
| `mime_type` | string | `download` | Best-effort from name/metadata (Dropbox may omit MIME). |
| `entries` | array | `list_folder` / `listen` | `{ tag, name, path_display, id, size? }` — no bytes. |
| `cursor` | string | list / search / listen | Pagination or longpoll cursor. Do not log. |
| `has_more` | boolean | list / search | Call continue when true. |
| `matches` | array | `search` | `{ name, path_display, id }` from `matches[].metadata`. |
| `changes` | boolean | `listen` | Longpoll `changes`. |
| `backoff_seconds` | number | `listen` | Honor `backoff` before the next longpoll. |

## Legal

- Developer Terms: https://www.dropbox.com/developers/reference/tos (posted 2026 research date; page marked Posted/Effective March 1, 2025)
- Developer Guide / production approval: https://www.dropbox.com/developers/reference/developer-guide
- Acceptable Use: https://www.dropbox.com/acceptable_use
- Privacy: https://www.dropbox.com/privacy
- Rate limits: Dropbox **does not publish numeric quotas**. HTTP **429** / **503** with `Retry-After`; JSON may include `too_many_requests` or `too_many_write_operations`. See https://developers.dropbox.com/dbx-performance-guide
- Development apps: capped linked users; after 50 linked users a production-approval window applies (developer guide). Operator owns App Console + production review.
- File contents and paths are personal data (GDPR). Bytes leave the daemon to Dropbox. Do not put ePHI in Dropbox unless the customer’s Dropbox contract/BAA covers it.
- Official-API-first: no scrape of dropbox.com, no unofficial wrappers.

## Implementation notes

- JDK HTTP only. Bind an OAuth access token minted outside StudioQuack as `access_token`. The engine does not exchange `refresh_token` / `app_key` / `app_secret`.
- RPC: `Content-Type: application/json`, `Authorization: Bearer {token}`. Content-upload/download: `Content-Type: application/octet-stream`, JSON args in `Dropbox-API-Arg` (compact JSON; escape Unicode). Download response body = bytes; parse `Dropbox-API-Result`.
- Upload ≤ 150 MiB: single POST `/2/files/upload`. Larger: POST `/2/files/upload_session/start` (first part), `/2/files/upload_session/append_v2` (offset + session_id), `/2/files/upload_session/finish` (commit `{ path, mode, autorename }`). Stream from the file store; never load the whole file as a JSON string.
- `listen`: HTTP client timeout ≥ `listen_timeout_seconds` + 120. Longpoll **Authentication: none** — do not attach Bearer (official). On `reset` error, obtain a new cursor via `list_folder`. On `changes=true`, POST `list_folder/continue`. Honor `backoff`.
- Timeouts: `timeout_seconds` (default 60) RPC; `content_timeout_seconds` (default 300) bytes. Cap download size to the daemon file-store max; over → `{ error }` without buffering unbounded memory.
- Retry: 429/503 honor `Retry-After` or exponential backoff (1s, 2s, 4s, cap 32s, jitter). Do not retry 400/401/403/409 path conflicts blindly. Max 4 attempts on 5xx/429. Serialize writes to the same namespace when seeing `too_many_write_operations`.
- HTTP 4xx/5xx → `{ error, http_status, error_tag? }` on OUTPUT. Never throw past dispatch. Never echo tokens or raw bytes.
- Logs: host, RPC name, status, sizes. Never log Authorization, `Dropbox-API-Arg` if it includes paths the operator marks sensitive, cursors, or file bytes.
- Egress allowlist: `api.dropboxapi.com`, `content.dropboxapi.com`, `notify.dropboxapi.com`.
