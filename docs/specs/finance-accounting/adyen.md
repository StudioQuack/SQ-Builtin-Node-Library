# Adyen

- **Status:** planned
- **Catalog:** `docs/catalog/finance-accounting.md`
- **Folder when built:** `nodes/adyen/`
- **Researched:** 2026-08-20
- **Official API:** Checkout **v72** + Transfers **v4** (payout only)
- **Docs:** https://docs.adyen.com/api-explorer/Checkout/72/overview
- **Auth:** api_key — secret binding: `api_key` (header `X-API-Key`). Engine does not collect or verify a webhook HMAC key.

## Scope

One node for Adyen Checkout payments (authorise, capture, refund) and optional platform payouts via the **Transfers API**. Test Checkout: `https://checkout-test.adyen.com/v72`. Live Checkout: `https://{PREFIX}-checkout-live.adyenpayments.com/checkout/v72`. Transfers test: `https://balanceplatform-api-test.adyen.com/btl/v4`; live: `https://balanceplatform-api-live.adyen.com/btl/v4`.

**PCI:** `paymentMethod` must be encrypted card blob from Adyen Web (`encryptedCardNumber` / token) **or** `storedPaymentMethodId` / `networkTxReference`. NEVER raw PAN/CVC. Reject `number` / `cvc` / `expiryMonth` as PAN fields on INPUT.

**DO NOT BUILD** the deprecated **Payout API** (`pal…/Payout`). Catalog `payout` maps to Transfers `POST /transfers` only. If the merchant has no Balance Platform entitlement, return `{ error }` (do not call pal Payout).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `payment_create` | POST | `/payments` | Authorise (Checkout v72) |
| `payment_capture` | POST | `/payments/{pspReference}/captures` | Capture an authorised payment |
| `payment_refund` | POST | `/payments/{pspReference}/refunds` | Refund a captured payment |
| `payout` | POST | `/transfers` | Transfers v4 — payout to `transferInstrumentId` / stored method. Never PAN |
| `trigger` | — | HMAC-SHA256 on notification | Standard webhook `NotificationRequestItem` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `payment_create` | no | Chooses the row above |
| `api_key` | password | yes (except `trigger`) | empty | yes | Checkout (and Transfers) API key |
| `merchantAccount` | string | yes for Checkout ops | empty | no | Merchant account code (Checkout body fallback) |
| `env` | select | no | `test` | no | `test` \| `live` |
| `live_prefix` | string | live Checkout | empty | no | Live URL prefix from Customer Area |
| `timeout_seconds` | number | no | `60` | no | Adyen processor calls can be slow |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Encrypted / token payment methods only |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never API key, HMAC key, PAN |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = payment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `amount` | number | yes | Minor units (`amount.value`) |
| `currency` | string | yes | ISO 4217 |
| `reference` | string | yes | Merchant reference (max 80) |
| `return_url` | string | yes | Shopper return URL |
| `payment_method` | object | yes | Token / `encryptedCard*` / `storedPaymentMethodId` / `type`+issuer — **no PAN** |
| `shopper_reference` | string | no | Recurring shopper id |
| `shopper_email` | string | no | Email |
| `country_code` | string | no | ISO country |
| `idempotency_key` | string | no | `Idempotency-Key` header |

### `operation = payment_capture` | `payment_refund`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `psp_reference` | string | yes | Payment `pspReference` |
| `amount` | number | yes | Minor units |
| `currency` | string | yes | Must match original |
| `reference` | string | no | Merchant reference |
| `idempotency_key` | string | no | Idempotency |

### `operation = payout`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `amount` | number | yes | Minor units |
| `currency` | string | yes | ISO currency |
| `balance_account_id` | string | yes | Source balance account |
| `category` | string | yes | e.g. `bank` \| `card` \| `internal` |
| `transfer_instrument_id` | string | one of | Counterparty instrument — **preferred** |
| `stored_payment_method_id` | string | one of | Tokenized payout instrument |
| `priority` | string | bank | e.g. `regular` \| `instant` |
| `reference` | string | no | Description |

Reject `card.number` / PAN in counterparty.

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Forwarded JSON (standard webhook) |
| `hmac_signature` | string | no | Payload `additionalData.hmacSignature` if already extracted; else parse from body |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `psp_reference` | string | Checkout | Adyen pspReference |
| `result_code` | string | `payment_create` | `Authorised`, `RedirectShopper`, `Refused`, … |
| `refusal_reason` | string | refused | Safe reason code (no PAN) |
| `action` | object | redirect / 3DS | Next action for the client SDK — **strip any card blobs** |
| `transfer_id` | string | `payout` | Transfers id |
| `event_code` | string | `trigger` | e.g. `AUTHORISATION`, `CAPTURE` |
| `success` | boolean | `trigger` | Notification `success` |
| `merchant_reference` | string | `trigger` | Merchant ref |
| `event_data` | object | `trigger` | Ids/status/amount; strip `additionalData` card fields |
| `error` | string | failure | Redacted |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Default 30 |

## Legal

- Terms: https://www.adyen.com/legal/terms-and-conditions and https://docs.adyen.com/development-resources/webhooks/verify-hmac-signatures
- Enterprise acquiring; live keys are production-sensitive. Customer holds the Adyen contract.
- PCI: encrypted client payload or stored method only.
- Rate limits: Adyen may 429; backoff. Processor timeouts up to 60 s are expected.
- PII / residency: shopper data stored in Adyen’s region per company account. GDPR DPA via Adyen.
- **DO NOT BUILD** pal Payout API (deprecated; Adyen directs new integrations to Transfers).

## Implementation notes

- JDK `HttpClient` only. Header `X-API-Key`, `Content-Type: application/json`. Live Checkout host requires `live_prefix`.
- `trigger`: Standard payments HMAC is over a **pipe-joined subset of fields**, not the raw JSON (see Adyen “Verify HMAC signatures”). Implement that construction; constant-time Base64 compare. Do not use a vendor SDK unless HMAC field-order tests fail — if so, document the exception.
- Retry once on 429/5xx with jitter. 4xx → `{ error }`. Body cap 1_000_000.
- Logs: operation, resultCode, pspReference — never api_key, hmac_key, encrypted card blobs, PAN.
