# Adyen


Payments take `value` / `currency` merged into the official `amount` envelope via bodyMap. Docs: https://docs.adyen.com/api-explorer/Checkout/latest/post/payments

In-process StudioQuack builtin (`nodes/adyen/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_ADYEN`.

## Overview

Adyen Checkout v72.

Official docs: https://docs.adyen.com/api-explorer/Checkout/72/overview

DO NOT BUILD catalog payout on this host (Transfers API is a different base). Live Checkout uses {PREFIX}-checkout-live.adyenpayments.com/checkout/v72. paymentMethod = encrypted card / token / storedPaymentMethodId only.

**DO NOT BUILD** (dropped from this node): `payout`.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `payment_create` | `payment_capture` | `payment_refund` | `trigger`

Auth (password secrets): `{"type": "header", "header": "X-API-Key", "prefix": "", "configKey": "api_key"}`

`merchantAccount` (string; Checkout body fallback when INPUT omits it). `timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.adyen.com/legal/terms-and-conditions
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
