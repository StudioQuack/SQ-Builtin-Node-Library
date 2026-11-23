# Braintree


GraphQL ops keep `variables` as an escape-hatch envelope; purpose fields merge into it via bodyMap. `query` remains required (the engine does not inject a GraphQL document).

In-process StudioQuack builtin (`nodes/braintree/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_BRAINTREE`.

## Overview

Braintree GraphQL (Braintree-Version 2019-01-01).

Official docs: https://developer.paypal.com/braintree/graphql/guides/making_api_calls

POST /graphql with official `query` + `variables`. Purpose-oriented fields (`paymentMethodId`, `amount`, `transactionId`, `planId`, `firstName`, `lastName`, `email`) nest into `variables`. Charge with a Drop-in / client SDK nonce — never PAN. Header `Braintree-Version: 2019-01-01` is sent on every call. Sandbox host: `payments.sandbox.braintree-api.com` (edit `api_spec.baseUrl`).

HARD PCI: cardholder data never enters the graph. Use processor tokens (pm_, nonce, encryptedCard, vault id) only. Never PAN, CVC, track data, or raw bank account numbers on INPUT/OUTPUT/logs. API keys are StudioQuack password secrets.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `client_token_create` | `transaction_sale` | `transaction_void` | `transaction_refund` | `customer_create` | `subscription_create` | `trigger`

Auth (password secrets): `{"type": "basic", "userKey": "public_key", "configKey": "private_key"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.braintreepayments.com/legal
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
