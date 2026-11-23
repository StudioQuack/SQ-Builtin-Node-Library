# Square

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/square/`
- **Researched:** 2026-08-20
- **Official API:** Square REST, pin **2026-08-19** (`Square-Version` header). (Catalog research cited 2026-07-15; live reference on 2026-08-20 is **2026-08-19**.)
- **Docs:** https://developer.squareup.com/reference/square
- **Auth:** api_key — secret binding: `access_token` (`Authorization: Bearer`). Webhooks: `webhook_signature_key`

## Scope

One node for Square Payments, Refunds, Orders, Customers, Invoices, Catalog, plus `trigger`. Hosts: live `https://connect.squareup.com`, sandbox `https://connect.squareupsandbox.com`.

**PCI:** `source_id` = Web Payments SDK nonce / token / card-on-file id (`cnon:…`, `ccof:…`). NEVER raw PAN. Reject card number fields on INPUT.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `payment_create` | POST | `/v2/payments` | Charge a token (`source_id`) |
| `payment_get` | GET | `/v2/payments/{payment_id}` | Retrieve a payment |
| `refund_create` | POST | `/v2/refunds` | Refund a payment |
| `order_create` | POST | `/v2/orders` | Create an order |
| `order_get` | GET | `/v2/orders/{order_id}` | Retrieve an order |
| `customer_create` | POST | `/v2/customers` | Create a customer |
| `customer_get` | GET | `/v2/customers/{customer_id}` | Retrieve a customer |
| `invoice_create` | POST | `/v2/invoices` | Create an invoice (order must exist) |
| `catalog_list` | GET | `/v2/catalog/list` | List catalog objects |
| `trigger` | — | verify `x-square-hmacsha256-signature` | Webhook after HMAC |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `payment_create` | no | Chooses the row above |
| `access_token` | password | yes (except `trigger`) | empty | yes | Seller OAuth / PAT |
| `env` | select | no | `sandbox` | no | `sandbox` \| `live` |
| `location_id` | string | no | empty | no | Default location |
| `api_version` | string | no | `2026-08-19` | no | `Square-Version` |
| `webhook_signature_key` | password | yes if `trigger` | empty | yes | Subscription signature key |
| `webhook_notification_url` | string | yes if `trigger` | empty | no | Exact URL Square signed (must match subscription) |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PAN |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = payment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `source_id` | string | yes | Nonce / token / card-on-file id — never PAN |
| `amount` | number | yes | Amount in smallest unit |
| `currency` | string | yes | ISO 4217 (e.g. `USD`) |
| `idempotency_key` | string | yes | UUID; Square requires it |
| `location_id` | string | no | Else config `location_id` |
| `customer_id` | string | if card-on-file | Customer id |
| `order_id` | string | no | Attach to order |
| `note` | string | no | Payment note |

### `operation = payment_get` | `order_get` | `customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Resource id (`payment_id` / `order_id` / `customer_id` aliases) |

### `operation = refund_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payment_id` | string | yes | Payment to refund |
| `amount` | number | yes | Smallest unit |
| `currency` | string | yes | ISO currency |
| `idempotency_key` | string | yes | UUID |
| `reason` | string | no | Refund reason |

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `location_id` | string | yes | Location |
| `line_items` | array | yes | `{ name, quantity, base_price_money: { amount, currency } }` |
| `idempotency_key` | string | no | Recommended |
| `customer_id` | string | no | Buyer |
| `reference_id` | string | no | Merchant reference |

### `operation = customer_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `given_name` | string | no | First name |
| `family_name` | string | no | Last name |
| `email_address` | string | no | Email |
| `phone_number` | string | no | Phone |
| `idempotency_key` | string | no | Recommended |
| `reference_id` | string | no | External id |

### `operation = invoice_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `location_id` | string | yes | Location |
| `order_id` | string | yes | Existing order |
| `idempotency_key` | string | yes | UUID |
| `delivery_method` | string | no | e.g. `EMAIL` |
| `scheduled_at` | string | no | RFC 3339 |

### `operation = catalog_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `types` | string | no | Comma types (`ITEM,ITEM_VARIATION,…`) |
| `cursor` | string | no | Pagination |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Exact forwarded body |
| `square_signature` | string | yes | `x-square-hmacsha256-signature` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | resource ops | Square id |
| `resource_status` | string | payment / refund / order / invoice | Provider status |
| `amount` | number | money ops | Smallest unit |
| `currency` | string | money ops | Currency |
| `objects` | array | `catalog_list` | Catalog objects (ids, type, name) |
| `cursor` | string | list | Next page |
| `event_id` | string | `trigger` | Event id |
| `event_type` | string | `trigger` | e.g. `payment.updated` |
| `event_data` | object | `trigger` | Object ids/status, no secrets |
| `error` | string | failure | Redacted (`errors[].detail`) |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

## Legal

- Developer terms: https://squareup.com/us/en/legal/general/developer
- PCI via Square Web Payments SDK. Seller holds the Square account.
- Rate limits: Square returns 429; honor `Retry-After`. Application-level throttles apply per token.
- PII: customer email/phone stored at Square. GDPR/CCPA on the seller.
- Marketplace / POS apps may need App Marketplace review.

## Implementation notes

- JDK `HttpClient` only. No Square Java SDK. Headers: `Authorization: Bearer {access_token}`, `Square-Version: {api_version}`, `Content-Type: application/json`.
- `trigger`: HMAC-SHA256 over `notificationUrl + rawBody`, Base64 digest, constant-time compare to header. URL must match Square subscription **exactly** (scheme, trailing slash).
- Retry once on 429/5xx with jitter. 4xx → `{ error }`. Body cap 1_000_000. Timeout 30 s.
- Logs: operation, status, ids — never access_token, signature key, PAN, source_id beyond length.
