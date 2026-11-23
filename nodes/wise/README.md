# Wise

In-process StudioQuack builtin (`nodes/wise/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_WISE`.

## Overview

Wise Platform API.

Official docs: https://docs.wise.com

SCA and sanctions screening are Wise’s. StudioQuack does not custody funds. Sandbox: `api.wise-sandbox.com` (V1 `api.sandbox.transferwise.tech` retired 2026-06-30). Deprecated: `balance_list` GET `/v1/borderless-accounts` — use `balances_list` GET `/v4/profiles/{profileId}/balances`. Deprecated: `statement_get` — use `balance_statement_get` GET `/v1/profiles/{profileId}/balance-statements/{balanceId}/statement.json`.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `balance_list` | `balances_list` | `currency_list` | `statement_get` | `balance_statement_get` | `rate_get` | `profile_get` | `profile_list` | `recipient_list` | `quote_create` | `quote_get` | `transfer_create` | `transfer_get` | `transfer_list` | `transfer_delete` | `transfer_execute` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "api_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://wise.com/terms-and-conditions
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
