# Okendo

Official Okendo Merchant Enterprise REST node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_OKENDO`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Merchant REST `api.okendo.io/enterprise`. Researched 2026-08-22.
- **Origin:** `https://api.okendo.io`
- **Docs:** https://docs.okendo.io/merchant-rest-api
- **Auth:** Basic username + `api_key`. extraHeader `okendo-api-version: {api_version}` (default `2025-02-01`, documented required date). Never on INPUT. Never logged.

## Operations

See `api_spec.json`. `review_create` is unverified on this surface.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` or `{error}`. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Official verb. |
| `api_key` | password | **yes** | Basic password / key. |
| `api_version` | string | no | `okendo-api-version` header. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

Okendo merchant ToS. Official API only. Minimize reviewer PII in logs.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
