# Odoo

In-process StudioQuack builtin (`nodes/odoo/`). Handler: `OfficialApiNode` via `api_spec.json`.
Override template id with `SQ_TEMPLATE_ID_ODOO`.

## Overview

Odoo **JSON-2 External API** (Odoo 19+): `POST /json/2/{model}/{method}` with `Authorization: Bearer {api_key}`. Optional `X-Odoo-Database` from config `db` (required when the host serves multiple databases).

**Migration (2026-08-22):** JSON-RPC `/jsonrpc` is deprecated and scheduled for removal in Odoo 20 (fall 2026). This node no longer calls `/jsonrpc`. Named body arguments only — there is no positional `params` envelope.

Official docs: https://www.odoo.com/documentation/19.0/developer/reference/external_api.html

Host is config `store_host` (hostname only). Default model `res.partner` (overridable on INPUT). Prefer API keys over passwords; Odoo caps key lifetime at three months — rotate.

Do not place secrets on INPUT. Never log tokens or account numbers.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

`operation` (required): `search_read` | `create` | `write` | `unlink` | `call_kw`

Auth: `{"type": "bearer", "configKey": "api_key"}` plus extraHeader `X-Odoo-Database: {db}`.

| Name | Type | Purpose |
|---|---|---|
| `store_host` | string | Hostname only (no scheme) |
| `model` | string | Default ORM model, e.g. `res.partner` |
| `db` | string | `X-Odoo-Database` when set |
| `api_key` | password | Bearer API key |
| `timeout_seconds` | number | Default 25 |

## Operations

| operation | HTTP | Path | Body (named JSON-2 args) |
|---|---|---|---|
| `search_read` | POST | `/json/2/{model}/search_read` | domain, fields, limit, offset, order, context |
| `create` | POST | `/json/2/{model}/create` | vals_list, context |
| `write` | POST | `/json/2/{model}/write` | ids, vals, context |
| `unlink` | POST | `/json/2/{model}/unlink` | ids, context |
| `call_kw` | POST | `/json/2/{model}/{method}` | ids, context, domain, fields, vals_list, vals |

## Legal

- Terms: https://www.odoo.com/terms
- Official-API-first: no scrape, no unofficial web wrapper.
- Customer holds the merchant/ERP account; StudioQuack does not custody funds.
- Researched: 2026-08-22.
