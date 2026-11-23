# TikTok Ads

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/tiktok-ads/`
- **Researched:** 2026-08-20
- **Official API:** TikTok Marketing API / TikTok API for Business **v1.3** (`/open_api/v1.3/`)
- **Docs:** https://business-api.tiktok.com/portal/docs — Marketing API https://business-api.tiktok.com/portal/docs/marketing-api/v1.3 — rate limits https://business-api.tiktok.com/portal/docs/rate-limits/v1.3
- **Auth:** oauth2 — secret bindings: `appSecret`, `accessToken`, `refreshToken`

## Scope

One node for TikTok **Ads Manager** (campaigns, ad groups, ads, reporting, Instant Form leads, Events API). Base origin **`https://business-api.tiktok.com`**. Path version is **`v1.3`** (confirmed 2026-08-20: migrate-to-v1.3 docs, rate-limits/v1.3, create-ads/v1.3). Header `Access-Token` (not `Authorization: Bearer`). One OAuth grant can cover many `advertiser_id`s. **Customer owns the TikTok for Business / Business Center account.** Do not scrape Ads Manager HTML. Do not call Organic / Business Messaging APIs from this node.

`pixel.track` uses **Events API 2.0** `POST /open_api/v1.3/event/track/` (current). Legacy `POST /open_api/v1.3/pixel/track/` is not the default.

`trigger.listen` calls the official **Subscription API** so TikTok POSTs lead / ad-review (and related) events to StudioQuack message-event ingress: `POST /open_api/v1.3/subscription/subscribe/` (v1.3 rename of `/v1.2/subscription/subscribe/`). Docs: https://business-api.tiktok.com/portal/docs/create-a-subscription/v1.3

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `advertiser.list` | GET | `/open_api/v1.3/oauth2/advertiser/get/` | Advertisers authorized for this token |
| `campaign.create` | POST | `/open_api/v1.3/campaign/create/` | Create a campaign |
| `campaign.get` | GET | `/open_api/v1.3/campaign/get/` | One/few campaigns (`filtering` / ids) |
| `campaign.list` | GET | `/open_api/v1.3/campaign/get/` | Page campaigns for an advertiser |
| `adgroup.create` | POST | `/open_api/v1.3/adgroup/create/` | Create an ad group |
| `adgroup.list` | GET | `/open_api/v1.3/adgroup/get/` | List ad groups |
| `ad.create` | POST | `/open_api/v1.3/ad/create/` | Create an ad (manual ads) |
| `ad.list` | GET | `/open_api/v1.3/ad/get/` | List ads |
| `report.get` | GET | `/open_api/v1.3/report/integrated/get/` | Integrated reporting |
| `pixel.track` | POST | `/open_api/v1.3/event/track/` | Server-side conversion (Events 2.0) |
| `lead.list` | POST then GET | `/open_api/v1.3/page/lead/task/` then `/open_api/v1.3/page/lead/task/download/` | Instant Form leads (async task) |
| `trigger.listen` | POST | `/open_api/v1.3/subscription/subscribe/` | Subscribe `LEAD` / ad-review / etc. → SQ ingress (`callback_url`) |

Retired: v1.2 paths (`/v1.2/…`, `/pages/leads/task/`). Numeric ids in v1.2 are **strings** in v1.3.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `advertiser.list` | no | Chooses the row above |
| `appId` | string | yes | | no | Developer app id |
| `appSecret` | password | yes | | yes | App secret (token exchange / refresh only) |
| `accessToken` | password | yes | | yes | Header `Access-Token` |
| `refreshToken` | password | no | | yes | OAuth refresh |
| `advertiserId` | string | no | | no | Default `advertiser_id` (string in v1.3) |
| `timeout_seconds` | number | no | `30` | no | Connect + read timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens or lead PII in logs |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = advertiser.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Token-scoped advertiser list |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config `advertiserId` |
| `campaign_name` | string | yes | Name |
| `objective_type` | string | yes | e.g. `TRAFFIC`, `LEAD_GENERATION`, `WEB_CONVERSIONS` |
| `budget_mode` | string | no | As documented for the objective |
| `budget` | number | no | Campaign budget when required |
| `operation_status` | string | no | e.g. `ENABLE` / `DISABLE` |

### `operation = campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config |
| `campaign_ids` | string[] | yes | v1.3 string ids |

### `operation = campaign.list` | `adgroup.list` | `ad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config |
| `page` | number | no | Page ≥ 1 |
| `page_size` | number | no | Clamp to documented max |
| `filtering` | object | no | Provider filter object (ids, status) |

### `operation = adgroup.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config |
| `campaign_id` | string | yes | Parent campaign |
| `adgroup_name` | string | yes | Name |
| `placement_type` / `budget` / `bid_type` / `optimization_goal` | mixed | no | Required set depends on objective — pass through documented fields |
| `location_ids` | string[] | no | Targeting; also used to pick lead-download region |

### `operation = ad.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config |
| `adgroup_id` | string | yes | Parent ad group |
| `ad_name` | string | yes | Name |
| `creatives` | object | yes | Creative payload as `/ad/create/` documents |
| `tracking_pixel_id` | string | no | Pixel for offsite tracking |

### `operation = report.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Else config |
| `report_type` | string | yes | e.g. `BASIC` |
| `data_level` | string | yes | `AUCTION_CAMPAIGN` / `AUCTION_ADGROUP` / `AUCTION_AD` as documented |
| `dimensions` | string[] | yes | Report dimensions |
| `metrics` | string[] | yes | Report metrics |
| `start_date` / `end_date` | string | yes | `YYYY-MM-DD` |
| `page` / `page_size` | number | no | Pagination |

### `operation = pixel.track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_source` | string | yes | `WEB` / `APP` / `OFFLINE` / `CRM` |
| `event_source_id` | string | yes | Pixel code, app id, offline id, or CRM event id |
| `data` | object[] | yes | Events: `event`, `event_time`, `event_id`, `user` (hashed), `properties`. Minimize PII; hash emails/phones per Events API rules |
| `test_event_code` | string | no | Events Manager test code |

### `operation = lead.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `advertiser_id` | string | yes* | Or `library_id` after Business Center transfer |
| `library_id` | string | no | Mutually exclusive with advertiser for migrated libraries |
| `ad_id` | string | no | Either `ad_id` or `page_id` |
| `page_id` | string | no | Instant Form id (`business_type=LEAD_GEN`) |
| `lead_region` | string | no | Header `x-lead-region`: `eu` for EEA/CH/UK storage, `us` otherwise (official lead-task docs). Do not use deprecated `ads.us.tiktok.com` host |

Handler: POST task (omit `task_id`) → poll same path with `task_id` until `SUCCEED` → GET download. Cap wait; on timeout `{ error }` with `task_id`.

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | HTTPS SQ message-event URL (v1.3 name; was `url` in v1.2) |
| `subscribe_entity` | string | yes | Official entity, e.g. `LEAD`, `CREATIVE_FATIGUE` (and other enums on the subscribe page) |
| `advertiser_id` | string | no | Inside `subscription_detail`; Admin access required for `LEAD` |
| `library_id` / `page_id` / `ad_id` / `adgroup_id` | string | no | As documented per `subscribe_entity` / `lead_source` |
| `lead_source` | string | no | When `subscribe_entity=LEAD` (Instant Form vs `DIRECT_MESSAGE`) |

Body also sends config `appId` + `appSecret` (`secret`) and `subscription_detail.access_token`. Callback URL must be HTTPS and return 200 quickly.

## Output fields (by operation)

### `advertiser.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | TikTok `code=0` | `ok` |
| `advertisers` | object[] | success | `advertiser_id`, `advertiser_name` |
| `error` | string | failure | Actionable; no token |

### `campaign.create` | `adgroup.create` | `ad.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `code=0` | `ok` |
| `id` | string | success | Created entity id (`campaign_id` / `adgroup_id` / `ad_id`) |
| `data` | object | success | Provider entity (no secrets) |
| `error` | string | failure | Includes TikTok `code` / `message` |

### `campaign.get` | `campaign.list` | `adgroup.list` | `ad.list` | `report.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `code=0` | `ok` |
| `list` | object[] | success | Rows (campaigns/adgroups/ads/report) |
| `page_info` | object | when present | `page`, `page_size`, `total_number` |
| `error` | string | failure | |

### `pixel.track`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `code=0` | `ok` |
| `request_id` | string | success | Correlation id |
| `error` | string | failure | Never log event `user` payloads |

### `lead.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `task_id` | string | always after create | Download task id |
| `leads` | object[] | download ok | Instant Form answers (**PII** — do not log) |
| `error` | string | failure | |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `code=0` | `ok` |
| `subscription_id` | string | success | v1.3 string id |
| `subscription` | object | success | Echo of entity / callback (no secrets) |
| `error` | string | failure | Never return `secret` |

HTTP 200 with TikTok `code != 0` is a failure → `{ error }` (e.g. `40100` throttle).

## Legal

- Portal / Business API: https://business-api.tiktok.com/portal/docs
- Subscription (v1.3): https://business-api.tiktok.com/portal/docs/create-a-subscription/v1.3
- Lead download: https://business-api.tiktok.com/portal/docs/create-a-lead-download-task/v1.3
- Business Products (Data) Terms apply to Events API: do not send sensitive data (health, government id, precise location beyond policy).
- Ads / app review: developer app + Marketing API permissions; published marketplace apps follow TikTok for Business policies.
- Rate limits (v1.3): app-level Basic **10 QPS / 600 QPM / 864,000 QPD**; Advanced/Premium/Ultimate higher. Throttle returns `"code": 40100`. QPM suspension ~5 minutes; QPD resets 00:00 UTC.
- Instant Form leads and Events `user` objects are PII. Customer owns the ad account and consent. Never log tokens, emails, phones, or form answers.
- Official-API-first: `business-api.tiktok.com/open_api/v1.3/` only.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No vendor SDK.
- Headers: `Access-Token: {accessToken}`, `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty token → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout default 30 s (floor 1). Lead download may use 60 s read.
- 429 / `code=40100` → `{ error, http_status, retry_after_seconds }` (300 if QPM; honor provider otherwise). 5xx / timeout → `retry_after_seconds: 30`. Do not tight-loop.
- Logs: operation, advertiser_id, TikTok `code`, sizes — never tokens, lead fields, or hashed identifiers.
- Lead tasks: send `x-lead-region: eu` or `us` as documented; stay on `business-api.tiktok.com`.
- **DO NOT BUILD** in this task.
