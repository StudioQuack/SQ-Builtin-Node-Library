# Revolut Business

In-process StudioQuack builtin (`nodes/revolut-business/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_REVOLUT_BUSINESS`.

## Overview

Revolut Business API 1.0.

Official docs: https://developer.revolut.com/docs/guides/manage-accounts/introduction

Business accounts only (Grow+). No personal API. Prefer saved counterparty ids over raw account numbers on the graph.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `account_list` | `counterparty_create` | `payment_create` | `transaction_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}` — OAuth access token minted outside StudioQuack (JWT client assertion is not performed here).

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.revolut.com/legal/business-terms/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
