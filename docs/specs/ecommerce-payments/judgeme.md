# Judge.me

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/judgeme/`
- **Researched:** 2026-08-20
- **Official API:** Judge.me REST **v1**
- **Docs:** https://judge.me/api (OpenAPI). Help: https://judge.me/help/en/articles/8409180-using-judge-me-api
- **Auth:** `api_token` (query) + `shop_domain` — secret binding: `api_token` (use **private** token server-side)

## Scope

One node for Judge.me product reviews (Shopify-centric; also WooCommerce / BigCommerce via `platform` on create). Public token is for client-side widget GETs only — this daemon node **must use the private token**.

UGC: moderate before republish. Never log reviewer email or review body.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `review_list` | GET | `https://api.judge.me/api/v1/reviews` | Paginated reviews (`per_page` max 100) |
| `review_create` | POST | `https://judge.me/api/v1/reviews` | Import/create a review (help center host; private token). Cannot mark verified or delete via API |

Do not use the public widget token for write. OAuth for third-party apps is documented separately (https://judge.me/help/en/articles/8283047-setting-up-oauth) and is out of starting ops.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `review_list` | no | Chooses the row above |
| `api_token` | password | yes | empty | yes | **Private** API token (query `api_token`). Never on INPUT; never log full request URLs (token is a query param) |
| `shop_domain` | string | yes | empty | no | `example.myshopify.com` (or documented shop domain) |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Reviewer email/body are PII |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_token` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = review_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page number |
| `per_page` | number | no | Max 100 |
| `product_id` | number | no | Internal Judge.me product id |
| `rating` | number | no | 1–5 |
| `published` | boolean | no | Publish filter |
| `reviewer_id` | number | no | Internal reviewer id |
| `reviewer_email` | string | no | Filter — PII; do not log |

### `operation = review_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `platform` | string | yes | `shopify` \| `woocommerce` \| `bigcommerce` |
| `name` | string | yes | Reviewer display name |
| `email` | string | yes | Reviewer email (PII — never log) |
| `rating` | number | yes | 1–5 |
| `body` | string | yes | Review body (PII — never log) |
| `id` | string | no | External (Shopify) product id |
| `title` | string | no | Title |
| `reviewer_name_format` | string | no | `''` \| `last_initial` \| `all_initials` \| `anonymous` |
| `picture_urls` | array | no | Public HTTPS image URLs (jpg/png, ≤5, ≤10 MB). Not type `file` — Judge.me does not accept daemon bytes / base64 |
| `ip_addr` | string | no | Reviewer IP for location — PII |

Do not send `cf_answers` unless empty (help center: non-empty `cf_answers` can duplicate reviews). Video URLs are ignored.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `reviews` | array | `review_list` | Review objects (ids, rating, published). Omit email/body from logs |
| `review` | object | `review_create` | Created review id / flags |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- Terms: https://judge.me/terms
- Operator holds a Judge.me shop and complies with storefront platform terms (Shopify etc.).
- Reviews created via API cannot be verified or deleted via API. Do not claim verified-buyer in republish unless Judge.me marks it.
- Official-API-first: no scrape of widgets or admin.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Query params: `api_token`, `shop_domain`. Redact `api_token` from any URL logged or returned in `{ error }`.
- Empty token/domain → `{ error }` — no HTTP.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 401 → `{ error }` missing/invalid token. Do not auto-retry.
- Logs: operation, shop_domain, HTTP status, counts — never token, email, or review body.
