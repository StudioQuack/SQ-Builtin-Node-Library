# WooCommerce

Official WooCommerce node. Handler: `OfficialApiNode` (spec-driven). Customer owns the merchant account.

- **Status:** done
- **Folder:** `nodes/woocommerce/`
- **Official docs:** https://developer.woocommerce.com/docs/apis/rest-api/v3/
- **Auth:** `basic` via `consumer_secret` (StudioQuack secret). Never log tokens, storefront passwords, or order PII.
- **Card capture:** not this node. Use Finance catalog PSP nodes (Stripe, PayPal, Square) when a processor is the source of truth.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `customer_create` | POST | `{store_host}/wp-json/wc/v3/customers` | Required: email |
| `customer_get` | GET | `{store_host}/wp-json/wc/v3/customers/{id}` | Required: id |
| `customer_list` | GET | `{store_host}/wp-json/wc/v3/customers` | Required: — |
| `customer_update` | PUT | `{store_host}/wp-json/wc/v3/customers/{id}` | Required: id |
| `customer_delete` | DELETE | `{store_host}/wp-json/wc/v3/customers/{id}` | Required: id |
| `order_create` | POST | `{store_host}/wp-json/wc/v3/orders` | Required: — |
| `order_get` | GET | `{store_host}/wp-json/wc/v3/orders/{id}` | Required: id |
| `order_list` | GET | `{store_host}/wp-json/wc/v3/orders` | Required: — |
| `order_update` | PUT | `{store_host}/wp-json/wc/v3/orders/{id}` | Required: id |
| `order_delete` | DELETE | `{store_host}/wp-json/wc/v3/orders/{id}` | Required: id |
| `product_create` | POST | `{store_host}/wp-json/wc/v3/products` | Required: — |
| `product_get` | GET | `{store_host}/wp-json/wc/v3/products/{id}` | Required: id |
| `product_list` | GET | `{store_host}/wp-json/wc/v3/products` | Required: — |
| `product_update` | PUT | `{store_host}/wp-json/wc/v3/products/{id}` | Required: id |
| `product_delete` | DELETE | `{store_host}/wp-json/wc/v3/products/{id}` | Required: id |
| `trigger` | ingress | BPMN message event | Storefront/provider webhooks bind here; no socket. |

`trigger` is `kind: ingress`. Do not add a sibling Webhook node. Correlate inbound deliveries onto a BPMN message start / catch / boundary event.

## Configuration

| Name | Type | Purpose |
|---|---|---|
| `operation` | select | Verb above |
| `consumer_secret` | password | Secret binding |
| `store_host` | string | Store hostname only (shop.example.com), no scheme. |
| `timeout_seconds` | number | Per-request timeout |

Self-hosted hosts (`store_host`) are hostname only (no scheme). `OfficialApiNode` concatenates `baseUrl` + path.

## Legal

Merchant ToS of WooCommerce apply. Official API only — no HTML scrape. Minimize buyer/reviewer PII in logs (ids and status codes only).

## Write fields

Customers take `email`, `first_name`, `last_name`, `username`, `password`, `billing`, `shipping`. Orders take `status`, `customer_id`, `payment_method`, `set_paid`, `billing`, `shipping`, `line_items`, `shipping_lines`. Products take `name`, `type`, `regular_price`, `sku`, `stock_*`, `categories`, `images`. Docs: https://developer.woocommerce.com/docs/apis/rest-api/v3/orders
