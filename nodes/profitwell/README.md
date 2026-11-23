# ProfitWell

In-process StudioQuack builtin (`nodes/profitwell/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_PROFITWELL`.

## Overview

ProfitWell Metrics API v2.

Official docs: https://profitwellapiv2.docs.apiary.io/
Setup: https://www.paddle.com/help/profitwell-metrics/setup/get-started/profit-well-api

Paddle-owned. Trailing slashes required. Paddle Billing /metrics stays on the paddle node — do not fork. Auth is the raw private token (no Bearer).

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `metric_get` | `plan_list` | `transaction_list`

Auth (password secrets): `{"type": "header", "header": "Authorization", "prefix": "", "configKey": "api_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.paddle.com/legal/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
