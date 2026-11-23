# NetSuite

In-process StudioQuack builtin (`nodes/netsuite/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_NETSUITE`.

## Overview

SuiteTalk REST record + SuiteQL (OAuth 2.0).

Official docs: https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/book_1559132836.html

DO NOT BUILD SOAP / new TBA (Oracle 2026.1+ prefers REST + OAuth 2). saved_search omitted — use SuiteQL. Host is `https://{account_id}.suitetalk.api.netsuite.com` (`account_id` interpolated into baseUrl). Prefer: transient on SuiteQL is not sent by OfficialApiNode.

**DO NOT BUILD** (dropped from this node): `saved_search`.

Do not place secrets on INPUT. Never log tokens or account numbers.

operation=trigger is kind:ingress — it does not open a socket. Use a BPMN message start / catch / boundary event; StudioQuack webhook ingress correlates onto those events. There is no Webhook node.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `record_get` | `record_create` | `record_update` | `record_delete` | `suiteql`

Auth (password secrets): `{"type": "bearer", "configKey": "access_token"}`

`timeout_seconds` (number, default 25).

## Operations

`record_create` / `record_update` merge INPUT `record` at the JSON root (bodyMap `$`). SuiteQL sends `q` and `params`. See `api_spec.json` for method/path/required/queryFrom/bodyFrom.

## Legal

- Terms: https://www.oracle.com/legal/terms.html
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/broker account; StudioQuack does not custody funds.
- Researched: 2026-08-20.
