# Wix Stores

Official Wix Stores node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/wix/`
- **Official docs:** https://dev.wix.com/docs/api-reference/business-solutions/e-commerce/orders/orders/search-orders
- **Auth:** `bearer` via `access_token` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Catalog:** product_list/product_get stay on Stores Catalog v1 (`/stores-reader/v1/products/...`). Catalog v3 search is `POST /stores/v3/products/search` (not switched).
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `order_list` | POST | `/ecom/v1/orders/search` | Required: — |
| `order_get` | GET | `/ecom/v1/orders/{id}` | Required: id |
| `product_list` | POST | `/stores-reader/v1/products/query` | Required: — |
| `product_get` | GET | `/stores-reader/v1/products/{id}` | Required: id |
| `inventory_update` | PATCH | `/stores/v3/inventory-items/{inventory_item_id}` | Required: inventory_item_id, revision |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `access_token` | password | Secret binding |
| `site_id` | string | Wix site id. Sent as `wix-site-id` on every site-level REST call. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of Wix Stores apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).

## Search / inventory bodies

`order_list` takes `filter`, `sort`, `limit`, `cursor` (nested under `search`) per Wix SearchOrders. `product_list` takes `filter`, `sort`, `paging` under `query`. `inventory_update` sends `inventoryItem.id` (from `inventory_item_id`), the required current `revision` and the new `quantity` (plus optional `trackQuantity`) under `inventoryItem`, per Inventory Items v3 Update.
