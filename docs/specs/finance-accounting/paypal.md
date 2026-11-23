# PayPal

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/paypal/`
- **Researched:** 2026-08-20
- **Official API:** REST **Orders v2** + **Payments captures** + **Payouts v1**
- **Docs:** https://developer.paypal.com/docs/api/orders/v2/
- **Auth:** bearer `access_token` minted outside StudioQuack (`POST /v1/oauth2/token`). No `client_id` / `client_secret` / `webhook_id` on this node.

## Scope

One node for PayPal Checkout (Orders v2), capture refunds, and Payouts. Token: `POST /v1/oauth2/token` (`grant_type=client_credentials`). Hosts: live `https://api-m.paypal.com`, sandbox `https://api-m.sandbox.paypal.com`. `trigger` verifies the forwarded event via `POST /v1/notifications/verify-webhook-signature`.

**PCI:** PayPal JS / Hosted Fields / vault tokens only (`paypal.vault_id`, order id after buyer approval). Reject PAN/CVC on INPUT. Do not send `payment_source.card` number objects.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_create` | POST | `/v2/checkout/orders` | Create an order (`intent` CAPTURE or AUTHORIZE) |
| `order_capture` | POST | `/v2/checkout/orders/{id}/capture` | Capture after buyer approval |
| `refund` | POST | `/v2/payments/captures/{id}/refund` | Refund a capture |
| `payout_create` | POST | `/v1/payments/payouts` | Batch payout to recipients |
| `payout_get` | GET | `/v1/payments/payouts/{payout_batch_id}` | Payout batch status |
| `payout_item_get` | GET | `/v1/payments/payouts-item/{payout_item_id}` | Single payout item |
| `payout_item_cancel` | POST | `/v1/payments/payouts-item/{payout_item_id}/cancel` | Cancel an unclaimed item |
| `trigger` | POST | `/v1/notifications/verify-webhook-signature` | Verify then emit event ids/status |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_create` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | OAuth access token minted outside StudioQuack |
| `env` | select | no | `sandbox` | no | `sandbox` \| `live` (selects api-m host) |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets, no PAN |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never tokens or secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `intent` | string | no | `CAPTURE` (default) \| `AUTHORIZE` |
| `currency` | string | yes | ISO 4217 (e.g. `USD`) |
| `amount` | string | yes | Decimal string (`"10.00"`) — `purchase_units[0].amount.value` |
| `description` | string | no | Soft descriptor / description |
| `return_url` | string | no | Application return URL |
| `cancel_url` | string | no | Cancel URL |
| `reference_id` | string | no | Merchant invoice / reference |
| `paypal_request_id` | string | no | `PayPal-Request-Id` (idempotency) |

### `operation = order_capture`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Order id |
| `paypal_request_id` | string | no | Idempotency |

### `operation = refund`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Capture id |
| `amount` | string | no | Partial refund value |
| `currency` | string | if amount | Currency |
| `note` | string | no | Note to payer |
| `paypal_request_id` | string | no | Idempotency |

### `operation = payout_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sender_batch_id` | string | yes | Unique batch id |
| `email_subject` | string | no | Email subject to recipients |
| `items` | array | yes | `{ receiver, amount, currency, note?, sender_item_id? }`. Receiver = PayPal email or `payer_id`, never a card |

### `operation = payout_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payout_batch_id` | string | yes | Batch id |

### `operation = payout_item_get` | `payout_item_cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payout_item_id` | string | yes | Item id |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Forwarded JSON body |
| `paypal_auth_algo` | string | yes | `PAYPAL-AUTH-ALGO` |
| `paypal_cert_url` | string | yes | `PAYPAL-CERT-URL` (must be `api*.paypal.com` cert host) |
| `paypal_transmission_id` | string | yes | `PAYPAL-TRANSMISSION-ID` |
| `paypal_transmission_sig` | string | yes | `PAYPAL-TRANSMISSION-SIG` |
| `paypal_transmission_time` | string | yes | `PAYPAL-TRANSMISSION-TIME` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | orders / captures | Resource id |
| `resource_status` | string | orders / payouts | `CREATED`, `COMPLETED`, `SUCCESS`, … |
| `approve_url` | string | `order_create` | HATEOAS `rel=approve` (buyer redirect) |
| `capture_id` | string | capture / refund | Capture id |
| `payout_batch_id` | string | payout ops | Batch id |
| `payout_item_id` | string | item ops | Item id |
| `event_id` | string | `trigger` | Webhook event id |
| `event_type` | string | `trigger` | e.g. `PAYMENT.CAPTURE.COMPLETED` |
| `event_data` | object | `trigger` | Resource ids/status only |
| `error` | string | failure | Redacted |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Default 60 / 30 |

## Legal

- Developer agreement: https://www.paypal.com/us/legalhub/paypal/developer-agreement
- Acceptable Use: https://www.paypal.com/us/legalhub/paypal/acceptableuse-full
- Payouts: US/EU and recipient-country restrictions; sanctions screening is PayPal’s. Operator holds the PayPal Business account.
- Rate limits: not a single public number; honor 429 + `Retry-After`. Use `PayPal-Request-Id` for captures.
- PII: payer email, shipping, payout receiver emails leave the daemon. GDPR/CCPA on the merchant.
- PCI: no CHD on this node. Prohibited: high-risk MCC without PayPal approval.

## Implementation notes

- JDK `HttpClient` only. Cache OAuth token in-memory until `expires_in - 60s`; never persist, never log.
- Mint `access_token` outside StudioQuack (`POST /v1/oauth2/token`, Basic `client_id:client_secret`, `grant_type=client_credentials`). API calls: `Authorization: Bearer {access_token}`, `Content-Type: application/json`.
- `trigger` is ingress (BPMN message event). Webhook signature verification is not a node config field.
- Retry once on 429/5xx with jitter. 4xx → `{ error }`. Body cap 1_000_000. Timeout 30 s.
- Logs: operation, HTTP status, order/payout ids — never client_secret, access_token, PAN.
