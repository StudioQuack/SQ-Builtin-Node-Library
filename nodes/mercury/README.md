# Mercury

In-process StudioQuack builtin (`nodes/mercury/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_MERCURY`.

## Overview

Mercury API v1.

Official docs: https://docs.mercury.com

US business banking. Webhooks exist in live only (not sandbox). Prefer recipientId over raw routing numbers. `payment_create` requires `recipientId`, `amount`, `paymentMethod` (`ach` | `check` | `domesticWire`), and `idempotencyKey` (https://docs.mercury.com/reference/createtransaction).

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `account_list` | `transaction_list` | `recipient_create` | `payment_create` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "api_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://mercury.com/legal/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
