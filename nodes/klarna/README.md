# Klarna

In-process StudioQuack builtin (`nodes/klarna/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_KLARNA`.

## Overview

Klarna Payments + Order Management.

Official docs: https://docs.klarna.com

BNPL. Consumer-credit disclosures. Klarna JS widget — no PAN. Config `region` is the official API hostname (`api.klarna.com` / `api-na.klarna.com` / `api-oc.klarna.com` or playground variants) substituted into `baseUrl` as `https://{region}`.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `session_create` | `order_get` | `order_capture` | `order_refund` | `trigger`

Auth (password secrets): `{"type": "basic", "userKey": "username", "configKey": "password"}`

`region` (select, official hostname → `https://{region}`).

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.klarna.com/international/legal/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
