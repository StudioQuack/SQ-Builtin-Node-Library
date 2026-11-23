# Interactive Brokers

In-process StudioQuack builtin (`nodes/interactive-brokers/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_INTERACTIVE_BROKERS`.

## Overview

IBKR Web API / Client Portal API.

Official docs: https://www.interactivebrokers.com/campus/ibkr-api-page/webapi-doc/

Regulated brokerage. Official Client Portal Gateway or Web API OAuth 2 only. DO NOT BUILD unofficial gateways or TWS socket as this HTTP node. Market-data licenses apply. Not investment advice. Customer needs IBKR API agreement.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `account_summary` | `place_order` | `cancel_order` | `positions` | `market_data` | `trigger`

Auth (password secrets): `{"type": "none"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.interactivebrokers.com/en/index.php?f=15650
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
