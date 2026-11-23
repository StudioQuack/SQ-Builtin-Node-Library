# ReCharge

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/recharge/`
- **Researched:** 2026-08-20
- **Official API:** ReCharge REST **`2021-11`** (also `2021-01`; **no 2026-xx API version** as of 2026-08-20). New integrations must send `2021-11`.
- **Docs:** https://developer.rechargepayments.com/2021-11 · versions: https://docs.getrecharge.com/docs/api-versions · webhooks: https://docs.getrecharge.com/docs/webhooks-overview
- **Auth:** API token header `X-Recharge-Access-Token` — secret binding: `api_key`. Version header `X-Recharge-Version`. Webhook HMAC uses **API Client Secret** — `webhook_secret`

## Scope

One node for ReCharge subscriptions on Shopify (and ReCharge-supported storefronts). The customer owns the ReCharge merchant account. **PCI stays with ReCharge / Shopify** — never send PAN, CVV, or full payment-method numbers through this node; tokens/ids only. Card capture is **not** this node — use Finance PSP nodes in `docs/catalog/finance-accounting.md` (Stripe, PayPal, Square, …).

`subscription_skip` on **2021-11** is implemented as skipping a future **charge** for subscription id(s) (`POST /addresses/{id}/charges/skip` and/or `POST /charges/{id}/skip`), not a retired `/subscriptions/{id}/skip` path.

Webhooks are a `trigger` operation on **this same node**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `customer_get` | GET | `/customers/{id}` | Retrieve a ReCharge customer |
| `subscription_list` | GET | `/subscriptions` | List subscriptions (`customer_id`, `address_id`, `status`, `limit`, cursor as documented) |
| `subscription_skip` | POST | `/addresses/{address_id}/charges/skip` | Skip a future charge for one or more `subscription_ids` on that address. Alternate: `POST /charges/{charge_id}/skip` when `charge_id` is supplied |
| `charge_list` | GET | `/charges` | List charges. **Processed charges with `processed_at` > 90 days are omitted** (policy from 2025-03-19) |
| `address_update` | PUT | `/addresses/{id}` | Update a subscription address (shipping). No PAN |
| `trigger` | inbound POST | ReCharge webhook | Topics such as `subscription/*`, `charge/*`, `address/*` |

Base: `https://api.rechargeapps.com`. Default version without header is store default (often `2021-01`) — **always send `X-Recharge-Version: 2021-11`**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `customer_get` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Store API token (`X-Recharge-Access-Token`) |
| `api_version` | string | no | `2021-11` | no | `X-Recharge-Version`. Allow `2021-01` only for legacy shops |
| `webhook_secret` | password | no | empty | yes | API Client Secret for `X-Recharge-Hmac-Sha256` on `trigger` |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PAN |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo payment instruments |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer_id` | number | yes | Path `{id}` |

### `operation = subscription_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer_id` | number | no | Filter |
| `address_id` | number | no | Filter |
| `status` | string | no | e.g. active |
| `limit` | number | no | Page size |
| `cursor` | string | no | Pagination cursor if documented |

### `operation = subscription_skip`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address_id` | number | conditional | Required unless `charge_id` set |
| `date` | string | conditional | `YYYY-MM-DD` of charge to skip (address skip) |
| `subscription_ids` | array | conditional | Subscription ids to skip on that date |
| `charge_id` | number | conditional | If set, `POST /charges/{charge_id}/skip` instead |

### `operation = charge_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer_id` | number | no | Filter |
| `address_id` | number | no | Filter |
| `status` | string | no | Filter |
| `limit` | number | no | Page size |

### `operation = address_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address_id` | number | yes | Path `{id}` |
| `address` | object | yes | Address fields ReCharge documents (name, lines, city, zip, country). No payment fields |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `headers` | object | yes | Includes `X-Recharge-Hmac-Sha256` |
| `raw_body` | string | yes | Raw body for HMAC |
| `topic` | string | no | Expected topic |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `customer` | object | `customer_get` | Customer record (email is PII — do not log) |
| `subscriptions` | array | `subscription_list` / skip | Subscription ids + status |
| `charges` | array | `charge_list` | Charge ids + status — **no PAN** |
| `address` | object | `address_update` | Updated address |
| `event` | object | `trigger` | Topic + resource ids |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- ReCharge + Shopify merchant/app terms. Customer holds the merchant account.
- **PCI-DSS:** daemon never persists or logs card numbers. Payment methods are ReCharge tokens/ids only.
- Webhook retries: ReCharge waits 5 s; retries up to 20 times over 48 h; then drops.
- Official-API-first: no scrape of ReCharge merchant portal.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `X-Recharge-Access-Token`, `X-Recharge-Version: {api_version}`, `Content-Type: application/json`.
- Reject any input key that looks like PAN (`number`, `cvv`, `cvc`) before HTTP.
- `trigger`: HMAC-SHA256 of raw body with `webhook_secret`; compare to `X-Recharge-Hmac-Sha256` (constant-time).
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Logs: operation, ids, HTTP status, sizes — never token, email, address lines, or payment fields.
