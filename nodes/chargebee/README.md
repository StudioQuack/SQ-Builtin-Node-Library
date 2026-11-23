# Chargebee

Chargebee API v2 (`OfficialApiNode`). Host `https://{site}.chargebee.com/api/v2`. Docs: [Create a customer](https://apidocs.chargebee.com/docs/api/customers/create-a-customer) · [Subscriptions](https://apidocs.chargebee.com/docs/api/subscriptions)

Writes use `application/x-www-form-urlencoded` (official). `subscription_create` sends Product Catalog 1.0 `plan_id` plus optional `subscription[billing_cycles]`, `subscription[trial_end]`, `subscription[start_date]`. Item-price (`subscription_items[0][item_price_id]`) is not wired — Product Catalog 2.0 sites need that shape.

Auth: config `api_key` sent as `Authorization: Basic` (operator stores the Basic token Chargebee issued, or the key in the form the engine expects).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `customer_create` | POST | `/customers` |
| `invoice_list` | GET | `/invoices` |
| `invoice_pdf` | POST | `/invoices/{id}/pdf` |
| `subscription_create` | POST | `/subscriptions` |
| `subscription_cancel` | POST | `/subscriptions/{id}/cancel` |
| `subscription_delete` | POST | `/subscriptions/{id}/delete` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `api_key` (password), `site`, `timeout_seconds`.
