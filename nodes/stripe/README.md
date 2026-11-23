# Stripe

In-process StudioQuack builtin (`nodes/stripe/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_STRIPE`.

## Overview

Stripe REST v1, pin 2026-07-29.dahlia (Stripe-Version).

Official docs: https://docs.stripe.com/api

DO NOT BUILD token/source/card/charge_create (retired + PCI). Charges are read-only. payment_method must be pm_… from Stripe.js / Payment Element.

**DO NOT BUILD** (dropped from this node): `token_create`, `source_create`, `source_get`, `source_delete`, `card_add`, `card_get`, `card_remove`, `charge_create`, `charge_update`.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `balance_get` | `payment_intent_create` | `payment_intent_get` | `payment_intent_confirm` | `customer_create` | `customer_get` | `customer_list` | `customer_update` | `customer_delete` | `coupon_create` | `coupon_list` | `meter_event_create` | `refund_create` | `subscription_create` | `invoice_get` | `payout_list` | `charge_get` | `charge_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "secret_key"}`

`api_version` (string, default `2026-07-29.dahlia`) is sent as `Stripe-Version` via extraHeaders. Blank omits the header.

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://stripe.com/legal/ssa
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
