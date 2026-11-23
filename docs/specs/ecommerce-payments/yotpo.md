# Yotpo

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/yotpo/`
- **Researched:** 2026-08-20
- **Official API:** **Two products, one node.** (1) Yotpo **Core API v3** + Reviews/UGC **v1** on `https://api.yotpo.com`. (2) Yotpo **Loyalty & Referrals API v2** on `https://loyalty.yotpo.com`.
- **Docs:** Core: https://core-api.yotpo.com/reference (updated 2026-06-23). Reviews (merchant): https://apidocs.yotpo.com/reference/about-merchant-reviews. Loyalty: https://loyaltyapi.yotpo.com/reference/welcome
- **Auth:** Reviews/Core: query `utoken` minted outside StudioQuack (`POST /oauth/token`). Loyalty: headers `X-GUID` + `X-API-KEY` from the Loyalty admin (different credentials).

## Scope

One node for Yotpo **Reviews** (UGC) and **Loyalty & Referrals**. They are **separate official APIs** with separate auth; namespace operations (`review_*` vs `loyalty_*` vs Core `order_send`). Do not call retired Yotpo SMS/Email/Subscriptions surfaces.

UGC: moderate before republish. Never log reviewer email or review body in daemon logs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `review_list` | GET | `/v1/apps/{store_id}/reviews` | List merchant reviews (Reviews API). Cap ~100 per page; ~1 h data delay per docs |
| `review_create` | POST | `/v1/widget/reviews` | Create a review (async; recommended). Sends verification email to the reviewer per Yotpo |
| `order_send` | POST | `/core/v3/stores/{store_id}/orders` | Sync an order to Core (review-request eligibility; not older than 6 months) |
| `loyalty_adjust` | POST | `/api/v2/points/adjust` | Adjust Loyalty points (`https://loyalty.yotpo.com`) |
| `trigger` | inbound POST | Core webhook subscription | Reviews / order / loyalty webhook topics registered via Core webhook targets |

Token mint is out of band: https://core-api.yotpo.com/reference/yotpo-authentication (`app_key` + store secret → `utoken`). Bind the minted `utoken` as a StudioQuack secret. The engine does not mint tokens.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `review_list` | no | Chooses the row above |
| `app_key` | string | yes | empty | no | Store ID / app key (path `{store_id}`). Not a password but treat as account id |
| `utoken` | password | yes | empty | yes | Reviews/Core query `utoken` minted outside StudioQuack |
| `loyalty_guid` | string | no | empty | no | Loyalty `X-GUID`. Required for `loyalty_adjust` |
| `loyalty_api_key` | password | no | empty | yes | Loyalty `X-API-KEY`. Required for `loyalty_adjust` |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Reviewer email/body are PII |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets or utoken |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = review_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page |
| `count` | number | no | Page size (default 10; recommend ≤ 100) |
| `since_id` | string | no | Lowest review id |
| `since_date` | string | no | Created-after (date) |
| `since_updated_at` | string | no | Updated-after |
| `deleted` | boolean | no | Include unpublished |
| `review_id` | string | no | Filter `id` |

### `operation = review_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sku` | string | yes | Product id on the store, or `yotpo_site_reviews` for a site review |
| `product_title` | string | yes | Product title |
| `product_url` | string | yes | Product URL |
| `display_name` | string | yes | Reviewer display name |
| `email` | string | yes | Reviewer email (PII — never log) |
| `review_content` | string | yes | Body (PII — never log) |
| `review_title` | string | yes | Title |
| `review_score` | number | yes | Star score |
| `domain` | string | no | Account domain |

### `operation = order_send`

JSON body is `{ "order": { … } }` (Core API examples). Map the fields below into that wrapper.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `external_id` | string | yes | Merchant order id |
| `order_date` | string | yes | ISO-8601; not > 6 months past/future |
| `line_items` | array | yes | `{ quantity, external_product_id, … }` |
| `customer` | object | yes | Checkout customer (`external_id`, `email`, names) — PII |
| `fulfillments` | array | no | Fulfillment + tracking (no PAN) |

### `operation = loyalty_adjust`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer_email` | string | conditional | Required if no `customer_id` |
| `customer_id` | string | conditional | Merchant customer id |
| `point_adjustment_amount` | number | yes | Signed integer (positive add, negative remove) |
| `apply_adjustment_to_points_earned` | boolean | no | Lifetime-earned flag per Loyalty docs |
| `history_title` | string | no | History label |
| `visible_to_customer` | boolean | no | Default true |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `headers` | object | yes | Inbound webhook headers |
| `raw_body` | string | yes | Raw JSON body |
| `topic` | string | no | Filter (e.g. review created / updated per Core topics) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `reviews` | array | `review_list` | `{ id, score, sku, created_at, … }` — omit email/content from logs |
| `review_id` | number | `review_create` | Provider review id when present |
| `yotpo_order_id` | string | `order_send` | `order.yotpo_id` |
| `adjustment` | object | `loyalty_adjust` | Loyalty action id / type (`manual_adjustment`) |
| `event` | object | `trigger` | Topic + resource ids only |
| `error` | string | failure | Actionable, no secrets |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor ratelimit headers; else 60 / 30 |

## Legal

- Terms: https://www.yotpo.com/terms-and-conditions/
- Reviews list: **5,000 req/min per app key**; widget create has separate limits. Loyalty 423 = resource locked — backoff, do not tight-loop.
- Reviewer email/content are PII. Moderate UGC before republishing on other channels. Merchant is responsible for incentivized-review disclosure.
- Official-API-first: no scrape of yotpo widgets or admin.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Reviews/Core: bind minted `utoken`; sent as query `utoken`. Loyalty: `X-GUID` + `X-API-KEY`.
- Empty Reviews `utoken` → `{ error }` — no HTTP. `loyalty_adjust` without Loyalty keys → `{ error }` — no HTTP.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Honor `ratelimit-remaining` / `x-ratelimit-remaining-minute`. 429 → `retry_after_seconds: 60`. Do not auto-retry in-handler.
- Logs: operation, store id, HTTP status, counts — never secret, utoken, reviewer email, or review body.
