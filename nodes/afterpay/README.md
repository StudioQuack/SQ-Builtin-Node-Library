# Afterpay


order_create / refund take `amount_value` (amount.amount) and `currency` (amount.currency) merged into the official `amount` envelope via bodyMap. Docs: https://developers.afterpay.com/afterpay-online/reference

In-process StudioQuack builtin (`nodes/afterpay/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_AFTERPAY`.

## Overview

Afterpay / Clearpay Online API v2.

Official docs: https://developers.afterpay.com/afterpay-online-developer/api-reference/reference/introduction

BNPL. Clearpay in UK. Regions: AU, NZ, US, CA, UK, FR, IT, ES on https://global-api.afterpay.com. Age/credit rules. Token from Afterpay JS — never PAN.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `order_create` | `order_auth` | `order_capture` | `order_void` | `refund` | `trigger`

Auth (password secrets): `{"type": "basic", "userKey": "merchant_id", "configKey": "secret_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.afterpay.com/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
