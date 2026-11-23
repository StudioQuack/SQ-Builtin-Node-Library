# Walmart

Official Walmart Marketplace node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_WALMART`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Walmart Marketplace REST. Researched 2026-08-22.
- **Origin:** Marketplace host in `api_spec.json`
- **Docs:** https://developer.walmart.com/us-marketplace/docs/lag-time-api-overview · https://developer.walmart.com/global-marketplace/docs/global-apis-faq
- **Auth:** Marketplace token headers (see spec). extraHeaders always send:
  - `WM_QOS.CORRELATION_ID` from `{correlation_id}` (new GUID per call; omitted if blank — Walmart may 400)
  - `WM_SVC.NAME` from `{svc_name}` (default `Walmart Marketplace`)
  - `WM_CONSUMER.CHANNEL.TYPE` from `{channel_type}` when set

Never on INPUT as secrets. Never logged.

## Operations

See `api_spec.json` for orders/items/inventory.

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields including optional `correlation_id`. No secrets. |
| `output` | OUTPUT | object | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | order_list | no | HTTP verb for OfficialApiNode. EXACT: order_list / order_ack / item_get / inventory_update / trigger. trigger does not open a socket. |
| `access_token` | password | yes |  | yes | WM_SEC.ACCESS_TOKEN. OAuth access token minted outside StudioQuack (Client ID/Secret → POST /v3/token). Never INPUT, never logged. |
| `channel_type` | string | no | 0 | no | WM_CONSUMER.CHANNEL.TYPE as documented for the seller. Sent as extraHeader when set; omitted when blank. |
| `correlation_id` | string | no |  | no | WM_QOS.CORRELATION_ID. Official requires a unique GUID per request. Set on INPUT for each call (or here as a fallback). Header omitted if blank. |
| `svc_name` | string | no | Walmart Marketplace | no | WM_SVC.NAME. Official required header. Default Walmart Marketplace. |
| `createdStartDate` | string | no |  | no | Query-string parameter `createdStartDate` for operation: order_list. |
| `createdEndDate` | string | no |  | no | Query-string parameter `createdEndDate` for operation: order_list. |
| `limit` | number | no |  | no | Query-string parameter `limit` for operation: order_list. |
| `status` | string | no |  | no | Query-string parameter `status` for operation: order_list. |
| `nextCursor` | string | no |  | no | order_list pagination cursor from the previous response. |
| `purchaseOrderId` | string | no |  | no | URL path parameter `purchaseOrderId` for operation: order_ack. Required by: order_ack. |
| `sku` | string | no |  | no | Seller SKU. Path/query for item_get and inventory_update (required query). Also sent in the inventory_update body. Encode special characters. Example: SKU_0001. |
| `shipNode` | string | no |  | no | Fulfillment ship node for inventory_update (query). Omit to use the default node. |
| `amount` | number | no |  | no | Inventory count for inventory_update. Nested as quantity.amount. Example: 10. https://developer.walmart.com/us-marketplace/docs/update-single-item-inventory |
| `inventoryAvailableDate` | string | no |  | no | US inventory available date YYYY-MM-DD for inventory_update. Example: 2026-01-15. |
| `unit` | string | no |  | no | Inventory unit for inventory_update. Official value: EACH. Nested as quantity.unit. Default EACH. |
| `timeout_seconds` | number | no | 25 | no | Per-request HTTP timeout seconds (connect + read). <=0 uses api_spec defaultTimeout. |

## Legal

Walmart Marketplace API ToS. Official API only.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. extraHeaders omit unresolvable placeholders.
- HTTP 4xx/5xx raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED).

## Inventory body

`inventory_update` PUT /v3/inventory?sku= sends `sku` plus `quantity.unit` / `quantity.amount` (and optional `inventoryAvailableDate`, `shipNode`) per https://developer.walmart.com/us-marketplace/docs/update-single-item-inventory.
