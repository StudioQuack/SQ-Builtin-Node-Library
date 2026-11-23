# Google Drive

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md` (canonical). `docs/catalog/files-documents.md` is a **cross-reference to this node**, not a second implementation.
- **Folder when built:** `nodes/google-drive/`
- **Researched:** 2026-08-20
- **Official API:** Google Drive API v3 (`https://www.googleapis.com` + `/drive/v3` and `/upload/drive/v3`)
- **Docs:** https://developers.google.com/workspace/drive/api/reference/rest/v3 · https://developers.google.com/workspace/drive/api/guides/api-specific-auth · https://developers.google.com/workspace/drive/api/guides/manage-uploads · https://developers.google.com/workspace/drive/api/guides/manage-downloads · https://developers.google.com/workspace/drive/api/guides/ref-export-formats · https://developers.google.com/workspace/drive/api/guides/push · https://developers.google.com/workspace/drive/api/guides/limits
- **Auth:** `oauth2` or `service_account` JWT — secret binding: `oauth_client_secret`, `refresh_token`, `service_account_json`

## Scope

**This file is the only Drive node spec.** Files & Documents catalogs the same provider; do not add `docs/specs/files-documents/google-drive.md`.

One node, callers pick `operation`. JDK HTTP to Drive v3 only. Upload/download/export use platform `file` (ref-id), never inline bytes on ports or config.

Service endpoint from the REST reference: `https://www.googleapis.com` with paths `/drive/v3/…`. Media upload host path: `https://www.googleapis.com/upload/drive/v3/…`.

No catalog verbs were dropped: every seeded operation maps to a current Drive v3 method (`files.*`, `permissions.create`, `drives.*`, `changes.watch` / `changes.list`). `delete_folder` and `share_folder` are the same HTTP as `delete` / `share` with a folder MIME check. `move` is `files.update` with `addParents` / `removeParents`. Workspace-native files cannot use `alt=media`; `download` of those types uses `files.export`.

Prefer scope `https://www.googleapis.com/auth/drive.file` (non-sensitive). Shared-drive **create** requires restricted `https://www.googleapis.com/auth/drive`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `copy` | POST | `/drive/v3/files/{fileId}/copy` | Duplicate a file |
| `create_from_text` | POST | `/drive/v3/files` or `/upload/drive/v3/files?uploadType=multipart` | Create from a **string** (not `file`) |
| `upload` | POST | `/upload/drive/v3/files?uploadType=multipart` (≤5 MiB) or `uploadType=resumable` (>5 MiB) | Create from platform `file` |
| `download` | GET | Blob: `/drive/v3/files/{fileId}?alt=media`. Workspace: `/drive/v3/files/{fileId}/export?mimeType=` | Bytes → platform `file` |
| `update` | PATCH | `/drive/v3/files/{fileId}` and/or `/upload/drive/v3/files/{fileId}?uploadType=…` | Metadata and/or content |
| `delete` | DELETE | `/drive/v3/files/{fileId}` | Permanent delete (not trash). Confirm `files.get` `trashed` semantics: this method deletes |
| `move` | PATCH | `/drive/v3/files/{fileId}?addParents=&removeParents=&supportsAllDrives=true` | Change parents |
| `share` | POST | `/drive/v3/files/{fileId}/permissions` | ACL on a file |
| `search` | GET | `/drive/v3/files?q=&pageSize=&pageToken=&supportsAllDrives=true&includeItemsFromAllDrives=true` | Query language `q` |
| `create_folder` | POST | `/drive/v3/files` body `{ name, mimeType: application/vnd.google-apps.folder, parents }` | Metadata-only folder |
| `delete_folder` | DELETE | `/drive/v3/files/{fileId}` | Same as `delete` after MIME `…folder` check |
| `share_folder` | POST | `/drive/v3/files/{fileId}/permissions` | Same as `share` after folder MIME check |
| `create_shared_drive` | POST | `/drive/v3/drives?requestId={uuid}` | Shared drive. Scope **`drive`** (restricted) |
| `get_shared_drive` | GET | `/drive/v3/drives/{driveId}` | Metadata |
| `list_shared_drives` | GET | `/drive/v3/drives` | Page of shared drives |
| `update_shared_drive` | PATCH | `/drive/v3/drives/{driveId}` | Name / restrictions |
| `delete_shared_drive` | DELETE | `/drive/v3/drives/{driveId}` | Organizer only; drive must be empty |
| `listen` | POST or GET | Push: `POST /drive/v3/changes/watch` or `POST /drive/v3/files/{fileId}/watch`. Poll: `GET /drive/v3/changes/startPageToken` then `GET /drive/v3/changes`. Stop: `POST /drive/v3/channels/stop` | Official Drive push, or one poll cycle |

Query `supportsAllDrives` on file methods that touch shared drives (INPUT `supportsAllDrives`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Row above |
| `auth_type` | select | yes | `service_account_json` | no | `oauth2` \| `service_account_json` |
| `oauth_client_id` | string | if `oauth2` | empty | no | OAuth client id |
| `oauth_client_secret` | password | if `oauth2` | empty | yes | OAuth client secret |
| `refresh_token` | password | if `oauth2` | empty | yes | Refresh token |
| `service_account_json` | password | if `service_account_json` | empty | yes | SA key JSON |
| `delegated_user` | string | no | empty | no | JWT `sub` for Workspace domain-wide delegation |
| `oauth_scopes` | string | no | `https://www.googleapis.com/auth/drive.file` | no | Space-separated. Shared-drive create needs `https://www.googleapis.com/auth/drive` |
| `timeout_seconds` | number | no | `60` | no | Token + each Drive call. Uploads/downloads need more than Sheets’ 25 s |
| `max_transfer_bytes` | number | no | `26214400` | no | Hard cap 25 MiB default for upload/download/export in this node (daemon protection). Google’s product max is 5 TB / 750 GB copy — do not stream that through the sandbox |
| `listen_mode` | select | no | `poll` | no | `poll` \| `push` |

### Auth

`POST https://oauth2.googleapis.com/token`. OAuth2 refresh grant or SA JWT RS256 (`iss=client_email`, `scope`, `aud=https://oauth2.googleapis.com/token`, `iat`/`exp`, optional `sub=delegated_user`). Same Sheets JWT rules: PKCS8 `BEGIN PRIVATE KEY`, expand `\n`, no token cache across invokes.

| Scope | Use | Sensitivity |
|---|---|---|
| `https://www.googleapis.com/auth/drive.file` | Per-file (created/opened/shared with the app) | Non-sensitive — **default** |
| `https://www.googleapis.com/auth/drive` | All files + `drives.create` | **Restricted** |
| `https://www.googleapis.com/auth/drive.readonly` | All files read/download | **Restricted** |
| `https://www.googleapis.com/auth/drive.metadata.readonly` | Metadata list/search without bytes | Restricted |

Restricted Drive scopes need OAuth verification, Limited Use, and a security assessment (CASA) if the app stores or transmits that user data. Internal Workspace-only apps can be exempt from public verification; still treat data as PII.

Share files/folders with the SA email unless using DWD.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens |

FUNCTION ports: none.

`upload` / `update` (content) input `file` and `download` output `file` are type **`file`**.

## Input fields (by operation)

Common: `file_id` `[A-Za-z0-9_-]+`, length ≥ 10, reject placeholders. INPUT `supportsAllDrives` is sent as query `supportsAllDrives`.

### `operation = copy`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Source |
| `name` | string | no | New name |
| `folder_id` | string | no | `parents: [id]` |

### `operation = create_from_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | File name |
| `text` | string | yes | UTF-8 body. Cap 1 MiB string. Not a `file` |
| `mime_type` | string | no | Default `text/plain`. Use `application/vnd.google-apps.document` to convert into a native Doc |
| `folder_id` | string | no | Parent |

### `operation = upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id. Handler reads bytes |
| `name` | string | no | Metadata name (else store filename) |
| `mime_type` | string | no | Else sniff / `application/octet-stream` |
| `folder_id` | string | no | Parent |

Multipart if size ≤ 5 MiB; else resumable (`uploadType=resumable`, session URI, then `PUT` chunks). Reject if size > `max_transfer_bytes`.

### `operation = download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | |
| `mime_type` | string | if Workspace native | `files.export` MIME ([export table](https://developers.google.com/workspace/drive/api/guides/ref-export-formats)). Blob files ignore this and use `alt=media` |
| `acknowledge_abuse` | boolean | no | Query on `files.get` when Google flags malware — default **false** |

HEAD/metadata `files.get` first for `mimeType` and `size`. If `application/vnd.google-apps.*` and `mime_type` missing → `{ error }` naming export MIME. If `size` > cap → error, no download.

### `operation = update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | |
| `name` | string | no | Metadata patch |
| `description` | string | no | |
| `mime_type` | string | no | |
| `starred` | boolean | no | |
| `file` | file | no | New bytes (upload URI). Omit for metadata-only |

### `operation = delete` / `delete_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Permanent delete. `delete_folder`: GET first; if MIME ≠ `application/vnd.google-apps.folder` → error |

### `operation = move`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | |
| `add_parents` | string | yes* | Comma-separated folder ids (*at least one of add/remove) |
| `remove_parents` | string | yes* | Previous parent ids |

### `operation = share` / `share_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | File or folder (or shared-drive id per Drive docs) |
| `role` | string | yes | `reader` \| `commenter` \| `writer` \| `fileOrganizer` \| `organizer` \| `owner` |
| `type` | string | yes | `user` \| `group` \| `domain` \| `anyone` |
| `email_address` | string | if user/group | |
| `domain` | string | if domain | |
| `send_notification_email` | boolean | no | Default true for user/group |
| `transfer_ownership` | boolean | no | Required true when `role=owner` |

`share_folder`: MIME must be folder.

Concurrent permission writes on the same file are unsupported (last write wins).

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | Drive query, e.g. `name contains 'q' and trashed = false`. Cap 1024 chars |
| `page_size` | number | no | 1–1000, default 100 |
| `page_token` | string | no | |
| `fields` | string | no | Partial response, default `nextPageToken,files(id,name,mimeType,parents,modifiedTime,size,driveId)` |
| `corpora` | string | no | `user` \| `drive` \| `allDrives` |
| `drive_id` | string | if corpora=drive | |
| `order_by` | string | no | e.g. `modifiedTime desc` |

### `operation = create_folder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | |
| `folder_id` | string | no | Parent (My Drive root if omitted) |

### Shared drives

#### `create_shared_drive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Drive name |
| `request_id` | string | yes | Idempotency UUID. Repeat + same user → no duplicate; conflict → 409 |

#### `get_shared_drive` / `delete_shared_drive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `drive_id` | string | yes | |

#### `list_shared_drives`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default 10, max 100 |
| `page_token` | string | no | |
| `q` | string | no | Shared-drive query |

#### `update_shared_drive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `drive_id` | string | yes | |
| `name` | string | no | |
| `restrictions` | object | no | Drive `restrictions` patch |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listen_mode` | string | no | `poll` \| `push` |
| `file_id` | string | if `files.watch` | Omit for changes.watch (user/shared-drive changelog) |
| `drive_id` | string | no | `changes.watch` / `changes.list` for a shared drive |
| `webhook_address` | string | if push | HTTPS callback (message-event ingress). Public CA TLS |
| `channel_id` | string | if push | UUID ≤ 64 chars |
| `channel_token` | string | no | `X-Goog-Channel-Token`. No OAuth secrets |
| `expiration_ms` | number | no | |
| `page_token` | string | poll | If empty, `GET /drive/v3/changes/startPageToken` |
| `include_removed` | boolean | no | |
| `stop_channel_id` | string | no | If set with `stop_resource_id`, POST `/drive/v3/channels/stop` instead of watch |
| `stop_resource_id` | string | with stop | |

Push notifications: empty body, headers `X-Goog-Resource-State` = `sync` \| `add` \| `remove` \| `update` \| `trash` \| `untrash` \| `change`. Respond 2xx. Channels expire; renew with a **new** `channel_id`. Watch/stop count against quota; delivered notifications do not.

## Output fields

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `file_id` | string | copy, create_*, upload, update, create_folder | Drive file id |
| `name` | string | file ops | |
| `mime_type` | string | file ops | |
| `parents` | array | file ops | |
| `web_view_link` | string | when Google returns it | |
| `file` | file | download | Platform ref-id |
| `byte_size` | number | download, upload | |
| `permission_id` | string | share* | |
| `files` | array | search | `{ id, name, mimeType, … }` |
| `next_page_token` | string | search, list_shared_drives, listen poll | |
| `drive_id` | string | shared-drive ops | |
| `drives` | array | list_shared_drives | |
| `channel_id` | string | listen push | |
| `resource_id` | string | listen push | For stop |
| `expiration` | number | listen push | |
| `changes` | array | listen poll | Changelog items (metadata, not bytes) |
| `new_start_page_token` | string | listen poll | Persist |
| `error` | string | failure | |
| `http_status` | number | HTTP error | Drive often uses **403** for rate limit as well as 429 |
| `retry_after_seconds` | number | 403/429/5xx | |

## Legal

- [Google APIs Terms of Service](https://developers.google.com/terms)
- [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy) — Limited Use for restricted/sensitive scopes
- [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) — restricted-scope app types (backup/sync, productivity, reporting/security)
- [OAuth verification / security assessment](https://support.google.com/cloud/answer/9110914)
- **Quota (verified 2026-08-20):** 1,000,000 units/min/project, 325,000/min/user; daily billing threshold 400,000,000 units/project; 1 TB/day egress before announced charges. Method costs: list 100, read 5, download 200, edit 50. HTTP 403 `User rate limit exceeded` or 429. [Usage limits](https://developers.google.com/workspace/drive/api/guides/limits). Possible Cloud billing later in 2026 with notice
- Product caps: 750 GB upload/copy per user per day; max upload 5 TB (this node uses `max_transfer_bytes`)
- **PII / DLP:** file bytes leave Google only on `download`/`export`. Customer is controller. ePHI only with a covering BAA
- No scrape of drive.google.com

## Implementation notes

- JDK HTTP only. Hosts: `oauth2.googleapis.com`, `www.googleapis.com`.
- Resume uploads: follow Drive resumable protocol (`Location` session URI, `PUT` with `Content-Range`). Subsequent chunks use PUT.
- Workspace download without export MIME → error, do not call `alt=media` (Google returns 403).
- Timeouts 60 s default; still fail if over `max_transfer_bytes`.
- Retry 403-rate / 429 / 5xx with truncated exponential backoff + jitter; not 400/401/404/403-permission.
- 4xx/5xx → `{ error, http_status }` on OUTPUT; never throw past dispatch.
- Redact tokens, SA JSON, `channel_token` if it could leak secrets. Log operation, file id, status, sizes — never bytes.
- `create_from_text` is not `upload`; do not accept `file` on that op.
- Enable **Google Drive API** in Cloud Console.
