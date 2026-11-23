# Brex

Brex Payments API via `OfficialApiNode`. Docs: [Create vendor](https://developer.brex.com/openapi/payments_api/vendors/createvendor/) · [Create transfer](https://developer.brex.com/openapi/payments_api/transfers/createtransfer/)

Auth: Bearer `api_token`. Writes send header `Idempotency-Key` from `idempotency_key`.

`vendor_create` requires unique `company_name` and optional `email`, `phone`, `payment_accounts`. `transfer_create` requires `amount` `{amount, currency}` (cents), `description`, `external_memo`, plus `originating_account` and a counterparty (`external_bank_account_id` maps to `counterparty.payment_instrument_id`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `transaction_list` | GET | `/v2/transactions/card/primary` |
| `vendor_create` | POST | `/v1/vendors` |
| `transfer_create` | POST | `/v1/transfers` |
| `card_list` | GET | `/v2/cards` |
| `expense_list` | GET | `/v1/expenses` |
| `trigger` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `api_token` (password), `timeout_seconds`.
