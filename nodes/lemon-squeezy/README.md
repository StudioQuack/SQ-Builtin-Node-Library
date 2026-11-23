# Lemon Squeezy

In-process StudioQuack builtin (`nodes/lemon-squeezy/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_LEMON_SQUEEZY`.

## Overview

Lemon Squeezy JSON:API v1 (Stripe-owned since 2024; API unchanged).

Official docs: https://docs.lemonsqueezy.com/api

Merchant of record; tax handled by Lemon Squeezy. Accept: application/vnd.api+json (engine sends JSON).

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `order_get` | `subscription_get` | `subscription_cancel` | `customer_get` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "api_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.lemonsqueezy.com/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
