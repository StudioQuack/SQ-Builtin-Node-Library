# Invoice Ninja

In-process StudioQuack builtin (`nodes/invoice-ninja/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_INVOICE_NINJA`.

## Overview

Invoice Ninja API v5.

Official docs: https://api-docs.invoicing.co/

**Account tier:** Hosted free/unpaid accounts return HTTP **403** (`Feature not available with free / unpaid account`) for essentially all API operations — this is billing, not a bad `api_token` (invalid tokens typically 401). Use a paid or trial-eligible workspace before agent/CI testing.

Self-host: OfficialApiNode baseUrl is invoicing.co; self-host URL must be the engine egress target. Keep URL on customer overlay. Official also requires `X-Requested-With: XMLHttpRequest` (https://api-docs.invoicing.co/).

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `client_create` | `client_delete` | `client_get` | `client_list` | `expense_create` | `expense_delete` | `expense_get` | `expense_list` | `invoice_create` | `invoice_delete` | `invoice_email` | `invoice_get` | `invoice_list` | `payment_create` | `payment_delete` | `payment_get` | `payment_list` | `quote_create` | `quote_delete` | `quote_email` | `quote_get` | `quote_list` | `task_create` | `task_delete` | `task_get` | `task_list` | `trigger`

Auth (password secrets): `{"type": "header", "header": "X-API-TOKEN", "prefix": "", "configKey": "api_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.invoiceninja.com/legal/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
