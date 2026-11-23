# Magento


customer_update / product_update take the same purpose fields as create, merged into `customer` / `product` via bodyMap. Keep the wrappers as escape hatches. Docs: https://developer.adobe.com/commerce/webapi/rest/quick-reference/

Official Magento node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/magento/`
- **Official docs:** https://developer.adobe.com/commerce/webapi/rest/quick-reference/
- **Auth:** `bearer` via `access_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `customer_create` | POST | `{store_host}/rest/V1/customers` | Required: — |
| `customer_get` | GET | `{store_host}/rest/V1/customers/{id}` | Required: id |
| `customer_list` | GET | `{store_host}/rest/V1/customers/search` | Required: — |
| `customer_update` | PUT | `{store_host}/rest/V1/customers/{id}` | Required: id |
| `customer_delete` | DELETE | `{store_host}/rest/V1/customers/{id}` | Required: id |
| `invoice_create` | POST | `{store_host}/rest/V1/order/{orderId}/invoice` | Required: orderId |
| `order_cancel` | POST | `{store_host}/rest/V1/orders/{id}/cancel` | Required: id |
| `order_get` | GET | `{store_host}/rest/V1/orders/{id}` | Required: id |
| `order_list` | GET | `{store_host}/rest/V1/orders` | Required: — |
| `order_ship` | POST | `{store_host}/rest/V1/order/{orderId}/ship` | Required: orderId |
| `product_create` | POST | `{store_host}/rest/V1/products` | Required: — |
| `product_get` | GET | `{store_host}/rest/V1/products/{sku}` | Required: sku |
| `product_list` | GET | `{store_host}/rest/V1/products` | Required: — |
| `product_update` | PUT | `{store_host}/rest/V1/products/{sku}` | Required: sku |
| `product_delete` | DELETE | `{store_host}/rest/V1/products/{sku}` | Required: sku |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `access_token` | password | Secret binding |
| `store_host` | string | Store hostname only (magento.example.com), no scheme. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Magento apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).
