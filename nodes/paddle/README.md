# Paddle

In-process StudioQuack builtin (`nodes/paddle/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_PADDLE`.

## Overview

Paddle Billing API v1 (Paddle-Version 1).

Official docs: https://developer.paddle.com/api-reference/about

DO NOT BUILD Paddle Classic (vendors.paddle.com, vendor_id, vendor_auth_code). Sandbox: sandbox-api.paddle.com. Merchant of record; tax is Paddle’s.

**DO NOT BUILD** (dropped from this node): `coupon_create`, `coupon_list`, `coupon_update`, `payment_list`, `payment_reschedule`, `plan_get`, `plan_list`, `user_list`.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `transaction_list` | `product_list` | `subscription_get` | `subscription_cancel` | `customer_list` | `discount_create` | `discount_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "api_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.paddle.com/legal/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
