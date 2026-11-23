# X Ads

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/x-ads/`
- **Researched:** 2026-08-20
- **Official API:** X **Ads API v12** (`https://ads-api.x.com/12/`)
- **Docs:** Canonical product docs live on the X Developer Platform (Ads API). Anonymous fetch of `https://developer.x.com/en/docs/twitter-ads-api` and `https://docs.x.com/x-ads-api/introduction` returned **HTTP 403** on 2026-08-20. Version **12** and paths below are confirmed from `https://ads-api.x.com/12/…` resource URLs in X’s Ads campaign-management reference and from [twitterdev/twitter-python-ads-sdk](https://github.com/twitterdev/twitter-python-ads-sdk) (`RESOURCE_SYNC = /{API_VERSION}/stats/accounts/{account_id}`).
- **Auth:** **OAuth 1.0a** (required for Ads API requests) — secret bindings: `consumerSecret`, `accessSecret`. Catalog `oauth2` is **not** the Ads write path; do not confuse with X API v2 Bearer used by the **Social** Twitter/X **search** node (`nodes/twitter`).

## Scope

One node for the **X Ads API** (formerly Twitter Ads API): ad accounts, campaigns, line items, promoted posts, targeting, analytics, conversion upload. Host **`https://ads-api.x.com`**. Path version **`/12/`**. Legacy `ads-api.twitter.com` may still resolve — **new code uses `ads-api.x.com`**.

**Restricted access:** developer app at `developer.x.com` / `console.x.com`, then a separate **Ads API Access Form**. Approval-gated; not the same entitlement as X API v2 search. **This node must never call** `/2/tweets/search` or other social-search endpoints.

**Customer owns the X Ads account.** Published apps: X Developer Agreement + Ads policies / app review.

No `trigger.listen` in the catalog row.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account.get` | GET | `/12/accounts` or `/12/accounts/:account_id` | List authorized ads accounts, or one account |
| `campaign.create` | POST | `/12/accounts/:account_id/campaigns` | Create campaign (query params) |
| `campaign.list` | GET | `/12/accounts/:account_id/campaigns` | List campaigns (`count` 1–1000, cursor) |
| `lineItem.create` | POST | `/12/accounts/:account_id/line_items` | Create line item under a campaign |
| `lineItem.list` | GET | `/12/accounts/:account_id/line_items` | List line items |
| `promotedTweet.create` | POST | `/12/accounts/:account_id/promoted_tweets` | Attach a post to a line item |
| `targeting.criteria` | GET or POST | `/12/accounts/:account_id/targeting_criteria` | GET list by `line_item_ids`; POST create criteria |
| `stats.get` | GET | `/12/stats/accounts/:account_id` | Synchronous analytics |
| `conversion.upload` | POST | `/12/measurement/conversions/:pixel_id` | Web conversion events JSON |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `account.get` | no | Chooses the row above |
| `consumerKey` | string | yes | | no | App API key (OAuth 1.0a consumer key) |
| `consumerSecret` | password | yes | | yes | Consumer secret |
| `accessToken` | password | yes | | yes | User access token (ads-capable user) |
| `accessSecret` | password | yes | | yes | Access token secret |
| `accountId` | string | no | | no | Default ads `account_id` |
| `timeout_seconds` | number | no | `30` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No OAuth secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens; conversion identifiers are PII even when hashed |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = account.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | no | If set, GET one account; else GET `/12/accounts` |
| `count` | number | no | Page size when listing (max 1000) |
| `cursor` | string | no | Pagination cursor |
| `with_deleted` | boolean | no | Include deleted |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `name` | string | yes | Campaign name |
| `funding_instrument_id` | string | yes | Funding instrument |
| `daily_budget_amount_local_micro` | number | no | Micros of local currency |
| `total_budget_amount_local_micro` | number | no | Total budget micros |
| `entity_status` | string | no | Prefer `PAUSED` on create |
| `budget_optimization` | string | no | e.g. `LINE_ITEM` |

POST uses **query parameters** (Ads API convention), not a JSON body.

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `campaign_ids` | string | no | Comma-separated (max 200) |
| `count` / `cursor` | mixed | no | Pagination |
| `with_deleted` | boolean | no | |

### `operation = lineItem.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `campaign_id` | string | yes | Parent campaign |
| `name` | string | yes | Line item name |
| `objective` | string | no | As documented |
| `bid_amount_local_micro` | number | no | Bid micros |
| `entity_status` | string | no | Prefer `PAUSED` |

### `operation = lineItem.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `campaign_ids` / `line_item_ids` | string | no | Comma-separated filters |
| `count` / `cursor` | mixed | no | |

### `operation = promotedTweet.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `line_item_id` | string | yes | Line item |
| `tweet_id` | string | yes | Post id to promote |

Requires Ads + tweet-composer permission on the promotable user.

### `operation = targeting.criteria`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `line_item_ids` | string | yes for GET | Comma-separated |
| `criteria` | object[] | yes for POST | Targeting objects (`targeting_type`, `targeting_value`, `operator_type` `EQ`/`NE`) |
| `count` / `cursor` | mixed | no | GET pagination |

If `criteria` present → POST JSON (`Content-Type: application/json`); else GET.

### `operation = stats.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes* | Else config |
| `entity` | string | yes | `CAMPAIGN` \| `LINE_ITEM` \| `PROMOTED_TWEET` \| … |
| `entity_ids` | string[] | yes | Ids for that entity |
| `metric_groups` | string[] | yes | Metric groups as documented |
| `start_time` / `end_time` | string | yes | ISO-8601 |
| `granularity` | string | no | e.g. `HOUR`, `DAY` |
| `placement` | string | no | e.g. `ALL_ON_TWITTER` |

### `operation = conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pixel_id` | string | yes | Conversion pixel / event source id |
| `conversions` | object[] | yes | Events: `conversion_time`, `event_id`, `identifiers` (hashed_email SHA-256, twclid, …), `value`, `conversion_id`. **Hash PII before send** per Ads measurement docs. Cap batch size. |

## Output fields (by operation)

### `account.get` | `campaign.list` | `lineItem.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object / object[] | success | Ads entities |
| `next_cursor` | string | when present | Pagination |
| `error` | string | failure | Ads `errors[]` text, no tokens |

### `campaign.create` | `lineItem.create` | `promotedTweet.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | success | Created id |
| `data` | object | success | Entity |
| `error` | string | failure | |

### `targeting.criteria`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object[] | success | Criteria |
| `error` | string | failure | |

### `stats.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object[] | success | Time series / metrics |
| `error` | string | failure | |

### `conversion.upload`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object | success | Acceptance / request id |
| `error` | string | failure | Never log `identifiers` |

## Legal

- X Developer Agreement and Policy: https://developer.x.com/en/developer-terms/agreement-and-policy
- Ads API is **approval-gated**. Do not ship calls that assume every X developer token can hit `ads-api.x.com`.
- Conversion upload: hash emails/phones as specified; do not send raw PII. Customer owns the ads account and conversion consent.
- Separate from Social catalog Twitter/X **search** node. Mixing tokens/hosts is a policy and product bug.
- Never log consumer secrets, access secrets, or conversion identifiers.
- Official-API-first: `ads-api.x.com/12` only. No scrape of ads.x.com UI.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Sign every request **OAuth 1.0a** (HMAC-SHA1) — no vendor SDK required; do not use OAuth 2 Bearer for this node.
- HTTPS only. JSON responses; POST campaign/line-item typically `application/x-www-form-urlencoded` query; conversion POST JSON.
- Empty secrets → `{ error }` no HTTP. If Ads access is denied (403), message must say Ads API approval is required — not “retry as search API”.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }` honor `Retry-After` else 60. 5xx/timeout → 30. Do not tight-loop.
- Logs: operation, account_id, HTTP status, sizes — never OAuth material, tweet text, or conversion identifiers.
- **DO NOT BUILD** in this task.
