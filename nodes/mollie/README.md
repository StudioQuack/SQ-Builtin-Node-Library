# Mollie

In-process StudioQuack builtin (`nodes/mollie/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_MOLLIE`.

## Overview

Mollie API v2.

Official docs: https://docs.mollie.com/reference/overview

EEA PSP. Redirect / Mollie Components — never PAN.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `payment_create` | `payment_get` | `refund_create` | `customer_create` | `mandate_create` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "api_key"}`

`timeout_seconds` (number, default 25).

## Operations

`payment_create` / `refund_create` take Mollie `amount` `{currency, value}`. Config `profile_id` maps to body `profileId`. Docs: https://docs.mollie.com/reference/create-payment. Never send card PAN.

## Legal

- Terms: https://www.mollie.com/legal
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
