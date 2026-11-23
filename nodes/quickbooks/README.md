# QuickBooks

In-process StudioQuack builtin (`nodes/quickbooks/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_QUICKBOOKS`.

## Overview

QuickBooks Online Accounting API v3, minorversion=75.

Official docs: https://developer.intuit.com/app/developer/qbo/docs/api/accounting/all-entities/invoice

Pin minorversion=75 on query. Employee is a payroll/HR record, not an Intuit login. Sandbox host sandbox-quickbooks.api.intuit.com. No PAN on payment_create.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `bill_create` | `bill_get` | `bill_list` | `bill_update` | `bill_delete` | `customer_create` | `customer_get` | `customer_list` | `customer_update` | `employee_create` | `employee_get` | `employee_list` | `estimate_create` | `estimate_get` | `estimate_list` | `estimate_update` | `estimate_send` | `invoice_create` | `invoice_get` | `invoice_list` | `invoice_update` | `invoice_send` | `payment_create` | `payment_get` | `payment_list` | `payment_update` | `payment_delete` | `payment_void` | `purchase_get` | `purchase_list` | `report_transaction` | `vendor_create` | `vendor_get` | `vendor_list` | `vendor_update` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}` — OAuth access token minted outside StudioQuack.

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.intuit.com/legal/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
