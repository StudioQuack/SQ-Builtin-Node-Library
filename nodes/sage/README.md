# Sage

In-process StudioQuack builtin (`nodes/sage/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_SAGE`.

## Overview

Sage Business Cloud Accounting API v3.1.

Official docs: https://developer.sage.com/accounting/

Access token TTL ~5 minutes; rotating refresh. No official webhook on this node.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `contact_create` | `invoice_create` | `invoice_get` | `payment_create`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`business_id` (required): sent as the official `X-Business` header on every request.

`timeout_seconds` (number, default 25).

## Operations

| operation | HTTP | Path | Body |
|---|---|---|---|
| `contact_create` | POST | `/contacts` | Nested `contact`: name, contact_type_ids (required), reference, tax_number, notes, email, telephone, main_address |
| `invoice_create` | POST | `/sales_invoices` | Nested `sales_invoice`: contact_id, date, invoice_lines (required) |
| `invoice_get` | GET | `/sales_invoices/{id}` | — |
| `payment_create` | POST | `/contact_payments` | Nested `contact_payment`: transaction_type_id, contact_id, bank_account_id, date, total_amount (required) |

## Legal

- Terms: https://www.sage.com/en-us/legal/terms-and-conditions/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
