# Recurly

In-process StudioQuack builtin (`nodes/recurly/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_RECURLY`.

## Overview

Recurly API v3 schema v2021-02-25.

Official docs: https://recurly.com/developers/api/v2021-02-25/

Bind api_key as Base64(api_key + ':'). Recurly.js tokens only — never PAN. EU host eu.recurly.com when applicable. Optional subscription_cancel body `timeframe` (`bill_date` | `term_end`) — https://docs.recurly.com/recurly-subscriptions/v2021-02-25/reference/cancel_subscription

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `account_create` | `subscription_create` | `subscription_cancel` | `invoice_list` | `trigger`

Auth (password secrets): `{"type": "header", "header": "Authorization", "prefix": "Basic ", "configKey": "api_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://recurly.com/legal/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
