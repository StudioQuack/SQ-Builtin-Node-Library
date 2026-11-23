# Ramp

In-process StudioQuack builtin (`nodes/ramp/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_RAMP`.

## Overview

Ramp Developer API v1.

Official docs: https://docs.ramp.com/developer-api/v1/

card_list is metadata/last4 only — never PAN.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `transaction_list` | `reimbursement_list` | `vendor_create` | `card_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}` — OAuth access token minted outside StudioQuack.

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://ramp.com/legal/platform-agreement
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
