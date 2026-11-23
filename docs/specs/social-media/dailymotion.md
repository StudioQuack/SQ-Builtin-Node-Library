# Dailymotion

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/dailymotion/`
- **Researched:** 2026-08-20
- **Official API:** Dailymotion API **v2** (`2.0.0` OpenAPI)
- **Docs:** https://developers.dailymotion.com/reference/perform-an-api-call · https://developers.dailymotion.com/docs/authenticate · https://developers.dailymotion.com/docs/upload-videos
- **Auth:** oauth2 (Bearer) — secret bindings: `client_secret`, `access_token` (`client_id` is not a secret)

## Scope

One node for Dailymotion Studio content. Primary surface is REST **API v2** at `https://api.dailymotion.com/v2`. Tokens come from `POST https://oauth2.dailymotion.com/v2/token` (client credentials documented; authorization-code also exists). Access tokens expire in **1800 seconds**. Do **not** implement the Legacy API as the primary path (no `/video/` without `/v2`, no Graph-style `fields` on the old host as the default).

Covers videos (including `file` upload), livestreams, playlists, and profile (channel) read. `trigger` patches the nested `webhook` object on a Profile (`profile.manage`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `video_get` | GET | `/v2/videos/{video_id}` | One video (`video.read`) |
| `video_list` | GET | `/v2/profiles/{profile_id}/videos` | List profile videos (`video.read`) |
| `video_create` | POST (+ upload session) | `/v2/files/upload_sessions` then POST file then `/v2/profiles/{profile_id}/videos` | Create + publish. Media type `file` or `source.file_url` |
| `livestream_get` | GET | `/v2/livestreams/{livestream_id}` | One livestream (`live.read`) |
| `livestream_create` | POST | `/v2/profiles/{profile_id}/livestreams` | Create livestream (`live.manage`) |
| `playlist_get` | GET | `/v2/playlists/{playlist_id}` | One playlist (`playlist.read`) |
| `playlist_add` | POST | `/v2/playlists/{playlist_id}/videos` | Add a video to a playlist (`playlist.manage`) |
| `channel_get` | GET | `/v2/profiles/{profile_id}` or `/v2/me` | Profile = channel. `/v2/me` lists manageable profiles (`account.read`) |
| `trigger` | PATCH / GET | `/v2/profiles/{profile_id}` (`webhook` object) | Subscribe / clear / read webhook; `ingest` for inbound POST |

Base origin: `https://api.dailymotion.com/v2`. Prefer `fields` query for sparse JSON.

Legacy API is omitted, not listed as “later”.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `video_get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | OAuth API key from Dailymotion Studio. Not a secret. |
| `client_secret` | password | yes | empty | yes | OAuth API secret. Never on INPUT. |
| `access_token` | password | yes | empty | yes | Bearer. Handler may refresh via client credentials using `client_id`/`client_secret` + `scope` when `expires_in` elapsed — store refresh only in the secret store, never on OUTPUT. |
| `oauth_scope` | string | no | `bundle.publisher` | no | Space-separated scopes or a documented bundle (`video.manage live.manage playlist.manage profile.manage account.read …`) |
| `default_profile_id` | string | no | empty | no | Default `profile_id` when input omits it |
| `timeout_seconds` | number | no | `30` | no | JSON calls. Floor 1 s |
| `upload_timeout_seconds` | number | no | `300` | no | Multipart POST to `upload_url`. Floor 30 s |

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
| `video_id` | string | yes | Provider video id |
| `fields` | string | no | Comma-separated sparse fields |

### `operation = video_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | yes if config default empty | Profile that owns the videos |
| `fields` | string | no | Sparse fields |
| `sort` | string | no | Default provider `created_at` desc |
| `visibility` | string | no | Filter when documented |

### `operation = video_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | yes if config default empty | Target profile |
| `file` | file | yes unless `file_url` | StudioQuack file ref-id (`multipart/form-data` field `file` to `upload_url`) |
| `file_url` | string | yes unless `file` | External or prior upload `url`. Account must be entitled for external URL ingest |
| `title` | string | yes | 1–255 characters |
| `category` | string | yes | e.g. `news`, `sport` |
| `visibility` | string | yes | `public` \| `private` \| `password` |
| `is_for_kids` | boolean | yes | COPPA / GDPR kids flag |
| `description` | string | no | Metadata |
| `tags` | array | no | String tags |

Upload sequence: POST `/v2/files/upload_sessions` (empty body) → POST `upload_url` with `file` → POST `/v2/profiles/{profile_id}/videos` with `source.file_url`. If `file_url` is already set, skip the session. Re-acquire `upload_url` if the upload POST fails. Upload activity must be seen at least every **4 hours**.

### `operation = livestream_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `livestream_id` | string | yes | Livestream id |
| `fields` | string | no | Sparse fields |

### `operation = livestream_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | yes if config default empty | Target profile |
| `title` | string | yes | Title |
| `category` | string | yes | Category |
| `visibility` | string | yes | `public` \| `private` \| `password` |
| `is_for_kids` | boolean | yes | Kids flag |

### `operation = playlist_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `playlist_id` | string | yes | Playlist id |
| `fields` | string | no | Sparse fields |

### `operation = playlist_add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `playlist_id` | string | yes | Playlist |
| `video_id` | string | yes | Existing video |
| `before_video_id` | string | no | Insert before this item |

### `operation = channel_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | no | If omitted, GET `/v2/me` |
| `fields` | string | no | Sparse fields (`webhook` only on trigger) |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `subscribe` \| `unsubscribe` \| `get` \| `ingest` |
| `profile_id` | string | yes except `ingest` | Profile (Owner role for subscribe/unsubscribe) |
| `callback_url` | string | `subscribe` | **HTTPS only** |
| `events` | array | `subscribe` | At least one of `video.created`, `video.deleted`, `video.published`, `video.complete`, `video.format.processing`, `video.format.ready`, `video.format.error`, `video.format.deleted` |
| `payload` | object | `ingest` | Inbound JSON `{ type, timestamp, data }` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `video_id` | string | video ops | Id |
| `title` | string | video / live / playlist | Title |
| `videos` | array | `video_list` | `{ video_id, title, created_at }` |
| `livestream_id` | string | live ops | Id |
| `playlist_id` | string | playlist ops | Id |
| `profile_id` | string | channel / me | Profile id |
| `profiles` | array | `channel_get` via `/me` | Manageable profiles |
| `webhook` | object | `trigger` get | `{ callback_url, events }` — URL only, no secrets |
| `event` | object | `trigger` ingest | `{ type, timestamp, video_id, data }` |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- [Perform an API call](https://developers.dailymotion.com/reference/perform-an-api-call) · [Authentication](https://developers.dailymotion.com/docs/authenticate) · [Upload](https://developers.dailymotion.com/docs/upload-videos) · [Webhooks](https://developers.dailymotion.com/docs/webhooks) · [Scopes](https://developers.dailymotion.com/reference/api-scopes) · [Rate limiting](https://developers.dailymotion.com/reference/rate-limiting)
- Operator accepts Dailymotion developer / Studio terms. `is_for_kids` is required for COPPA/GDPR.
- Upload caps (published 2026-06-24): Standard/Pro trial/starter/advanced — **2 h / 4 GB** per video, **15 videos / 10 h** per 24 h. Premium / Pro Enterprise — **64 GB**, **96 videos / 24 h**. Request QPS is not published as a single number on that page; honor HTTP 429.
- Webhook delivery IPs (allowlist if used): `3.254.17.195`, `63.33.129.201`, `18.202.188.16`. Verify `X-DM-Signature` **before** processing. Signature algorithm: follow the live webhooks page (header is documented; algorithm details were not fully expanded in the 2026-08-20 fetch — implementer must copy the verification recipe from that page).
- Do not scrape dailymotion.com. Do not ship Legacy API as default.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- API: `Authorization: Bearer {access_token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty token → `{ error: "Missing Dailymotion access token. Bind access_token in node parameters." }` — no HTTP.
- JSON body cap **1_000_000** bytes. File upload streams the ref; do not heap-buffer. Timeout 30 s JSON / 300 s upload.
- 401 with expired JWT → one token refresh via client credentials, then retry **once**. Other 4xx → `{ error }` truncated 240 chars, secrets redacted `***`.
- Do not auto-retry 429 in-handler.
- Logs: operation, status, ids, sizes — never tokens or file bytes.
- Ingest `callback_url` must be StudioQuack HTTPS ingress (self-hosted relay or Pro daemon).
