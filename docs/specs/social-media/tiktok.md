# TikTok

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/tiktok/`
- **Researched:** 2026-08-20
- **Official API:** TikTok Content Posting API v2 + Display API v2 + Research API v2 (`open.tiktokapis.com`)
- **Docs:** https://developers.tiktok.com/doc/content-posting-api-get-started — Guidelines: https://developers.tiktok.com/doc/content-sharing-guidelines
- **Auth:** oauth2 — secret binding: `access_token` (`client_key`+`client_secret` for app/research client credentials)

## Scope

One node for official TikTok Open API: **Content Posting** (direct video/photo, inbox upload, status), **Display** (creator video list + per-video stats), and **Research** (academic client only). Host `https://open.tiktokapis.com`. Unaudited Content Posting clients are limited to **SELF_ONLY**, private accounts at post time, and **5 publishing users / 24 h**. Research endpoints require a Research API project and a **client** token (`clt.` prefix), not a user `act.` token.

Official APIs only. No unofficial mobile/web scrapers. No watermarks/logos on creator media (Content Sharing Guidelines).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `creator_info` | POST | `/v2/post/publish/creator_info/query/` | Required before direct post (privacy options, max duration) |
| `direct_post_video` | POST (+ PUT) | `/v2/post/publish/video/init/` then `upload_url` | Direct-post video (`video.publish`). `FILE_UPLOAD` or `PULL_FROM_URL` |
| `direct_post_photo` | POST | `/v2/post/publish/content/init/` | Photo carousel. Official path requires `PULL_FROM_URL` + verified domain; node `media` still type `file` |
| `upload_inbox` | POST (+ PUT) | `/v2/post/publish/inbox/video/init/` | Draft to creator inbox (`video.upload`) |
| `publish_status` | POST | `/v2/post/publish/status/fetch/` | Poll by `publish_id` |
| `video_list` | POST | `/v2/video/list/` | Display API list (`video.list`) |
| `insights_get` | POST | `/v2/video/query/` | Display stats: `like_count`, `comment_count`, `share_count`, `view_count` |
| `research_query` | POST | `/v2/research/video/query/` | Academic Research API only |
| `trigger` | inbound webhook | App-configured Content Posting webhooks | `post.publish.complete` / `failed` / `inbox_delivered` / `publicly_available` |

Photo `content/init` requires `post_mode=DIRECT_POST` and `media_type=PHOTO`. Do not use inbox video init for photos.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `creator_info` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | User token (`act.`) for posting/display; Research client token (`clt.`) for `research_query`. Never on INPUT. |
| `client_key` | string | no | empty | no | App key (audit/UX metadata only; not sent on user calls) |
| `client_secret` | password | no | empty | yes | App secret; never log. Needed if the handler exchanges tokens (not for per-run user calls) |
| `timeout_seconds` | number | no | `30` | no | JSON timeout. Floor 1 s. Uploads 120 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = creator_info`

No body fields. POST empty JSON with Bearer token.

### `operation = direct_post_video` | `upload_inbox`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | file | yes unless `video_url` | Local/daemon file → `FILE_UPLOAD` (type `file`) |
| `video_url` | string | yes unless `media` | `PULL_FROM_URL`; URL prefix must be verified in TT4D |
| `title` | string | no | Caption; max 2200 UTF-16 runes |
| `privacy_level` | string | yes (direct post) | Must be one of `privacy_level_options` from `creator_info`. Unaudited → effectively `SELF_ONLY` |
| `disable_comment` / `disable_duet` / `disable_stitch` | boolean | no | Honor creator_info disabled flags |
| `is_aigc` | boolean | no | AI-generated disclosure |
| `brand_content_toggle` / `brand_organic_toggle` | boolean | no | Commercial disclosure |

### `operation = direct_post_photo`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | file | yes (1..n) | Photo file(s). Handler must publish bytes at a **verified public URL** (API has no FILE_UPLOAD for photos) or fail clearly |
| `photo_images` | array | no | Alternate: already-verified public URLs |
| `title` / `description` | string | no | Photo post metadata |
| `privacy_level` | string | yes | Same rules as video |
| `auto_add_music` | boolean | no | Photo posts |

### `operation = publish_status`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `publish_id` | string | yes | From init |

### `operation = video_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `max_count` | number | no | Default 10, max 20 |
| `cursor` | number | no | UTC ms |
| `fields` | string | no | Default `id,title,cover_image_url,create_time` |

### `operation = insights_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `video_ids` | array | yes | Max 20 ids |
| `fields` | string | no | Default `id,like_count,comment_count,share_count,view_count,title` |

### `operation = research_query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | object | yes | Research condition tree (`and`/`or`/`not`, `EQ`/`IN`/…) |
| `start_date` / `end_date` | string | yes | `YYYYMMDD` |
| `max_count` | number | no | Default 10, max 100 |
| `cursor` / `search_id` | string/number | no | Pagination |
| `fields` | string | no | Query string `fields=` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | yes | `event` (webhooks are registered in TT4D, not via this REST call) |
| `payload` | object | yes | Webhook JSON (`publish_id`, `publish_type`, `reason`) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx and TikTok `error.code=ok` | `ok` |
| `creator` | object | `creator_info` | username, privacy_level_options, max_video_post_duration_sec |
| `publish_id` | string | init | Track status |
| `upload_url` | string | FILE_UPLOAD init | PUT target (valid ~1 h). Do not log query token |
| `post_status` | string | `publish_status` | `PROCESSING_*` / `PUBLISH_COMPLETE` / `FAILED` / `SEND_TO_USER_INBOX` |
| `videos` | array | list/insights | Display objects |
| `research` | object | `research_query` | videos + cursor + search_id |
| `event` | object | `trigger` | Webhook fields |
| `error` | string | failure | `error.message` / `error.code`; no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

Treat TikTok JSON `error.code != ok` as failure even when HTTP is 200.

## Legal

- Content Posting get started: https://developers.tiktok.com/doc/content-posting-api-get-started
- Direct post: https://developers.tiktok.com/doc/content-posting-api-reference-direct-post
- Status + webhooks: https://developers.tiktok.com/doc/content-posting-api-reference-get-video-status
- Inbox upload: https://developers.tiktok.com/doc/content-posting-api-reference-upload-video
- Guidelines (unaudited **5 users / 24 h**, SELF_ONLY, ~15 posts/creator/day): https://developers.tiktok.com/doc/content-sharing-guidelines
- Display list/query: https://developers.tiktok.com/doc/tiktok-api-v2-video-list — https://developers.tiktok.com/doc/tiktok-api-v2-video-query
- Research: https://developers.tiktok.com/doc/research-api-get-started
- Operator must complete TT4D app, scopes (`video.publish` / `video.upload` / `video.list`), and **audit** for public posts. Unaudited public posting is not available. Research API is academic-approval only — skip with a clear error if the token is not a research client. `video.init` **6 req/min** per user token; status fetch **30 req/min**. No scraping. Do not embed `client_secret` in client-side artifacts.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Hosts: `open.tiktokapis.com`, `open-upload.tiktokapis.com`.
- Headers: `Authorization: Bearer`, `Content-Type: application/json; charset=UTF-8`. FILE_UPLOAD PUT: `Content-Type` video/mp4|quicktime|webm, `Content-Range`, `Content-Length`.
- Call `creator_info` before direct post; reject duration above `max_video_post_duration_sec`; reject privacy not in `privacy_level_options`.
- Photos: API pull-from-URL only. Resolve `file` to bytes, then either operator-verified URL or `{ error }` explaining the verified-URL requirement — do not scrape TikTok CDN.
- Empty token → `{ error }` no HTTP. JSON cap **1_000_000** bytes. Video max **4 GB** (media transfer guide). `upload_url` TTL 1 hour.
- 429 `rate_limit_exceeded` → `{ error, http_status: 429, retry_after_seconds }` (default 60). Honor `Retry-After`. **No unbounded retry.** 403 `unaudited_client_can_only_post_to_private_accounts` / `spam_risk_too_many_posts` → `{ error }` no retry.
- HTTP 4xx/5xx (and non-ok TikTok error) → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, error.code, publish_id, sizes — never tokens, captions, or file bytes.
