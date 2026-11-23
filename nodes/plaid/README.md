# Plaid

In-process StudioQuack builtin (`nodes/plaid/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_PLAID`.

## Overview

Plaid API 2020-09-14.

Official docs: https://plaid.com/docs/api/

No screen-scraping. End-user consent via Plaid Link (this node only creates link_token). access_token is a secret — prefer config, never logs. Sandbox: sandbox.plaid.com. processor_token is for Stripe/Adyen — not bank passwords.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `link_token_create` | `item_public_token_exchange` | `accounts_get` | `transactions_sync` | `auth_get` | `identity_get` | `processor_token_create` | `trigger`

Auth (password secrets): `{"type": "header", "header": "Plaid-Secret", "prefix": "", "configKey": "secret"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://plaid.com/legal/#end-user-privacy-policy
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
