# Pinterest Ads

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/pinterest-ads/`
- **Researched:** 2026-08-20
- **Official API:** Pinterest API **v5** (only supported version; v3 retired). Ads live under the same `/v5` host with ads scopes.
- **Docs:** Ads use-case https://developers.pinterest.com/usecase/ads/ · auth https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/ · campaigns list https://developers.pinterest.com/docs/api/v5/campaigns-list · conversions https://developers.pinterest.com/docs/track-conversions/track-conversions-in-the-api/ · rate limits https://developers.pinterest.com/docs/reference/rate-limits/ · sandbox https://developers.pinterest.com/docs/developer-tools/sandbox/ · audiences async https://developers.pinterest.com/docs/work-with-targets-and-audiences/use-asynchronous-upload-jobs-for-large-customer-lists/
- **Auth:** bearer `access_token` minted outside StudioQuack. Conversions may use a **conversion access token** (Ads Manager) instead of user OAuth. No `client_id` / `client_secret` on this node.

## Scope

One node for Pinterest **ads** (ad accounts, campaigns, ad groups, ads, analytics, audiences, conversion events). Organic Pins belong in Social — do not add pin create/board ops here.

Standard vs Trial access: ads write/analytics require **Standard** (video demo + review). Operator completes Pinterest app review.

## Operations (`operation` config values)

Pinterest developer HTML pages often render empty shells (2026-08-20 fetch). Paths below are the v5 OpenAPI / campaigns-list URL shape: `https://api.pinterest.com/v5/ad_accounts/{ad_account_id}/…`. Confirm against the live OpenAPI if a path 404s.

Host: `https://api.pinterest.com/v5`  
OAuth token URL: `POST https://api.pinterest.com/v5/oauth/token`  
Authorize: `https://www.pinterest.com/oauth/`

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `adAccount.list` | GET | `/ad_accounts` | List ad accounts the token can access |
| `campaign.create` | POST | `/ad_accounts/{ad_account_id}/campaigns` | Create campaigns (array body as v5) |
| `campaign.list` | GET | `/ad_accounts/{ad_account_id}/campaigns` | List/filter campaigns |
| `adGroup.create` | POST | `/ad_accounts/{ad_account_id}/ad_groups` | Create ad groups |
| `adGroup.list` | GET | `/ad_accounts/{ad_account_id}/ad_groups` | List ad groups |
| `ad.create` | POST | `/ad_accounts/{ad_account_id}/ads` | Create ads |
| `ad.list` | GET | `/ad_accounts/{ad_account_id}/ads` | List ads |
| `analytics.get` | GET | `/ad_accounts/{ad_account_id}/campaigns/analytics` | Campaign analytics (also ad-group/ad variants via `level`) |
| `audience.create` | POST | `/ad_accounts/{ad_account_id}/customer_lists` | Small customer list (`customer_lists/create`) |
| `conversion.upload` | POST | `/ad_accounts/{ad_account_id}/events` | Conversion API events |

`analytics.get` with `level=ad_group` → `/ad_groups/analytics`; `level=ad` → `/ads/analytics`; default campaign.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `adAccount.list` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | OAuth Bearer minted outside StudioQuack **or** conversion token for `conversion.upload` |
| `ad_account_id` | string | ads ops | empty | no | Advertiser / ad account id |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s |

Scopes: `ads:read`, `ads:write`, `ads:analytics` (or category names `ads_read` / `ads_write` / `ads_analytics` as published), `ads:write` for conversions if using OAuth instead of conversion token.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Customer lists are PII |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never log Bearer or list records |

FUNCTION ports: none.

## Input fields (by operation)

### `adAccount.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Cap 100 |
| `bookmark` | string | no | Pagination bookmark |

### `campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaigns` | array | yes | CampaignCreate objects (`name`, `status`, `objective_type`, daily/lifetime spend caps as v5 schema) |

### `campaign.list` | `adGroup.list` | `ad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_ids` | array | no | Filter |
| `entity_statuses` | array | no | e.g. `ACTIVE`, `PAUSED` |
| `page_size` | number | no | Cap 100 |
| `bookmark` | string | no | Cursor |

### `adGroup.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_groups` | array | yes | Includes `campaign_id`, budget, bid, targeting spec |

### `ad.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ads` | array | yes | Includes `ad_group_id`, creative / pin id as v5 AdsCreateRequest |

### `analytics.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start_date` | string | yes | `YYYY-MM-DD` UTC; not more than 90 days back (non-HOUR) |
| `end_date` | string | yes | ≤ 90 days after start |
| `columns` | array | yes | Metric names |
| `granularity` | string | yes | e.g. `DAY` |
| `level` | select | no | `campaign` \| `ad_group` \| `ad` |
| `campaign_ids` | array | no | Filter |

### `audience.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | List name |
| `list_type` | string | yes | As customer_lists/create (`EMAIL`, `MAID`, …) |
| `records` | array | yes | **Hashed** identifiers (SHA-256 as Pinterest currently requires). Cap batch; large lists must use async upload jobs (out of v1 — return `{ error }` directing operator to split) |

Never accept raw emails without hashing in the handler.

### `conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data` | array | yes | Events (`event_name`, `event_time`, `user_data` hashed). `test=true` query for dry run |
| `is_test` | boolean | no | Maps to `test=true` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | array | list/create | `items` array from v5 |
| `bookmark` | string | paging | Next page |
| `analytics` | array | analytics.get | Metric rows |
| `customer_list` | object | audience.create | id, name, num_batches |
| `events` | object | conversion.upload | Received / filtered counts |
| `error` | string | failure | Pinterest code + message (no token) |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | Category caps: ads_write 400/min Standard; ads_analytics 300/min; ads_read 1000/min |

Universal ceiling cited for Standard: 100 req/s per user per app (confirm live rate-limits page).

## Legal

- Pinterest Developer Guidelines + Advertising Guidelines. Operator completes ads access review.
- Customer owns the Pinterest ad account. Conversion user_data is PII — hash as documented; never log.
- Fee-disclosure rules on Standard access (operator).
- Do not store conversion payloads.

## Implementation notes

- JDK `HttpClient` only. No Pinterest SDK.
- Header `Authorization: Bearer {access_token}`, `Content-Type: application/json`.
- Missing `ad_account_id` on non-list-account ops → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout 30 s.
- Pagination: one page per node run; return `bookmark`.
- Logs: operation, ad_account_id, HTTP status, item counts — never Bearer, emails, or MAIDs.
