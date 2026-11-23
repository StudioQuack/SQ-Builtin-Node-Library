# ReCharge

Official ReCharge Subscriptions node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_RECHARGE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** ReCharge Subscriptions API. Dated versions `2021-11` (recommended) and `2021-01`. Researched 2026-08-22.
- **Origin:** `https://api.rechargeapps.com`
- **Docs:** https://developer.rechargepayments.com/2021-11/authentication · https://developer.rechargepayments.com/2021-11/versioning
- **Auth:** `X-Recharge-Access-Token`. extraHeader `X-Recharge-Version: {api_version}` from existing config. PCI stays with ReCharge/Shopify. Never on INPUT. Never logged.

## Operations

See `api_spec.json` for customers, subscriptions, charges, orders.

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
| `api_key` | password | **yes** | `X-Recharge-Access-Token`. |
| `api_version` | string | no | Sent as `X-Recharge-Version`. Default `2021-11`. |
| `timeout_seconds` | number | no | Default `25`. |

## Legal

ReCharge / Shopify merchant ToS. Official API only. PCI stays with the processor.

## Implementation notes

- JDK HTTP via `OfficialApiNode`.
- HTTP 4xx/5xx → `{error}` on OUTPUT. Never log tokens.
