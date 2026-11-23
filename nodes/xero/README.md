# Xero

In-process StudioQuack builtin (`nodes/xero/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_XERO`.

## Overview

Xero Accounting API 2.0.

Official docs: https://developer.xero.com/documentation/api/accounting/overview

Expense Claims / Receipts / Employees decommissioned 2026-04-28 — omitted. Partner review required for Custom Connections.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `contact_create` | `contact_get` | `contact_list` | `contact_update` | `invoice_create` | `invoice_get` | `invoice_list` | `invoice_update` | `payment_create` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}` — OAuth access token minted outside StudioQuack.

`tenant_id` (string): sent as `Xero-Tenant-Id` on every Accounting API call.

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://developer.xero.com/xero-developer-platform-terms-conditions/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
