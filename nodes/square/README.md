# Square

In-process StudioQuack builtin (`nodes/square/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_SQUARE`.

## Overview

Square API 2026-07-15 (Payments, Orders, Customers, Catalog).

Official docs: https://developer.squareup.com/reference/square

source_id is a Square nonce / card-on-file id from the Web Payments SDK. Sandbox: connect.squareupsandbox.com.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `payment_create` | `payment_get` | `refund_create` | `order_create` | `order_get` | `customer_create` | `customer_get` | `invoice_create` | `catalog_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://squareup.com/legal/general/ua
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
