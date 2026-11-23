# Bold Commerce

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/bold/`
- **Researched:** 2026-08-20
- **Official API:** **Bold Subscriptions API v1** (`/subscriptions/v1/…`) on `https://api.boldcommerce.com`. Date header `Bold-API-Version-Date` (fallback **`2022-05-01`**). **Not** Bold Checkout / Price Rules — those are separate Bold APIs; this catalog row is subscriptions.
- **Docs:** https://developer.boldcommerce.com/api/subscriptions · getting started: https://developer.boldcommerce.com/guides/subscriptions-v2/getting-started · versioning: https://developer.boldcommerce.com/guides/subscriptions-v2/resources/api-versioning · Shopify Checkout APIs help: https://support.boldcommerce.com/boldsubscriptions/customizations-styling/apis-in-subscriptions-for-shopify-checkout
- **Auth:** Bearer API access token — secret binding: `api_token`. Shop identifier GUID from `GET /shops/v1/info` (config `shop` may be domain; resolve to `shop_identifier`)

## Scope

One node for Bold **Subscriptions** (Shopify Checkout / Recurring Orders app). The customer owns the Bold + Shopify merchant accounts and must install Bold Subscriptions. **PCI stays with Shopify / Bold Checkout** — no PAN on the daemon. Card capture is **not** this node — use Finance PSP nodes in `docs/catalog/finance-accounting.md` (Stripe, PayPal, Square, …). Do not call Bold Checkout payment endpoints from this node. Do not scrape the Bold Account Center.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `subscription_get` | GET | `/subscriptions/v1/shops/{shop_identifier}/subscriptions/{subscription_id}` | Retrieve one subscription (`expand=customer` optional via query) |
| `subscription_update` | PUT or PATCH | `/subscriptions/v1/shops/{shop_identifier}/subscriptions/{subscription_id}` | Full replace (`PUT`) or partial (`PATCH`) — prefer PATCH for status/line changes |
| `trigger` | inbound POST | Bold webhook subscription | Events from `/webhooks/topics`; register at `/webhooks/subscriptions` |

Resolve shop: `GET https://api.boldcommerce.com/shops/v1/info` with the same Bearer token → `shop_identifier` GUID. Cache per dispatch only.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `subscription_get` | no | Chooses the row above |
| `api_token` | password | yes | empty | yes | Bold Account Center API access token (`Authorization: Bearer`). Scopes: Subscriptions (and Shops read) |
| `shop` | string | yes | empty | no | Shop domain **or** Bold `shop_identifier` GUID. If not a GUID, resolve via Get Shop Info |
| `api_version_date` | string | no | `2022-05-01` | no | Header `Bold-API-Version-Date` (`YYYY-MM-DD`). Never a future date in production |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PAN |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_token` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = subscription_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscription_id` | string | yes | Path id |
| `expand` | string | no | e.g. `customer` |

### `operation = subscription_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscription_id` | string | yes | Path id |
| `subscription` | object | yes | Bold `subscription` body (status, interval, line items as documented). No payment-card fields |
| `partial` | boolean | no | `true` → PATCH; default PUT |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `headers` | object | yes | Inbound Bold webhook headers (verify per Bold webhook docs) |
| `raw_body` | string | yes | Raw JSON |
| `topic` | string | no | Expected topic |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `shop_identifier` | string | success | Resolved GUID |
| `subscription` | object | get/update | Subscription resource (subscriber PII — do not log) |
| `event` | object | `trigger` | Topic + subscription id |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Bold developer docs + Bold Subscriptions merchant terms. **Shopify Partner / App Store terms** apply when the shop is on Shopify.
- Token scopes are created in Bold Account Center (Customer / Subscription / Subscription Group / Shop keys). Least-privilege: Subscriptions read/write + Shop read.
- Official-API-first: no scrape of apps.shopify.com listings or Bold admin.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer {api_token}`, `Bold-API-Version-Date: {api_version_date}`, `Content-Type: application/json`.
- Reject PAN-shaped fields on `subscription` before HTTP.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Logs: operation, shop_identifier, subscription id, HTTP status — never token, emails, or payment methods.
