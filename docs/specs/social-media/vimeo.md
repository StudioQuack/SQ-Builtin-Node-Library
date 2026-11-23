# Vimeo

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/vimeo/`
- **Researched:** 2026-08-20
- **Official API:** Vimeo REST API **3.4** (changelog latest patch **3.4.10**, 2023-10-26; 3.4 remains the current version header)
- **Docs:** https://developer.vimeo.com/api/guides/start · https://developer.vimeo.com/api/reference · https://developer.vimeo.com/api/changelog
- **Auth:** oauth2 — secret bindings: `client_secret`, `access_token` (`client_id` is not a secret)

## Scope

One node for Vimeo video hosting. The designer picks `operation`; the handler calls `https://api.vimeo.com` with `Authorization: bearer {access_token}` and `Accept: application/vnd.vimeo.*+json;version=3.4`. Upload uses the documented **tus** (resumable) approach, with optional **pull** when the source is already a public URL. Folders are the same resource the API still paths as `projects`. `trigger` registers **app webhooks** (documented under API Apps).

Do not scrape vimeo.com HTML. Do not call unofficial “complete upload” shims — after tus, completeness is `Upload-Offset == Upload-Length` on `HEAD` to `upload.upload_link`. Official PHP/Python/Node SDKs are not used (JDK HTTP only).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `video_get` | GET | `/videos/{video_id}` | One video (clip representation) |
| `video_list` | GET | `/me/videos` | Authenticated user’s videos (paginated) |
| `video_upload` | POST then PATCH | `/me/videos` then `{upload.upload_link}` | Create placeholder + tus binary upload |
| `folder_list` | GET | `/me/projects` | List folders (projects) owned by the token user |
| `trigger` | POST / GET / DELETE | App webhooks (`/webhooks` — confirm live path on [API Apps](https://developer.vimeo.com/api/reference/api-apps)) | Register, list, or delete an app webhook; inbound POST is the listen path |

Base origin: `https://api.vimeo.com`. OAuth token URL: `https://api.vimeo.com/oauth/access_token` (authorization code / client credentials / device — see authentication guide). Prefer `fields=` query filtering; Vimeo **doubles** the per-minute quota when `fields` is present.

Retired: pre-3.4 `type` / `link` / `redirect_url` on POST `/me/videos` (replaced by `upload.approach`). Unofficial site scrapers omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `video_get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | OAuth client identifier. Not a secret. |
| `client_secret` | password | yes | empty | yes | OAuth client secret. Never on INPUT. |
| `access_token` | password | yes | empty | yes | Bearer token (`upload`+`edit` for uploads; `private` for private videos). Never on INPUT. |
| `timeout_seconds` | number | no | `60` | no | Connect + request timeout. Floor 1 s. Upload PATCH may use `upload_timeout_seconds`. |
| `upload_timeout_seconds` | number | no | `300` | no | tus PATCH timeout. Floor 30 s. |
| `page_size` | number | no | `25` | no | Default `per_page` for list ops (Vimeo max 100). |
| `webhook_secret` | password | no | empty | yes | Shared secret if the live webhook API documents HMAC; otherwise unused. Never logged. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = video_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `video_id` | string | yes | Numeric id or URI tail (`258684937`). Strip `/videos/` prefix if present. |
| `fields` | string | no | Comma-separated field filter (e.g. `name,uri,status`). |

### `operation = video_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | 1-based page |
| `per_page` | number | no | Else config `page_size`. Clamp 1–100 |
| `query` | string | no | Search within the user’s videos |
| `fields` | string | no | Field filter |
| `sort` | string | no | As documented (`date`, `alphabetical`, …) |
| `direction` | string | no | `asc` \| `desc` |

### `operation = video_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pull_url` | string | no | If set, use `upload.approach=pull` + `upload.link` instead of tus. |
| `name` | string | no | Video title |
| `description` | string | no | Description |
| `privacy_view` | string | no | `privacy.view` (`anybody`, `nobody`, `password`, `disable`, … as documented) |
| `folder_id` | string | no | Move into folder after create if the live folders API documents it |

tus steps the handler must perform: POST `/me/videos` with `{ "upload": { "approach": "tus", "size": <bytes> } }` → PATCH the file bytes to `upload.upload_link` with `Tus-Resumable: 1.0.0`, `Upload-Offset: 0`, `Content-Type: application/offset+octet-stream` → HEAD until `Upload-Offset` equals `Upload-Length`. Max file size **300 GB**, duration **24 h** (provider). Cap handler read of the file ref at a daemon-configured max (recommend 4 GiB default unless the operator raises it).

### `operation = folder_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | 1-based page |
| `per_page` | number | no | Clamp 1–100 |
| `fields` | string | no | Field filter |
| `project_id` | string | no | If set, GET `/me/projects/{project_id}` (one folder) instead of the collection |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `subscribe` \| `unsubscribe` \| `list` \| `ingest` |
| `callback_url` | string | `subscribe` | HTTPS URL of StudioQuack message-event ingress |
| `webhook_id` | string | `unsubscribe` | Provider webhook id |
| `events` | array | `subscribe` | Event names as documented on API Apps / app-webhooks (e.g. video transcode complete). Empty → `{ error }` no HTTP. |
| `payload` | object | `ingest` | Raw JSON body of an inbound Vimeo POST |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `video_id` | string | get / upload | Numeric id parsed from `uri` |
| `uri` | string | get / upload | e.g. `/videos/123` |
| `name` | string | get / upload | Title |
| `link` | string | get / upload | Public page URL when present |
| `upload_status` | string | upload | `upload.status` after HEAD complete |
| `videos` | array | `video_list` | Normalized `{ video_id, name, uri, link }` |
| `folders` | array | `folder_list` | Normalized `{ project_id, name, uri }` |
| `folder` | object | `folder_list` + `project_id` | Single folder |
| `webhooks` | array | `trigger` list | Provider webhook rows |
| `event` | object | `trigger` ingest | `{ type, video_id, raw }` — `raw` capped, no tokens |
| `page` | number | list | Echo |
| `total` | number | list | `total` from Vimeo paging |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `X-RateLimit-Reset` / `Retry-After` when present; else 60 on 429, 30 on 5xx/timeout |

## Legal

- Developer addendum / API: https://developer.vimeo.com/guidelines/terms (confirm live) · [Authentication](https://developer.vimeo.com/api/authentication) · [Rate limits](https://developer.vimeo.com/guidelines/rate-limiting) · [Uploads](https://developer.vimeo.com/api/upload/videos) · [Folders](https://developer.vimeo.com/api/guides/folders) · [App webhooks](https://developer.vimeo.com/api/app-webhooks)
- Operator must have a Vimeo developer app. Free accounts need **approved API upload access**.
- Rate limits (authenticated token, **without** `fields`): Free 25 / Starter 125 / Standard 250 / Advanced 750 / Studio 1500 / Enterprise 2500 requests per user per minute. Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Over-quota → HTTP **429**, error code **9000**. Vimeo forbids scraping; this node never fetches vimeo.com HTML.
- Uploaded videos and webhook payloads may contain personal names, faces, and private media. Minimize fields; do not log descriptions or file bytes.
- Scopes used: `public`, `private`, `upload`, `edit`, `create`, `delete`, `interact` as required per op.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers on every API call: `Authorization: bearer {access_token}`, `Accept: application/vnd.vimeo.*+json;version=3.4`, `User-Agent: StudioQuack/1.0`. JSON bodies: `Content-Type: application/json`.
- Empty `access_token` → `{ error: "Missing Vimeo access token. Bind access_token in node parameters." }` — no HTTP.
- Response body cap **1_000_000** bytes (JSON). tus PATCH streams the file ref; do not buffer the whole video in heap. JSON timeout default 60 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }` from `X-RateLimit-Reset` when parseable. Do not auto-retry inside the handler.
- Logs: operation, status, video_id, byte sizes — never `access_token`, `client_secret`, or file bytes.
- **Unverified at fetch time:** exact POST path and body for “Add a webhook for an app” (the [API Apps](https://developer.vimeo.com/api/reference/api-apps) Try-it panel did not serialize in the fetch). Implementer must copy method/path from that page before shipping `trigger` subscribe. Inbound listen still requires HTTPS ingress (StudioQuack webhook relay / Pro daemon).
