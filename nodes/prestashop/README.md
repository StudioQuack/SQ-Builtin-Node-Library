# PrestaShop

Official PrestaShop Webservice node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_PRESTASHOP`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** PrestaShop Webservice (JSON output on reads). Researched 2026-08-22.
- **Origin:** `{shop_url}` (config)
- **Docs:** https://devdocs.prestashop-project.org/9/webservice/tutorials/testing-access/
- **Auth:** HTTP Basic with `api_key` as **username** and empty password (`auth.basicKeyPosition: "username"`). Official curl uses `api_key:` as user. Never on INPUT. Never logged.

## Operations

Resource CRUD via `resource` + `{id}`. Writes send raw XML in INPUT `xml` (`text/xml`). Reads use `output_format=JSON` plus `display`, `filter`, `limit`, `sort`, `date`. Shop-wide key — scope it in PrestaShop. Docs: https://devdocs.prestashop-project.org/9/webservice/

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}`. Errors raise a BPMN error. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Official verb. |
| `shop_url` | string | no | Shop origin. |
| `api_key` | password | **yes** | Basic username. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

PrestaShop license / merchant ToS. Official API only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log `api_key`.
