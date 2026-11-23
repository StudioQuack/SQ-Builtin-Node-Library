# Gumroad

In-process StudioQuack builtin (`nodes/gumroad/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_GUMROAD`.

## Overview

Gumroad OAuth API v2. Query `access_token` still works; official preferred is `Authorization: Bearer` (https://app.gumroad.com/api).

Official docs: https://app.gumroad.com/api

Ping/webhooks land on BPMN message events — same node, no Gumroad Trigger sibling.

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

| Parameter | Purpose |
|---|---|
| `operation` | Default `sale_list` \| `product_list` \| `subscriber_list` \| `trigger` |
| `access_token` | OAuth token (query `access_token`) — secret |
| `product_id` | Optional default product id for **`subscriber_list`** when INPUT omits it |
| `timeout_seconds` | Default 25 |

## Operations

| operation | Notes |
|---|---|
| `sale_list` | Optional filters: `page`, `after`, `before`, `email` (`YYYY-MM-DD` for dates) |
| `product_list` | Lists products; unknown filters are ignored — Gumroad may return `200` with an empty list |
| `subscriber_list` | **`product_id` required** (INPUT or config); optional `page`, `email` |
| `trigger` | Ingress only (BPMN message event) |

Agent tools should expose **`product_id`** on INPUT (not `id` or `query`) for `subscriber_list`. Run `product_list` first to obtain product ids.

See `api_spec.json` for method/path/required/queryFrom.

## Legal

- Terms: https://gumroad.com/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
