# Kraken

In-process StudioQuack builtin (`nodes/kraken/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_KRAKEN`.

## Overview

Kraken REST 0.

Official docs: https://docs.kraken.com/api/

Crypto exchange ToS; geo KYC. Not investment advice. Private methods require API-Sign (engine limitation).

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `ticker` | `ohlc` | `balance` | `add_order` | `cancel_order` | `ledgers`

Auth (password secrets): `{"type": "header", "header": "API-Key", "prefix": "", "configKey": "api_key"}`

Private endpoints also need API-Sign (HMAC of a private secret). OfficialApiNode cannot HMAC, so signed private methods cannot work. There is no `private_key` config.

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.kraken.com/legal
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
