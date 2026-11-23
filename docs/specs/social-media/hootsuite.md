# Hootsuite

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/hootsuite/`
- **Researched:** 2026-08-20
- **Official API:** Hootsuite REST API **1.0** (`https://platform.hootsuite.com/v1`)
- **Docs:** https://developer.hootsuite.com/docs/api-overview — Getting started https://developer.hootsuite.com/docs/getting-started-with-the-rest-api — Auth https://developer.hootsuite.com/docs/api-authentication — Schedule https://developer.hootsuite.com/docs/message-scheduling — Media https://developer.hootsuite.com/docs/uploading-media — Analytics overview https://developer.hootsuite.com/docs/analytics-overview — Rate limits https://developer.hootsuite.com/docs/api-rate-limits — Reference https://platform.hootsuite.com/docs/api/index.html
- **Auth:** oauth2 — secret bindings: `client_id`, `client_secret`, `access_token`, `refresh_token`. Authorization Code (`scope` includes `offline` for refresh). Token `POST https://platform.hootsuite.com/oauth2/token`. Custom grants `member_app` / `organization_app` exist for **installed** apps (org grant: Enterprise install).

## Scope

Enterprise-oriented social scheduler. `message_schedule` is `POST /v1/messages`; `message_list` is `GET /v1/messages` (outbound, max **4 weeks** window). Media: `POST /v1/media` → PUT bytes to the returned `uploadUrl` → attach `media[].id` on the message. `analytics_get` uses the Analytics API (`POST /v1/analytics/posts` and related) and requires the **Advanced Analytics** add-on plus OAuth scope **`analytics:read`**. `trigger` is Hootsuite **HTTP webhooks** (`com.hootsuite.messages.event.v1`), not a scrape. Publishing API **does not** support TikTok, YouTube, or Bluesky (overview, updated 2026-06-24). A **Developer** account may require Hootsuite approval (email when approved). First Dev Portal use can require a **developer review**. Honest: this is not an anonymous public API.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message_schedule` | POST | `/v1/messages` | Schedule text (+ optional media) to `socialProfileIds`. `scheduledSendTime` ISO-8601 **UTC ending in Z**, ≥ 5 minutes ahead (videos: ≥ 15 minutes; large videos: prefer ~1 hour) |
| `message_list` | GET | `/v1/messages` | Outbound messages; required `startTime` + `endTime` (end ≤ start + 4 weeks) |
| `analytics_get` | POST | `/v1/analytics/posts` | List published posts + lifetime metrics (Advanced Analytics). Profile daily metrics: `/v1/analytics/profiles` per using-the-api |
| `trigger` | inbound POST | Hootsuite webhook to the workflow listen URL | Message state changes (`com.hootsuite.messages.event.v1`) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message_list` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | App from Developer Portal |
| `client_secret` | password | yes | empty | yes | Token / member_app |
| `access_token` | password | yes | empty | yes | Bearer. Never on INPUT. |
| `refresh_token` | password | no | empty | yes | Refresh tokens are **single-use** |
| `member_id` | string | no | empty | no | `member_app` grant |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. Upload PUT may use 60 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `trigger` input is the webhook JSON. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message_schedule`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | Message body |
| `social_profile_ids` | array | yes | Hootsuite profile **id**s (not `socialNetworkId`) from `GET /v1/socialProfiles` |
| `scheduled_send_time` | string | yes | UTC ISO-8601 with `Z` |
| `email_notification` | boolean | no | Default false |

### `operation = message_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_time` | string | yes | ISO-8601 |
| `end_time` | string | yes | ISO-8601; not later than 4 weeks after start |
| `state` | string | no | `PENDING_APPROVAL` \| `REJECTED` \| `SENT` \| `SCHEDULED` \| `SEND_FAILED_PERMANENTLY` |
| `social_profile_ids` | array | no | Repeat query key |
| `limit` | number | no | Default 50; max 100 |
| `cursor` | string | no | Pagination |

### `operation = analytics_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `kind` | string | no | `posts` (default) \| `profiles` |
| `filters` | object | yes | Body as Analytics docs: social profile ids (**max 10** per call), reporting period (≤ 2 years history), network type |
| `limit` | number | no | Max 100 |
| `cursor` | string | no | Pagination |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | yes | Webhook body (`type`, `data.state`, `data.message.id`) |

Ping events (`com.hootsuite.ping.event.v1`): accept and return `{ status: "ok" }` without treating as a message.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `messages` | array | schedule / list | `data[]` (id, state, text, scheduledSendTime) |
| `analytics` | object | `analytics_get` | Provider JSON (cap body) |
| `event_type` | string | `trigger` | e.g. `com.hootsuite.messages.event.v1` |
| `message_id` | string | `trigger` | `data.message.id` |
| `state` | string | `trigger` | Message state |
| `error` | string | failure | Actionable, no secret |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx |

## Legal

- Developer portal: https://developer.hootsuite.com — first-time **developer review** may apply; community signup is approval-gated.
- API ToS: https://hootsuite.com/legal/api-terms-of-service — Developer terms also cited in OpenAPI as https://hootsuite.com/legal/dev-api-terms
- Rate limits: **20 requests/second**, **100,000 calls/day**. Headers `X-Account-Quota`, `X-Account-Quota-Used`, `X-Account-Rate-Limit-Requests-Remaining`. HTTP **429** body may include `code` 1003 “Account plan limit exceeded”.
- Analytics: user must have **Advanced Analytics**; app scope `analytics:read`. `POST /v1/analytics/posts` requires `profileId` in filters; `lastModified` ≤ 30 days; `reportingPeriod` ≤ 2 years. Max **10** social profile ids per analytics call. Paid analytics (`POST /v1/analytics/paid/{campaigns|adsets|ads}`) is a separate surface — do not fold it into organic `analytics_get`.
- Messages and analytics contain social-network PII and copyrighted posts. Destination-network ToS still apply. No dashboard scrape.

## Implementation notes

- JDK `HttpClient` only. Base `https://platform.hootsuite.com`. Authorize `https://platform.hootsuite.com/oauth2/auth`. Token `POST https://platform.hootsuite.com/oauth2/token`. Header `Authorization: Bearer {access_token}`. JSON `Content-Type: application/json;charset=utf-8` except media PUT to the returned `uploadUrl` (`Content-Type` + `Content-Length` must match `sizeBytes`).
- Resolve profile ids with `GET /v1/socialProfiles` (or `GET /v1/me`) before schedule. Empty token → `{ error }` no HTTP.
- Envelope: success `{ data, errors }`. Partial failure may return both. Map `errors[]` to `{ error }` (code, message, id) with secrets stripped.
- Response cap **1_000_000** bytes. Timeout 30 s (60 s upload).
- 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Truncate 240 chars; redact Bearer.
- Do not auto-retry. Honor 429 with `retry_after_seconds`.
- Webhooks: https://developer.hootsuite.com/docs/webhooks — `trigger` accepts `com.hootsuite.messages.event.v1` (and ignores ping). Message `webhookUrls` may be set on `POST /v1/messages` to push state changes.
- Logs: operation, status, message ids, sizes — never tokens or file bytes.
