# Wave

In-process StudioQuack builtin (`nodes/wave/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_WAVE`.

## Overview

Wave public GraphQL.

Official docs: https://developer.waveapps.com/hc/en-us/articles/360019968212-API-Reference

Single GraphQL endpoint. US/CA-centric. Supply official mutations in query; never PAN.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `customer_create` | `invoice_create` | `invoice_list` | `transaction_list` | `trigger`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`timeout_seconds` (number, default 25).

## Operations

See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.waveapps.com/legal
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.

## GraphQL variables

Wave is GraphQL at POST /public. Send the GraphQL `query` plus purpose-oriented variables (`businessId`, `name`, `email`, `customerId`, `items`, dates) nested as `variables.input.*` per https://developer.waveapps.com/hc/en-us/articles/360019968212-API-Reference.
