# Stripe

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/stripe/`
- **Researched:** 2026-08-20
- **Official API:** Stripe REST v1, account pin **2026-07-29.dahlia** (`Stripe-Version` header)
- **Docs:** https://docs.stripe.com/api
- **Auth:** api_key — secret binding: `secret_key` (`Authorization: Bearer sk_…`). `api_version` → `Stripe-Version` extraHeaders. Webhook signature verify is not done by this HTTP spec node.

## Scope

One node for Stripe Payments + Billing + Connect-adjacent read ops the merchant already has keys for. Designer picks `operation`; handler calls `https://api.stripe.com` with form-encoded bodies. Live vs sandbox is implied by the key (`sk_live_` / `sk_test_`). Ingress for `trigger` is the StudioQuack webhook relay; this node verifies `Stripe-Signature` on the forwarded raw body.

**HARD PCI:** cardholder data never enters the graph. Reject INPUT that looks like PAN, CVC, magnetic-stripe track, or a raw card object. `payment_method` must be a `pm_…` token (or omit and confirm later with Stripe.js). Do not emit `client_secret` on OUTPUT.

**DO NOT BUILD** (retired / PCI): `token_create`, `source_create` / `source_get` / `source_delete`, `card_add` / `card_get` / `card_remove` with raw cards, `charge_create` with card numbers. Charges remain **read-only** (`charge_get`, `charge_list`). Drop catalog `charge_update` — not in the current PaymentIntents surface for this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `balance_get` | GET | `/v1/balance` | Available / pending balances |
| `payment_intent_create` | POST | `/v1/payment_intents` | Create a PaymentIntent (`amount`, `currency`, `payment_method` = `pm_…` only) |
| `payment_intent_get` | GET | `/v1/payment_intents/{id}` | Retrieve a PaymentIntent |
| `payment_intent_confirm` | POST | `/v1/payment_intents/{id}/confirm` | Confirm server-side (token already attached) |
| `customer_create` | POST | `/v1/customers` | Create a customer (email/name/metadata — no card) |
| `customer_get` | GET | `/v1/customers/{id}` | Retrieve a customer |
| `customer_list` | GET | `/v1/customers` | List customers |
| `customer_update` | POST | `/v1/customers/{id}` | Update a customer |
| `customer_delete` | DELETE | `/v1/customers/{id}` | Delete a customer |
| `coupon_create` | POST | `/v1/coupons` | Create a coupon |
| `coupon_list` | GET | `/v1/coupons` | List coupons |
| `meter_event_create` | POST | `/v1/billing/meter_events` | Record usage for a Billing meter |
| `refund_create` | POST | `/v1/refunds` | Refund a PaymentIntent / Charge by id |
| `subscription_create` | POST | `/v1/subscriptions` | Create a subscription (`customer`, `items[].price`) |
| `invoice_get` | GET | `/v1/invoices/{id}` | Retrieve an invoice |
| `payout_list` | GET | `/v1/payouts` | List payouts to the connected bank |
| `charge_get` | GET | `/v1/charges/{id}` | Legacy charge read |
| `charge_list` | GET | `/v1/charges` | Legacy charge list |
| `trigger` | — | verify `Stripe-Signature` | Webhook event after HMAC check |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `balance_get` | no | Chooses the row above |
| `secret_key` | password | yes (except `trigger`) | empty | yes | Secret key. Never on INPUT, never logged, never in `{error}` |
| `api_version` | string | no | `2026-07-29.dahlia` | no | `Stripe-Version` header via extraHeaders |
| `timeout_seconds` | number | no | `30` | no | Connect + read timeout. Floor 1 s |
| `stripe_account` | string | no | empty | no | Optional `Stripe-Account` (Connect) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PAN/CVC |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never `secret_key`, `client_secret`, PAN |

FUNCTION ports: none.

## Input fields (by operation)

PCI guard on every op: reject keys `number`, `cvc`, `exp_month`+card object, `card[number]`, `source` card hashes.

### `operation = balance_get`

*(none)*

### `operation = payment_intent_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `amount` | number | yes | Amount in the smallest currency unit |
| `currency` | string | yes | ISO 4217 lowercase (e.g. `usd`) |
| `payment_method` | string | no | `pm_…` token only |
| `customer` | string | no | `cus_…` |
| `confirm` | boolean | no | If true, confirm immediately (still token-only) |
| `description` | string | no | Statement / dashboard text |
| `metadata` | object | no | String map; cap 50 keys |
| `idempotency_key` | string | no | `Idempotency-Key` header |

### `operation = payment_intent_get` | `payment_intent_confirm`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | `pi_…` |
| `payment_method` | string | no | Confirm only: `pm_…` if not already set |
| `return_url` | string | no | Confirm only: redirect after next_action |

### `operation = customer_create` | `customer_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update only | `cus_…` |
| `email` | string | no | Customer email |
| `name` | string | no | Display name |
| `description` | string | no | Internal note |
| `metadata` | object | no | String map |

### `operation = customer_get` | `customer_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | `cus_…` |

### `operation = customer_list` | `coupon_list` | `charge_list` | `payout_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | 1–100, default 10 |
| `starting_after` | string | no | Cursor |
| `ending_before` | string | no | Cursor |

### `operation = coupon_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `percent_off` | number | one of | Percent discount |
| `amount_off` + `currency` | number + string | one of | Fixed discount |
| `duration` | string | yes | `once` \| `repeating` \| `forever` |
| `duration_in_months` | number | if repeating | Months |
| `id` | string | no | Coupon code / id |

### `operation = meter_event_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_name` | string | yes | Meter `event_name` |
| `payload` | object | yes | Must include meter customer + value keys (default `stripe_customer_id`, `value`) |
| `identifier` | string | no | Dedup id (UUID recommended) |
| `timestamp` | number | no | Unix seconds |

### `operation = refund_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payment_intent` | string | one of | `pi_…` |
| `charge` | string | one of | `ch_…` (legacy) |
| `amount` | number | no | Partial refund in smallest unit |
| `reason` | string | no | `duplicate` \| `fraudulent` \| `requested_by_customer` |
| `idempotency_key` | string | no | Idempotency |

### `operation = subscription_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer` | string | yes | `cus_…` |
| `items` | array | yes | `[{ "price": "price_…" }]` — no card |
| `default_payment_method` | string | no | `pm_…` |
| `metadata` | object | no | String map |

### `operation = invoice_get` | `charge_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | `in_…` / `ch_…` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Exact forwarded body bytes as UTF-8 string |
| `stripe_signature` | string | yes | `Stripe-Signature` header (`t=…,v1=…`) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | resource ops | Stripe object id |
| `object` | string | resource ops | Stripe object type |
| `resource_status` | string | PI / sub / refund / charge | Provider status (`succeeded`, `active`, …) |
| `amount` | number | money ops | Amount in smallest unit |
| `currency` | string | money ops | ISO currency |
| `data` | array | list ops | Page of objects (ids + status only; strip secrets) |
| `has_more` | boolean | list ops | Stripe pagination |
| `available` / `pending` | array | `balance_get` | Balance amounts by currency |
| `event_id` | string | `trigger` | `evt_…` |
| `event_type` | string | `trigger` | e.g. `payment_intent.succeeded` |
| `event_data` | object | `trigger` | `data.object` with PAN/secrets stripped |
| `error` | string | failure | Actionable; redacted |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present else 30–60 |

## Legal

- API terms: https://stripe.com/legal/ssa and https://docs.stripe.com/security
- PCI DSS: merchant is the Stripe customer; StudioQuack never stores CHD. SAQ A posture requires Stripe.js / Payment Element / hosted tokens only.
- Rate limits (published): live global ~100 rps; many endpoints ~25 rps; PaymentIntents 1000 updates/object/hour. 429 → backoff, do not tight-loop.
- PII: customer email/name/metadata leave the daemon to Stripe. Operator must have a lawful basis (GDPR/CCPA). Data residency follows the Stripe account’s entity.
- Prohibited: restricted businesses per Stripe Prohibited and Restricted Businesses list. Not a money transmitter; customer holds the merchant account.

## Implementation notes

- JDK `HttpClient` only. No Stripe Java SDK. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer {secret_key}`, `Stripe-Version: {api_version}`, `Content-Type: application/x-www-form-urlencoded` on POST, `Idempotency-Key` when supplied, optional `Stripe-Account`.
- Empty `secret_key` (non-trigger) → `{ error }` no HTTP. `trigger` is BPMN ingress — this node does not verify `Stripe-Signature`.
- Body cap **1_000_000** bytes. Timeout default 30 s (floor 1).
- Retry **once** on 429/5xx/timeout with full jitter (honor `Retry-After`). Other 4xx → `{ error }` never throw past dispatch.
- `trigger`: BPMN message-event ingress. This HTTP spec node cannot verify webhook signatures.
- Logs: operation, HTTP status, object id, sizes — never keys, PAN, CVC, `client_secret`.
