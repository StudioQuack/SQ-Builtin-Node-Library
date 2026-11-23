# PayPal

In-process StudioQuack builtin (`nodes/paypal/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_PAYPAL`.

## Overview

PayPal REST Orders v2 + Payouts v1 + Payments captures.

Official docs: https://developer.paypal.com/docs/api/orders/v2/

Bind a client-credentials access_token (or refresh it outside the graph). Sandbox host is api-m.sandbox.paypal.com. PCI: PayPal JS / vault tokens only.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `order_create` | `order_capture` | `refund` | `payout_create` | `payout_get` | `payout_item_get` | `payout_item_cancel` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.paypal.com/us/legalhub/home
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
