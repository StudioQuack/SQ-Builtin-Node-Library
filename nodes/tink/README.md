# Tink

In-process StudioQuack builtin (`nodes/tink/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_TINK`.

## Overview

Tink / Visa Data API v2.

Official docs: https://docs.tink.com

Open banking. User token needs accounts:read balances:read transactions:read. No screen-scrape fallback.

Do not place secrets on INPUT. Never log tokens or account numbers.

**AI Agent tools:** Map DuckTape `request` → INPUT (`operation`, `grant_type`, `scope`, …). Nested `config` / `args` are flattened. `grantType` → `grant_type` on `authorize`. `authorize` does not send the stored `access_token` Bearer header (client credentials on the form body only). Data reads need a user token with the right scopes.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `authorize` | `accounts_list` | `transactions_list` | `identity_get`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://tink.com/legal/
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
