# TrueLayer

In-process StudioQuack builtin (`nodes/truelayer/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_TRUELAYER`.

## Overview

TrueLayer **Payments API v3** + **Data API v3** (recommended for new data integrations). Data v3 is currently UK-only.

Official docs: https://docs.truelayer.com/docs/enable-your-users-to-connect-their-bank-account · https://docs.truelayer.com/docs/data-api-basics · https://docs.truelayer.com/reference/create-connection

UK/EEA PSD2. Consent receipts required. Verify Tl-Signature on BPMN ingress. Sandbox: `api.truelayer-sandbox.com`.

Do not place secrets on INPUT. Never log tokens or account numbers.

## Migration

Data v1 (`GET /data/v1/auth`, `GET /data/v1/accounts`, `GET /data/v1/accounts/{id}/transactions`) is replaced:

| operation | Data v3 |
|---|---|
| `auth_link` | POST `/v3/data-connections` (hosted connection + authorisation) |
| `accounts_list` | GET `/v3/connected-accounts` + header `Connection-Id` |
| `transactions_list` | GET `/v3/connected-accounts/{account_id}/transactions/requests/{request_id}` + `Connection-Id` |

v3 transactions are async: create a request first, then GET by `request_id`. Payments stay `/v3/payments`.

## Configuration parameters

`operation` (required): `auth_link` | `accounts_list` | `transactions_list` | `payment_create` | `payment_get` | `trigger`

Auth: `{"type": "bearer", "configKey": "access_token"}` (`data` scope for Data v3). Mint the token outside StudioQuack.

| Name | Type | Purpose |
|---|---|---|
| `connection_id` | string | `Connection-Id` header (input overrides config) |
| `timeout_seconds` | number | Default 25 |

## Legal

- Terms: https://truelayer.com/legal
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
