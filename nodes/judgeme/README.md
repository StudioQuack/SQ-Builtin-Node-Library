# Judge.me

Official Judge.me Reviews node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_JUDGEME`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Judge.me Private API. Researched 2026-08-22.
- **Origin:** `https://judge.me/api/v1`
- **Docs:** https://judge.me/api/docs · https://judge.me/help/en/articles/8409180-using-judge-me-api
- **Auth:** Recommended header `X-Api-Token` from config `api_token`. Query `api_token` is **still documented** and remains in `queryFrom`. `shop_domain` required. Never on INPUT. Never logged.

## Operations

See `api_spec.json` for reviews. Never log reviewer email/body.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` . |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Official verb. |
| `api_token` | password | **yes** | Header + query. |
| `shop_domain` | string | no | Shop domain. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

Judge.me merchant ToS. Official API only. Minimize reviewer PII in logs.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_token`.
