# Sprout Social

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/sprout-social/`
- **Researched:** 2026-08-20
- **Official API:** Sprout **Public API** major version **v1** (`https://api.sproutsocial.com`) — responses include `X-Sprout-API-Version` (e.g. `1.1`)
- **Docs:** https://api.sproutsocial.com/docs/ — Support https://support.sproutsocial.com/hc/en-us/articles/360045006152-Sprout-Public-API
- **Auth:** api_key **or** oauth2 — secret binding: `access_token` (Bearer). Account-scoped token from Settings → Global Features → API, **or** OAuth 2.0 JWT (machine-to-machine client credentials to `https://identity.sproutsocial.com/oauth2/84e39c75-d770-45d9-90a9-7b79e3037d2c/v1/token`, scope `organization_id`; discovery URL documented on the API docs).

## Scope

Documented **Public API** for customers on a plan that includes API access (support: **Advanced** + **API Permissions** granted by an Account Owner; in-app Analytics API ToS). This is **not** a hidden partner-only scrape. Publishing: `POST /v1/{customer_id}/publishing/posts` currently creates **draft** posts only (`is_draft: true` required). Retrieve: `GET /v1/{customer_id}/publishing/posts/{publishing_post_id}`. Analytics: `POST .../analytics/profiles` and `POST .../analytics/posts`. Catalog `message_create` / `message_get` map to **publishing** (not inbox). Inbox is a different endpoint (`POST .../messages`) — omit unless catalog adds it. Catalog `trigger` omitted: no webhook API on the public docs fetched 2026-08-20. Media uploads are official (`POST .../media/` under 50 MB).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `message_create` | POST | `/v1/{customer_id}/publishing/posts` | Create a **draft** publishing post (`is_draft: true`) |
| `message_get` | GET | `/v1/{customer_id}/publishing/posts/{publishing_post_id}` | Retrieve by id (delivery_status may stay `PENDING` even after send — docs) |
| `analytics_get` | POST | `/v1/{customer_id}/analytics/profiles` or `/analytics/posts` | Owned profile or post analytics |
| `media_upload` | POST | `/v1/{customer_id}/media/` | Single-request upload under 50 MB for `media_id` on create |

Path form: `/{version}/{customer ID}/{endpoint}` with version `v1`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `message_get` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Account token or OAuth JWT. Header `Authorization: Bearer`. Never on INPUT. |
| `customer_id` | string | yes | empty | no | Sprout customer id (`GET /v1/metadata/client`) |
| `client_id` | string | no | empty | no | OAuth M2M |
| `client_secret` | password | no | empty | yes | OAuth M2M |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. Upload may use 60 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `media` type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = message_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `group_id` | number | yes | Sprout group |
| `customer_profile_ids` | array | yes | Profile ids in that group |
| `text` | string | no | Body (per-network rules) |
| `is_draft` | boolean | yes | Must be `true` (only draft create is supported) |
| `scheduled_times` | array | no | ISO-8601 UTC future times; seconds rounded down to the minute |
| `delivery_type` | string | no | Only `SCHEDULED` when scheduling a draft |
| `media_ids` | array | no | `{ media_id, media_type }` PHOTO \| VIDEO \| DOCUMENT |
| `tag_ids` | array | no | Optional tags |

Instagram Mobile Publisher / story posts cannot be created directly (docs). Unsupported media for a profile is **dropped without error** (fan-out).

### `operation = message_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `publishing_post_id` | string | yes | Calendar post id |

### `operation = analytics_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `kind` | string | yes | `profiles` \| `posts` |
| `filters` | object | yes | JSON body as documented (profiles, date range, metrics). Cap date range; 504 if too large |

### `operation = media_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | file | yes | Platform ref-id. Cap **50_000_000** bytes |
| `media_type` | string | no | Hint PHOTO/VIDEO/DOCUMENT |

Multipart submission endpoints exist for larger files; v1 of this node uses the simple `POST .../media/` path only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 200 | `ok` |
| `posts` | array | create / get | Fan-out publishing objects (`publishing_post_id`, permalink, deliveries) |
| `analytics` | object | `analytics_get` | Provider JSON (cap body) |
| `media_id` | string | `media_upload` | Id for `message_create` |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Including **202** (not ready — media processing) |
| `retry_after_seconds` | number | 429 / 202 / 5xx / timeout | Honor `wait_until` on 202 media; 60 on 429; 30 on 5xx |

202 on media complete → `{ error }` with retry hint, not success.

## Legal

- Product overview (Advanced plan + API Permissions; representative provisioning also described on sproutsocial.com/insights): https://sproutsocial.com/insights/sprout-social-api/
- API docs / developer terms acknowledgement: https://api.sproutsocial.com/docs/
- Support (Advanced + API Permissions; X data extra EULA in-app): https://support.sproutsocial.com/hc/en-us/articles/360045006152-Sprout-Public-API
- **Rate limits:** **60 requests/minute**, **250,000 requests/month**. HTTP 429.
- Headers: `X-Sprout-Request-ID`, `X-Sprout-API-Version`.
- No paid ad-account data, no X listening, no Reddit listening message-level data (docs “does not currently include”).
- Publishing and analytics contain social PII and third-party content. X data requires accepting Sprout’s X content terms in the product. No scrape of app.sproutsocial.com.

## Implementation notes

- JDK `HttpClient` only. Base `https://api.sproutsocial.com`. `Accept` + `Content-Type: application/json`.
- Empty token or `customer_id` → `{ error }` no HTTP. Discover ids via `GET /v1/metadata/client` then `GET /v1/{customer_id}/metadata/customer` (profiles/groups) before publishing.
- `message_create` JSON: `group_id`, `customer_profile_ids`, `is_draft: true`, optional `text`, `media[]`, `delivery.scheduled_times` + `delivery.type=SCHEDULED`. Response is **fan-out** (one object per profile × send time).
- Response cap **1_000_000** bytes. Timeout 30 s (60 s upload).
- 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Truncate 240 chars; redact Bearer.
- Do not auto-retry (except operator-driven wait on documented 202 `wait_until` for media processing).
- JWT M2M: `grant_type=client_credentials` + `scope=organization_id` against the documented identity token URL. Authorization header accepts **both** account-scoped tokens and OAuth JWTs.
- Logs: operation, customer_id, HTTP status, `X-Sprout-Request-ID`, sizes — never tokens, email fields, or file bytes.
