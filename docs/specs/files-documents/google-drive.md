# Google Drive

> Catalog-canonical row is productivity-collaboration; this file is the implementer contract until that category spec lands.

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md` (same node as `docs/catalog/productivity-collaboration.md`)
- **Folder when built:** `nodes/google-drive/`
- **Researched:** 2026-08-20
- **Official API:** Google Drive API v3
- **Docs:** https://developers.google.com/drive/api/reference/rest/v3
- **Auth:** `oauth2` or `service_account_json` — secret binding: `oauth_client_secret`, `refresh_token`, `service_account_json`

## Scope

One node for Google Drive files, folders, and shared drives over **Drive API v3** at `https://www.googleapis.com/drive/v3/…` (media upload `https://www.googleapis.com/upload/drive/v3/…`). JDK HTTP + RS256 JWT (same pattern as Google Sheets). Pass `supportsAllDrives=true` and `supportsTeamDrives=true` (legacy alias) on methods that accept them so shared-drive items work.

Prefer OAuth scope `https://www.googleapis.com/auth/drive.file` (per-file). Broad `drive` / `drive.readonly` / `drive.metadata` are **restricted** and need Google OAuth verification / CASA — do not default to them.

`listen` is official **push notifications** (`files.watch` / `changes.watch` + `channels.stop`), HTTPS POST to StudioQuack webhook ingress. Not a Drive UI scrape.

File bytes use type `file` (ref-id `sq-file:` / `sq-ref:gridfs:`), never inline bytes.

**Do not build:** Drive UI scraping; Docs/Sheets/Slides as this node (those are other catalog rows); using `alt=media` on Google Workspace native types without `files.export`.

## Operations (`operation` config values)

Base: `https://www.googleapis.com`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `copy` | POST | `/drive/v3/files/{fileId}/copy` | Copy; body File resource (`name`, `parents`). Query `supportsAllDrives=true`. |
| `create_from_text` | POST | `/upload/drive/v3/files?uploadType=multipart` | Create a file from UTF-8 text (not a `file` handle). Metadata + media. |
| `upload` | POST | `/upload/drive/v3/files?uploadType=multipart` or `resumable` | Create from a `file` ref-id. Resumable if over ~5 MiB (Google resumable protocol). |
| `download` | GET | `/drive/v3/files/{fileId}?alt=media` or `/drive/v3/files/{fileId}/export?mimeType=` | Binary vs Google Workspace export. Store as `file` ref-id. |
| `update` | PATCH | `/drive/v3/files/{fileId}` and/or `/upload/drive/v3/files/{fileId}` | Metadata and/or new bytes. |
| `delete` | DELETE | `/drive/v3/files/{fileId}` | Permanent delete (not trash) unless `trashed` via update. |
| `move` | PATCH | `/drive/v3/files/{fileId}?addParents=&removeParents=` | Change parents. `supportsAllDrives=true`. |
| `share` | POST | `/drive/v3/files/{fileId}/permissions` | Add a permission (`type`, `role`, `emailAddress`). |
| `search` | GET | `/drive/v3/files?q=` | Drive query language. `corpora`, `driveId`, `includeItemsFromAllDrives`, `supportsAllDrives`. |
| `create_folder` | POST | `/drive/v3/files` | Metadata only; `mimeType=application/vnd.google-apps.folder`. |
| `delete_folder` | DELETE | `/drive/v3/files/{fileId}` | Same as file delete (folders are files). |
| `share_folder` | POST | `/drive/v3/files/{fileId}/permissions` | Same permissions API as `share`. |
| `create_shared_drive` | POST | `/drive/v3/drives?requestId=` | Create a shared drive (`name`). `requestId` UUID required. |
| `get_shared_drive` | GET | `/drive/v3/drives/{driveId}` | Shared-drive metadata. |
| `list_shared_drives` | GET | `/drive/v3/drives` | Page shared drives. |
| `update_shared_drive` | PATCH | `/drive/v3/drives/{driveId}` | Update metadata. |
| `delete_shared_drive` | DELETE | `/drive/v3/drives/{driveId}` | Organizer only; drive must be empty. |
| `listen` | POST | `/drive/v3/changes/watch` (default) or `/drive/v3/files/{fileId}/watch` | Channel `{ id, type: "web_hook", address }`. Stop: `POST /drive/v3/channels/stop`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2` | no | `oauth2` \| `service_account_json`. |
| `oauth_client_id` | string | if `oauth2` | — | no | Google Cloud OAuth client id. |
| `oauth_client_secret` | password | if `oauth2` | — | **yes** | Client secret. |
| `refresh_token` | password | if `oauth2` | — | **yes** | User refresh token. Token URL `https://oauth2.googleapis.com/token`. |
| `service_account_json` | password | if `service_account_json` | — | **yes** | Key JSON (`client_email` + PKCS8 `private_key`). Share Drive files/folders with that email or use DWD. |
| `impersonate_user` | string | no | — | no | DWD `sub` (Workspace user email). Empty = the service account itself. |
| `oauth_scope` | string | no | `https://www.googleapis.com/auth/drive.file` | no | Space-separated. Restricted scopes need verification. |
| `listen_address` | string | `listen` | — | no | HTTPS notification URL (StudioQuack ingress). |
| `timeout_seconds` | number | no | `60` | no | JSON/token. |
| `upload_timeout_seconds` | number | no | `300` | no | Media upload/download. |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Upload `file` is a ref-id. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or JWT. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = copy`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Source file id. |
| `name` | string | no | Copied file name. |
| `parent_id` | string | no | Destination parent folder/drive item id. |

### `operation = create_from_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | UTF-8 body (not type `file`). |
| `name` | string | yes | File name. |
| `mime_type` | string | no | Default `text/plain`. |
| `parent_id` | string | no | Parent folder id. |

### `operation = upload` / `update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | `upload`; optional on `update` if metadata-only | Platform ref-id of bytes. |
| `file_id` | string | `update` | Existing file. |
| `name` | string | `upload` | Drive name. |
| `mime_type` | string | no | Metadata `mimeType`. |
| `parent_id` | string | no | Parent for create. |
| `filename` | string | no | Companion override. |

### `operation = download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | File id. |
| `export_mime_type` | string | if Google Workspace native | e.g. `application/pdf` for Docs (`files.export`). |
| `filename` | string | no | Stored filename. |

### `operation = delete` / `delete_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | File or folder id. |

### `operation = move`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Item to move. |
| `add_parent_id` | string | yes | New parent. |
| `remove_parent_id` | string | yes | Old parent (required by Drive to avoid multi-parent). |

### `operation = share` / `share_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | File or folder id. |
| `role` | select | yes | `reader` \| `commenter` \| `writer` \| `fileOrganizer` \| `organizer`. |
| `type` | select | yes | `user` \| `group` \| `domain` \| `anyone`. |
| `email_address` | string | if user/group | Grantee. |
| `domain` | string | if domain | Workspace domain. |
| `send_notification_email` | boolean | no | Default `true`. |
| `email_message` | string | no | Optional notification body. |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | Drive query, e.g. `name contains 'report' and trashed = false`. |
| `page_size` | number | no | Max 1000. |
| `page_token` | string | no | Pagination. |
| `drive_id` | string | no | Shared drive id (`corpora=drive`). |
| `corpora` | select | no | `user` \| `drive` \| `allDrives`. |

### `operation = create_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Folder name. |
| `parent_id` | string | no | Parent; omit = My Drive root (scope-dependent). |

### `operation = create_shared_drive` / `get_shared_drive` / `update_shared_drive` / `delete_shared_drive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | `create` / optional `update` | Shared drive name. |
| `drive_id` | string | get/update/delete | Shared drive id. |
| `request_id` | string | `create` | Idempotency UUID. Handler may generate if omitted. |

### `operation = list_shared_drives`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Page size. |
| `page_token` | string | no | Pagination. |
| `q` | string | no | Drive list query. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel_id` | string | yes | Unique channel UUID (caller-supplied). |
| `address` | string | no | Override `listen_address`. HTTPS only. |
| `file_id` | string | no | If set, `files.watch`; else `changes.watch`. |
| `drive_id` | string | no | Shared-drive changes (`changes.watch` + `driveId`). |
| `token` | string | no | Channel verification token (optional; not a Google secret). |
| `expiration_ms` | number | no | Unix ms expiration. Google caps channel lifetime. |
| `page_token` | string | `changes.watch` | From `GET /drive/v3/changes/startPageToken`. Handler fetches if omitted. |

Google POSTs notifications to `address`. Respond 2xx quickly. Sync token / `pageToken` via `changes.list` to see what changed. `POST /drive/v3/channels/stop` with `{ id, resourceId }` to unsubscribe.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | `Drive API Error: HTTP {code}` + `reason`. No tokens. |
| `http_status` | number | HTTP error | Vendor status (403 restricted scope, 429, …). |
| `id` | string | file/drive success | File or shared-drive id. |
| `name` | string | metadata | Name. |
| `mime_type` | string | metadata | Drive `mimeType`. |
| `parents` | string[] | metadata | Parent ids. |
| `web_view_link` | string | metadata | If fields requested. |
| `size` | number | binary files | Bytes (Workspace natives often omit). |
| `file` | file | `download` | Platform ref-id. |
| `filename` | string | `download` | Companion. |
| `files` | array | `search` | `{ id, name, mime_type }` — no bytes. |
| `next_page_token` | string | list/search | Pagination. |
| `start_page_token` | string | listen setup | Persist for `changes.list`. |
| `channel_id` | string | `listen` | Echo of channel id. |
| `resource_id` | string | `listen` | Required to `channels.stop`. |
| `expiration` | string | `listen` | Channel expiry. |
| `permission_id` | string | share_* | Created permission id. |

## Legal

- Google APIs Terms of Service: https://developers.google.com/terms
- Drive API scopes (restricted vs `drive.file`): https://developers.google.com/drive/api/guides/api-specific-auth
- Additional User Data policy / Limited Use: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Restricted-scope verification: https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification
- Usage limits: https://developers.google.com/workspace/drive/api/guides/limits — **1,000,000 quota units/min/project**, **325,000 units/min/user/project**, **1 TB/day/project**. Method units (examples): `files.get` 5, `files.list` 100, download 200, `files.update` 50. Upload/copy also subject to **750 GB/user/day** Drive backend cap. HTTP 403 `userRateLimitExceeded` or 429 → backoff.
- Push notifications: https://developers.google.com/workspace/drive/api/guides/push
- File contents are personal data. Bytes leave the daemon to Google. PHI only with a customer BAA covering Google Workspace / Drive.
- Official-API-first: no Drive web scrape, no unofficial Drive wrappers.

## Implementation notes

- JDK HTTP only. OAuth refresh: POST `https://oauth2.googleapis.com/token`. Service account: JWT RS256 `iss=client_email`, `scope=oauth_scope`, `aud=https://oauth2.googleapis.com/token`, optional `sub=impersonate_user`. Cache Bearer until `expires_in` minus 60s.
- Always send `supportsAllDrives=true` when the method documents it. `includeItemsFromAllDrives=true` on list/search when `corpora=allDrives` or `drive`.
- Multipart upload: metadata JSON part + media part. Resumable: start session (`uploadType=resumable`), PUT byte ranges to the session URI. Stream from the file store; never base64 in JSON.
- Download: if `mimeType` starts with `application/vnd.google-apps.`, require `export_mime_type` and GET export; else GET `alt=media`. Store in file store; OUTPUT type `file`.
- `fields` query: request only needed properties (`id,name,mimeType,parents,size`) to save quota.
- Timeouts: 60s JSON, 300s media. Cap to daemon file-store max.
- Retry: 403 rateLimit / 429 / 503 exponential backoff (1s…32s + jitter), honor `Retry-After`. Max 4 attempts. Do not retry 400/401/404.
- HTTP 4xx/5xx → `{ error, http_status }` on OUTPUT. Never throw past dispatch.
- Logs: method, path template, status, sizes, quota-ish unit class. Never log tokens, JWT, notification `token`, or bytes.
- Egress: `oauth2.googleapis.com`, `www.googleapis.com`. Listen inbound is StudioQuack ingress (Google as caller).
- `listen`: HTTPS only; Google syncs with a `X-Goog-Channel-*` header set. Persist `resourceId` + `pageToken`. Renew before `expiration`.
