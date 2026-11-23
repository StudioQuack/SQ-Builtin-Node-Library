# Google Docs

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/google-docs/`
- **Researched:** 2026-08-20
- **Official API:** Google Docs API v1 (`docs.googleapis.com`) plus Drive API v3 for copy, export, and watch (Docs has no `documents.update` and no push channel of its own)
- **Docs:** https://developers.google.com/workspace/docs/api/reference/rest · https://developers.google.com/workspace/docs/api/auth · https://developers.google.com/workspace/drive/api/reference/rest/v3 · https://developers.google.com/workspace/drive/api/guides/ref-export-formats · https://developers.google.com/workspace/drive/api/guides/push
- **Auth:** `oauth2` or `service_account` JWT — secret binding: `oauth_client_secret`, `refresh_token`, `service_account_json`

## Scope

One node for Google Docs. Callers pick `operation`. The node talks to **Docs API v1** on `https://docs.googleapis.com` for create / get / `batchUpdate`, and to **Drive API v3** on `https://www.googleapis.com` for copy (`files.copy`), export (`files.export`), folder placement after create, and `listen` (`files.watch` or `changes.list` poll). There is no scrape and no Google client library — JDK HTTP only.

`documents.create` makes a **blank** document from `title` only; body fields in that request are ignored by Google. Content changes go through `documents.batchUpdate`. Placing a new doc in a folder is a Drive `files.update` with `addParents` after create (Docs create has no `folderId`).

**Dropped from catalog (not a Docs method, not listed as later):** `update`. Catalog seeded `update` plus `batch_update`. Docs REST v1 exposes only `documents.create`, `documents.get`, and `documents.batchUpdate`. Use `batch_update`.

Native Docs `documentId` is the same id as the Drive file.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create` | POST | `https://docs.googleapis.com/v1/documents` | Blank doc from title (Docs create has no folder field) |
| `get` | GET | `https://docs.googleapis.com/v1/documents/{documentId}` | Latest document JSON |
| `batch_update` | POST | `https://docs.googleapis.com/v1/documents/{documentId}:batchUpdate` | Atomic structural edits (`requests[]`) |
| `copy_via_drive` | POST | `https://www.googleapis.com/drive/v3/files/{fileId}/copy` | Duplicate a Doc (Drive copy) |
| `export` | GET | `https://www.googleapis.com/drive/v3/files/{fileId}/export?mimeType={mime}` | Export Workspace Doc to bytes stored as platform `file` |
| `listen` | POST or GET | Push: `POST https://www.googleapis.com/drive/v3/files/{fileId}/watch`. Poll: `GET https://www.googleapis.com/drive/v3/changes` (after `GET .../changes/startPageToken`) | Official Drive push, or one poll cycle. No Docs watch method. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get` | no | Row in the table above |
| `auth_type` | select | yes | `service_account_json` | no | `oauth2` \| `service_account_json` |
| `oauth_client_id` | string | if `oauth2` | empty | no | Installed/web OAuth client id |
| `oauth_client_secret` | password | if `oauth2` | empty | yes | OAuth client secret |
| `refresh_token` | password | if `oauth2` | empty | yes | Long-lived refresh token |
| `service_account_json` | password | if `service_account_json` | empty | yes | Downloaded SA key JSON (`client_email` + PKCS8 `private_key`) |
| `delegated_user` | string | no | empty | no | Workspace user email for SA JWT `sub` (domain-wide delegation). Empty = SA acts as itself; file must be shared with `client_email` |
| `oauth_scopes` | string | no | see Auth | no | Space-separated scope URIs. Prefer `drive.file` (non-sensitive). Do not request `drive` / `drive.readonly` unless the operator’s Cloud project is verified for restricted Drive |
| `timeout_seconds` | number | no | `25` | no | Connect+read timeout for token POST and each Google call. `export` may use this same budget |
| `max_export_bytes` | number | no | `10485760` | no | Cap on `files.export` body (Docs export is often limited near 10 MiB). Fail closed if exceeded |
| `listen_mode` | select | no | `poll` | no | `poll` \| `push`. `push` needs a public HTTPS receiver (StudioQuack message-event ingress). Google rejects self-signed TLS |

### Auth (mint token, then Bearer)

Token URL (hardcode): `POST https://oauth2.googleapis.com/token`.

- **OAuth2:** `application/x-www-form-urlencoded` `grant_type=refresh_token` + `client_id` + `client_secret` + `refresh_token`. Read `access_token`.
- **Service account:** compact JWT RS256 (JDK `KeyFactory` / `SHA256withRSA`), same pattern as shipped Google Sheets. Claims: `iss=client_email`, `scope=<oauth_scopes>`, `aud=https://oauth2.googleapis.com/token`, `iat`, `exp=iat+3600`. If `delegated_user` is set, add `sub=<email>`. Exchange with `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` and `assertion=<jwt>`. Do not cache the access token across invokes.

**Default scopes** (if `oauth_scopes` blank):

| Need | Scope | Sensitivity |
|---|---|---|
| Create / get / batchUpdate + per-file Drive | `https://www.googleapis.com/auth/drive.file` | Non-sensitive (recommended) |
| Docs-wide (all user Docs) | `https://www.googleapis.com/auth/documents` | Sensitive |
| Docs read-all | `https://www.googleapis.com/auth/documents.readonly` | Sensitive |
| All Drive files | `https://www.googleapis.com/auth/drive` | **Restricted** |
| All Drive read/download | `https://www.googleapis.com/auth/drive.readonly` | **Restricted** |

Share the Doc with the SA `client_email` unless using domain-wide delegation. Enable **Google Docs API** and **Google Drive API** on the Cloud project.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or key JSON |

FUNCTION ports: none.

File payloads: `export` output `file` is a platform ref-id. Never put bytes on INPUT or in config.

## Input fields (by operation)

Common: `document_id` (string) overrides nothing on `create`. For other ops, required unless the copy/export field names below apply. Document id charset: `[A-Za-z0-9_-]+`, length ≥ 20, reject placeholder tokens (`PLACEHOLDER`, `YOUR_`, `EXAMPLE`, `TODO`).

### `operation = create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Docs `title`. Other Document fields are ignored by Google |

### `operation = get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Path `{documentId}` |
| `include_tabs_content` | boolean | no | Query `includeTabsContent` (Docs tabs). Default false |
| `suggestions_view_mode` | string | no | Docs `suggestionsViewMode` if needed |

### `operation = batch_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Path `{documentId}` |
| `requests` | array | yes | Docs `Request` objects (`insertText`, `deleteContentRange`, `replaceAllText`, …). Cap length (e.g. 500) and JSON size 1 MiB |
| `required_revision_id` | string | no | `writeControl.requiredRevisionId` — 400 if not current |
| `target_revision_id` | string | no | `writeControl.targetRevisionId` — merge with collaborators |

Do not invent a second mutation verb. Convenience “replace body with string” is still this operation (delete range + insertText in `requests`).

### `operation = copy_via_drive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Source Drive/Docs file id |
| `name` | string | no | Copy title |
| `folder_id` | string | no | Copy `parents: [folderId]` |
| `supports_all_drives` | boolean | no | Query `supportsAllDrives` (default true) |

### `operation = export`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Drive file id of the Doc |
| `mime_type` | string | yes | Drive export MIME. Docs: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.oasis.opendocument.text`, `application/rtf`, `text/plain`, `application/zip` (HTML), `application/epub+zip`, `text/markdown` |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | push on one file: yes | File to `files.watch`. Omit only for drive-wide poll |
| `listen_mode` | string | no | Override config: `poll` \| `push` |
| `webhook_address` | string | if push | HTTPS URL for Drive notifications (StudioQuack message-event ingress). Must be publicly trusted TLS |
| `channel_id` | string | if push | UUID, max 64 chars |
| `channel_token` | string | no | Echoed as `X-Goog-Channel-Token`. Not a Google OAuth token; still do not put secrets here |
| `expiration_ms` | number | no | Unix ms channel expiry |
| `page_token` | string | poll | Drive changes page token; if empty, call `changes/startPageToken` first |
| `include_removed` | boolean | no | Poll `includeRemoved` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `document_id` | string | create, get, batch_update, copy | Id |
| `title` | string | create, get, copy | Title / Drive `name` |
| `revision_id` | string | get, batch_update | Opaque revision; valid ~24 h |
| `document` | object | get | Docs Document JSON. Omit bulky tabs unless requested. Cap serialized size (e.g. 2_000_000 bytes) |
| `replies` | array | batch_update | `replies[]` aligned with `requests` |
| `file_id` | string | copy_via_drive | New Drive file id (same as document id) |
| `file` | file | export | Platform ref-id of exported bytes |
| `export_mime_type` | string | export | MIME used |
| `byte_size` | number | export | Stored size |
| `channel_id` | string | listen push | Channel id |
| `resource_id` | string | listen push | Needed for `channels.stop` |
| `expiration` | number | listen push | Unix ms |
| `changes` | array | listen poll | Drive change objects (ids, not file bytes) |
| `new_start_page_token` | string | listen poll | Persist for next poll |
| `error` | string | failure | Validation or `Google Docs API Error: HTTP …` / `Google Drive API Error: HTTP …` |
| `http_status` | number | HTTP error | Status from Google |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` when present |

## Legal

- [Google APIs Terms of Service](https://developers.google.com/terms)
- [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy) (Limited Use + additional requirements for sensitive/restricted scopes)
- [Docs API authorizing / scopes](https://developers.google.com/workspace/docs/api/auth)
- [Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) — `drive` / `drive.readonly` are restricted (OAuth verification + security assessment / CASA when storing or transmitting that data)
- [OAuth app verification](https://support.google.com/cloud/answer/9110914)
- **Quota (Docs, verified 2026-08-20):** reads 3,000/min/project and 300/min/user/project; writes 600/min/project and 60/min/user/project. HTTP 429 → backoff. Standard use currently no extra Cloud charge; Google has announced possible billing later in 2026 with notice — [Docs usage limits](https://developers.google.com/workspace/docs/api/limits), [Workspace agent/API quota model](https://developers.google.com/workspace/tools-safety)
- Drive copy/export/watch consume **Drive** quota units ([Drive usage limits](https://developers.google.com/workspace/drive/api/guides/limits)): 1,000,000 units/min/project, 325,000/min/user; list=100, read=5, edit=50, download=200
- **PII:** document body, titles, and comments may be personal data. Calls leave the customer sandbox to Google. Customer is Cloud project owner and controller. Do not put ePHI in Docs unless the customer’s Google/BAA posture covers that project
- Official-API-first: no HTML scrape of docs.google.com

## Implementation notes

- JDK `HttpClient` only. No Google API client JARs.
- Hosts: `oauth2.googleapis.com`, `docs.googleapis.com`, `www.googleapis.com`. Honor daemon SSRF/egress.
- Timeouts: default 25 s (match Sheets). `export` of large Docs: still bounded by `timeout_seconds` and `max_export_bytes`.
- Retry: truncated exponential backoff on 429 and 5xx only (Google’s documented algorithm: 1, 2, 4, … + jitter, cap 32–64 s, finite attempts). Do not retry 400/401/403/404.
- HTTP 4xx/5xx → `{ error, http_status }` on OUTPUT. Never throw past dispatch.
- Empty/missing secrets → `{ error }` with no HTTP.
- Unknown `operation` → `{ error: "Unknown operation: …" }`.
- `update` if sent → `{ error: "Unknown operation: update. Use batch_update (documents.batchUpdate). There is no documents.update." }`.
- Redact `private_key`, JWT assertion, `access_token`, `refresh_token`, `oauth_client_secret`, raw SA JSON to `***` in errors and logs. Log operation, document id, HTTP status, byte sizes — never document body.
- `export`: stream to the platform file store; return ref-id. Do not base64 the file onto OUTPUT.
- `listen` push: Drive POST to `webhook_address` has empty body and `X-Goog-*` headers (`sync`, `add`, `remove`, `update`, `trash`, `untrash`). Ingress must answer 2xx. Renew channels before expiry (no auto-renew). Stop: `POST https://www.googleapis.com/drive/v3/channels/stop` with `{ id, resourceId }` — not a catalog operation; implement as part of listen teardown or a future op if needed.
- `listen` poll: persist `newStartPageToken`. Do not scrape the Docs UI.
- Enable APIs in Cloud Console: Docs + Drive.
