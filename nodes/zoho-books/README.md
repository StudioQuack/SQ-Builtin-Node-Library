# Zoho Books

In-process StudioQuack builtin (`nodes/zoho-books/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_ZOHO_BOOKS`.

## Overview

Zoho Books API v3.

Official docs: https://www.zoho.com/books/api/v3/introduction/

Distinct from Zoho CRM. No PAN. organization_id required. Config `dc` is the TLD interpolated into `https://www.zohoapis.{dc}/books/v3` (`com` | `eu` | `in` | `com.au` | `jp` | `sa` | `ca` | `com.cn`). Official multi-DC table: https://www.zoho.com/books/api/v3/introduction/#multidc.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | contact_create | no | HTTP verb to run. EXACT: contact_create / invoice_create / invoice_get / payment_create / bill_create / trigger. Default contact_create. operation=trigger is kind:ingress — it d... |
| `access_token` | password | yes |  | yes | Zoho Books credential (header Authorization). StudioQuack secret. Never INPUT, never logged. |
| `organization_id` | string | yes |  | no | organization_id query on every call. |
| `dc` | string | no | com | no | Data center TLD interpolated into https://www.zohoapis.{dc}/books/v3. EXACT: com / eu / in / com.au / jp / sa / ca / com.cn. Official: https://www.zoho.com/books/api/v3/introduc... |
| `payment_terms` | number | no |  | no | Net payment days. contact_create and invoice_create. Example: 15. |
| `billing_address` | object | no |  | no | Billing address {address,city,state,zip,country}. contact_create. |
| `shipping_address` | object | no |  | no | Shipping address {address,city,state,zip,country}. contact_create. |
| `company_name` | string | no |  | no | Legal company name. contact_create. Example: Acme Corp. |
| `contact_name` | string | no |  | no | Display name. Required on contact_create. Example: Acme Corp. |
| `email` | string | no |  | no | Contact email. contact_create. PII — do not log. Example: billing@example.com. |
| `contact_type` | string | no |  | no | Zoho Books contact type. EXACT: customer / vendor. contact_create. Example: customer. https://www.zoho.com/books/api/v3/contacts/ |
| `contact_persons` | list | no |  | no | Contact persons [{first_name,last_name,email,phone}]. contact_create. |
| `phone` | string | no |  | no | Contact phone. contact_create. PII — do not log. |
| `customer_id` | string | no |  | no | Customer contact id. Required on invoice_create and payment_create. Example: 8527119000000099001. |
| `notes` | string | no |  | no | Notes on invoice_create or bill_create. |
| `due_date` | string | no |  | no | Due date YYYY-MM-DD. invoice_create / bill_create. |
| `date` | string | no |  | no | Document date YYYY-MM-DD. invoice_create, payment_create, bill_create. Example: 2026-01-15. |
| `currency_id` | string | no |  | no | Zoho currency id for invoice_create when not using the org default. |
| `line_items` | string | no |  | no | Line items [{item_id, quantity, rate}] for invoice_create / bill_create. Required. Example: [{"item_id":"…","quantity":1,"rate":100}]. |
| `invoice_number` | string | no |  | no | Invoice number. invoice_create. Auto-generated when omitted. Max 100 chars. |
| `invoice_id` | string | no |  | no | URL path parameter `invoice_id` for operation: invoice_get. Required by: invoice_get. |
| `payment_mode` | string | no |  | no | payment_create. EXACT: check / cash / creditcard / banktransfer / bankremittance / autotransaction / others. Required by Zoho. Example: banktransfer. |
| `reference_number` | string | no |  | no | Payment reference. payment_create. Max 100 chars. |
| `amount` | string | no |  | no | Payment amount. Required on payment_create. Example: 150.00. |
| `description` | string | no |  | no | Payment description / notes. payment_create. Example: Invoice settlement. |
| `invoices` | list | no |  | no | payment_create invoices [{invoice_id, amount_applied}]. Required by Zoho. Example: [{"invoice_id":"123","amount_applied":50}]. |
| `account_id` | string | no |  | no | Deposit account id for payment_create. |
| `bill_number` | string | no |  | no | Vendor bill number. bill_create. Example: BILL-001. |
| `vendor_id` | string | no |  | no | Vendor contact id. Required on bill_create. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout seconds. Default 25. |

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.zoho.com/books/terms.html
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.

## Write fields

Contacts take `contact_name`, `contact_type`, `company_name`, `email`, addresses. Invoices take `customer_id`, `line_items`, dates. Payments take `customer_id`, `amount`, `payment_mode`, `date`, `invoices`. Bills take `vendor_id`, `line_items`, `bill_number`. Docs: https://www.zoho.com/books/api/v3/contacts/
