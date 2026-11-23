# Threads

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/threads/`
- **Researched:** 2026-08-20
- **Official API:** Threads API **v1.0** (Meta; **not** Facebook Graph)
- **Docs:** https://developers.facebook.com/docs/threads/overview/ — Posts: https://developers.facebook.com/docs/threads/posts/
- **Auth:** oauth2 (Threads user token) — secret binding: `threads_user_token` (`app_secret` for webhook HMAC)

## Scope

One node for the Threads API on `https://graph.threads.net/v1.0/` (also `https://graph.threads.com/v1.0/`). Publishing is **container then publish**: `POST /{threads-user-id}/threads` then `POST /{threads-user-id}/threads_publish`. Official media ingest is a **publicly reachable URL** (Meta fetches it) via `image_url` / `video_url`. Scopes: `threads_basic`, `threads_content_publish`, `threads_manage_replies`, `threads_read_replies`, `threads_manage_insights`. Profile cap **250 published posts / 24 h** (carousels count as one). Do not call `graph.facebook.com` for Threads objects. Instagram remains its own shipped node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `thread_create` | POST + POST | `/v1.0/{user-id}/threads` then `/v1.0/{user-id}/threads_publish` | Text / image / video / carousel post |
| `thread_get` | GET | `/v1.0/{threads-media-id}` or `/v1.0/{user-id}/threads` | One media object or user media list |
| `reply_create` | POST + POST | `/v1.0/{user-id}/threads` (`reply_to_id`) then `threads_publish` | Reply to a thread |
| `insights_get` | GET | `/v1.0/{threads-media-id}/insights` or `/v1.0/{user-id}/threads_insights` | Media or user insights |
| `trigger` | GET verify + POST events | Threads webhooks | `replies`, `mentions`, `publish`, `delete` |

Check quota via `GET /{threads-user-id}/threads_publishing_limit` before publish when `quota_usage` would exceed `quota_total` (250 / 86400 s). Wait until container `status=FINISHED` (often ~30 s; poll `GET /{container-id}` at most ~1/min for 5 min).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `thread_create` | no | Chooses the row above |
| `threads_user_token` | password | yes | empty | yes | Threads access token. Never on INPUT. |
| `app_secret` | password | no | empty | yes | HMAC for webhook `X-Hub-Signature-256` |
| `threads_user_id` | string | no | empty | no | Default user id; else `me` |
| `api_host` | select | no | `https://graph.threads.net` | no | Alternate `https://graph.threads.com` |
| `timeout_seconds` | number | no | `30` | no | Per HTTP call. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = thread_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes unless image/video URL | Post text (official max **500** characters) |
| `media_type` | string | no | `TEXT` \| `IMAGE` \| `VIDEO` \| `CAROUSEL` |
| `image_url` / `video_url` | string | no | Already-public URL if the operator supplies one |
| `children` | array | CAROUSEL | Child container ids (2–20). Create children first |
| `reply_control` | string | no | Documented reply audience including `parent_post_author_only`, `followers_only` |

### `operation = thread_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media_id` | string | yes unless listing | Threads media id |
| `fields` | string | no | e.g. `id,text,media_type,permalink,timestamp` |
| `limit` | number | no | List page size when `media_id` omitted |

### `operation = reply_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `reply_to_id` | string | yes | Parent media id |
| `text` | string | yes unless image/video URL | Reply text |

### `operation = insights_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media_id` | string | no | If set, media insights; else user `threads_insights` |
| `metric` | string | yes | Media or user metrics (`views`, `likes`, `replies`, `reposts`, `quotes`, `clicks`, `shares`, `followers_count`, …) |
| `since` / `until` | number | no | Unix; earliest **1712991600** (2024-04-13). Default range ~2 days if omitted |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | yes | `verify` \| `event` |
| `hub_mode` / `hub_verify_token` / `hub_challenge` | string | `verify` | Subscription handshake |
| `payload` | object | `event` | Webhook JSON; verify signature with `app_secret` over raw bytes |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `container_id` | string | create/reply (step 1) | Media container |
| `id` | string | published | Threads media id |
| `thread` | object | `thread_get` | Media fields |
| `insights` | array | `insights_get` | Metric data |
| `quota` | object | optional on create | `quota_usage` / `quota_total` if fetched |
| `event` | object | `trigger` | Topic payload (`replies`, `publish`, …) |
| `error` | string | failure | Actionable; no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

## Legal

- Overview / rate limits: https://developers.facebook.com/docs/threads/overview/
- Posts (container + publish): https://developers.facebook.com/docs/threads/posts/
- Insights: https://developers.facebook.com/docs/threads/insights/
- Webhooks: https://developers.facebook.com/docs/threads/webhooks/
- Troubleshooting / quotas: https://developers.facebook.com/docs/threads/troubleshooting/
- Meta Platform Terms: https://developers.facebook.com/terms
- **250** API-published posts / 24 h per profile; replies/deletes have separate quota objects on `threads_publishing_limit`. App call-count also uses impressions formula (min impressions 10). `follower_demographics` needs ≥100 followers. Content and handles are personal data — do not log post text. App Review + Threads permissions required for live. No web scrape of threads.net.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Hosts: `graph.threads.net`, `graph.threads.com` only.
- Query or header token as Threads docs specify; never log it. Empty token → `{ error }` no HTTP.
- `thread_create` / `reply_create` are **two** HTTP calls in one operation (not FUNCTION ports). Poll container until `FINISHED` or `ERROR`; cap polls (≤5 minutes, ≥30 s first wait). Do not tight-loop.
- If only `file` is set and no public URL can be minted, return `{ error }` — do not upload to Facebook Graph photo edges.
- JSON cap **1_000_000** bytes. Timeout 30 s per call. 429/5xx → `{ error, http_status, retry_after_seconds }`; **no unbounded retry**.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, media ids, container status, sizes — never tokens or text bodies.
- Webhook HMAC same pattern as Meta Graph (`X-Hub-Signature-256`).
