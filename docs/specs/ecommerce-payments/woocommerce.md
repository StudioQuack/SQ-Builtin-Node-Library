# WooCommerce

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/woocommerce/`
- **Researched:** 2026-08-20
- **Official API:** WooCommerce REST API namespace `wc/v3` (current recommended). Store API is a separate customer-facing surface and is **not** this node.
- **Docs:** https://developer.woocommerce.com/docs/apis/rest-api/v3/ — authentication: https://developer.woocommerce.com/docs/apis/rest-api/authentication/ — customers: https://developer.woocommerce.com/docs/apis/rest-api/v3/customers — orders: https://developer.woocommerce.com/docs/apis/rest-api/v3/orders — products: https://developer.woocommerce.com/docs/apis/rest-api/v3/products — webhooks: https://developer.woocommerce.com/docs/apis/rest-api/v3/webhooks — merchant key UI: https://woocommerce.com/document/woocommerce-rest-api/
- **Auth:** HTTPS HTTP Basic (consumer key as username, consumer secret as password) — secret binding: `consumer_key` + `consumer_secret`

## Scope

One node for a merchant-owned WooCommerce store. The customer supplies the store HTTPS URL and REST API keys generated in WooCommerce → Settings → Advanced → REST API (tied to a WordPress user). All calls use `/wp-json/wc/v3/…`. Storefront webhooks are the `trigger` operation on this same node (`POST /wp-json/wc/v3/webhooks`).

HTTPS is required. HTTP Basic over plaintext HTTP is forbidden; WooCommerce documents OAuth 1.0a only as an HTTP fallback — this node rejects non-HTTPS `url` instead of implementing one-legged OAuth.

Card capture is **not** this node — payment gateways on the store remain the merchant’s PSP. Cross-ref Finance nodes in `docs/catalog/finance-accounting.md`. Marketing lists belong on the Klaviyo node (`docs/catalog/crm-marketing.md`) when used.

The WP REST Store API (`/wc/store/v1`) is omitted (shopper cart/checkout, not Admin CRUD). Legacy REST (`wc-api/v3`) is removed from WooCommerce core and is omitted.

## Operations (`operation` config values)

Base path: `{url}/wp-json/wc/v3`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `customer_create` | POST | `/customers` | Create a customer (`email` mandatory) |
| `customer_get` | GET | `/customers/{id}` | Load one customer |
| `customer_list` | GET | `/customers` | Paginated customers |
| `customer_update` | PUT | `/customers/{id}` | Update name, email, addresses |
| `customer_delete` | DELETE | `/customers/{id}?force=true` | Permanently delete (WooCommerce requires `force=true`; there is no trash) |
| `order_create` | POST | `/orders` | Create an order (line items + billing) |
| `order_get` | GET | `/orders/{id}` | Load one order |
| `order_list` | GET | `/orders` | Paginated orders |
| `order_update` | PUT | `/orders/{id}` | Update status, addresses, note |
| `order_delete` | DELETE | `/orders/{id}?force=true` | Permanently delete |
| `product_create` | POST | `/products` | Create a product |
| `product_get` | GET | `/products/{id}` | Load one product |
| `product_list` | GET | `/products` | Paginated products |
| `product_update` | PUT | `/products/{id}` | Update catalog fields |
| `product_delete` | DELETE | `/products/{id}?force=true` | Permanently delete |
| `trigger` | POST/GET/DELETE | `/webhooks` / `/webhooks/{id}` | Subscribe, list, or delete webhooks. Delivery URL is the environment message-event ingress |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_get` | no | Chooses the row above |
| `url` | string | yes | | no | Store origin only (`https://shop.example.com`). No path, no query, no credentials. Must be `https://`. SSRF-guarded by the daemon |
| `consumer_key` | password | yes | | yes | REST consumer key (`ck_…`). HTTP Basic username |
| `consumer_secret` | password | yes | | yes | REST consumer secret (`cs_…`). HTTP Basic password |
| `verify_ssl` | boolean | no | `true` | no | Leave true. Off only for local trusted stores with a broken chain — never default off |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

If a host strips `Authorization`, WooCommerce allows `consumer_key` / `consumer_secret` query params. Do **not** implement that fallback (keys would land in access logs). Fail with `{ error }` asking the merchant to fix HTTPS Basic Auth.

File payloads: product `images[].src` is a URL string, not platform `file`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys. Order/customer PII may appear on success |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = customer_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Customer email (official `MANDATORY`) |
| `first_name` | string | no | First name |
| `last_name` | string | no | Last name |
| `username` | string | no | WP login; auto-generated if omitted |
| `password` | string | no | Write-only account password — never echo on output |
| `billing` | object | no | `first_name`, `last_name`, `company`, `address_1`, `address_2`, `city`, `state`, `postcode`, `country`, `email`, `phone` |
| `shipping` | object | no | Same address fields without email/phone |

### `operation = customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id |

### `operation = customer_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default `1` |
| `per_page` | number | no | Default `10`, max `100` |
| `search` | string | no | Search string |
| `email` | string | no | Exact email filter |
| `role` | string | no | WP role filter |
| `orderby` | string | no | `id` / `include` / `name` / `registered_date` |
| `order` | string | no | `asc` / `desc` |

### `operation = customer_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id |
| `email` | string | no | New email |
| `first_name` | string | no | First name |
| `last_name` | string | no | Last name |
| `billing` | object | no | Billing address |
| `shipping` | object | no | Shipping address |

### `operation = customer_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id |
| `reassign` | number | no | WP user id to reassign posts |

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `status` | string | no | `pending` / `processing` / `on-hold` / `completed` / `cancelled` / `refunded` / `failed` (default `pending`) |
| `currency` | string | no | ISO currency |
| `customer_id` | number | no | `0` = guest |
| `customer_note` | string | no | Checkout note |
| `billing` | object | no | Billing address + email/phone |
| `shipping` | object | no | Shipping address |
| `line_items` | array | yes | `{ product_id, quantity, variation_id? }` |
| `shipping_lines` | array | no | `{ method_id, method_title, total }` |
| `coupon_lines` | array | no | `{ code }` |
| `set_paid` | boolean | no | If true, status becomes processing and stock decreases |
| `payment_method` | string | no | Gateway id (not PAN) |
| `payment_method_title` | string | no | Gateway title |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order id |

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default `1` |
| `per_page` | number | no | Default `10`, max `100` |
| `search` | string | no | Search |
| `after` / `before` | string | no | ISO 8601 created window |
| `status` | string | no | Status filter (`any` for all) |
| `customer` | number | no | Customer id |
| `product` | number | no | Product id |
| `orderby` | string | no | `date` / `id` / `include` / `title` / `slug` |
| `order` | string | no | `asc` / `desc` |

### `operation = order_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order id |
| `status` | string | no | New status |
| `customer_note` | string | no | Note |
| `billing` | object | no | Billing |
| `shipping` | object | no | Shipping |
| `line_items` | array | no | Line updates (include `id` to change existing) |

### `operation = order_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order id |

### `operation = product_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Product name |
| `type` | string | no | `simple` / `grouped` / `external` / `variable` (default `simple`) |
| `status` | string | no | `draft` / `pending` / `private` / `publish` |
| `sku` | string | no | SKU |
| `regular_price` | string | no | Price as string |
| `sale_price` | string | no | Sale price |
| `description` | string | no | HTML description |
| `short_description` | string | no | Short HTML |
| `manage_stock` | boolean | no | Stock management |
| `stock_quantity` | number | no | Qty when managing stock |
| `stock_status` | string | no | `instock` / `outofstock` / `onbackorder` |
| `categories` | array | no | `{ id }` |
| `images` | array | no | `{ src }` HTTPS URLs |

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Product id |

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default `1` |
| `per_page` | number | no | Default `10`, max `100` |
| `search` | string | no | Search |
| `sku` | string | no | SKU |
| `status` | string | no | Product status |
| `type` | string | no | Product type |
| `category` | string | no | Category id |
| `stock_status` | string | no | Stock filter |

### `operation = product_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Product id |
| Same optional body as `product_create` | | | Partial update |

### `operation = product_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Product id |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | yes | `subscribe` / `unsubscribe` / `list` |
| `name` | string | when subscribe | Friendly name |
| `topic` | string | when subscribe | Core: `order.created`, `order.updated`, `order.deleted`, `product.created`, `product.updated`, `product.deleted`, `customer.created`, `customer.updated`, `customer.deleted` |
| `delivery_url` | string | when subscribe | HTTPS message-event ingress |
| `secret` | string | no | HMAC secret (write-only). Prefer generating and storing as a StudioQuack secret — never log |
| `status` | string | no | `active` / `paused` / `disabled` (default `active`) |
| `id` | number | when unsubscribe | Webhook id |
| `page` / `per_page` | number | when list | Pagination |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `customer` | object | customer write/get | `id`, `email`, `first_name`, `last_name`, `username` (no password), `billing`, `shipping`, `date_created_gmt` |
| `customers` | array | `customer_list` | Page of customer summaries |
| `order` | object | order write/get | `id`, `number`, `status`, `currency`, `total`, `customer_id`, `billing`, `shipping`, `line_items`, `date_created_gmt` |
| `orders` | array | `order_list` | Page of order summaries |
| `product` | object | product write/get | `id`, `name`, `sku`, `type`, `status`, `regular_price`, `stock_status`, `stock_quantity`, `permalink` |
| `products` | array | `product_list` | Page of product summaries |
| `webhook` | object | `trigger` subscribe | `id`, `name`, `topic`, `status`, `delivery_url` (never `secret`) |
| `webhooks` | array | `trigger` list | Subscription summaries |
| `deleted` | object | delete ops | `{ id }` of removed resource |
| `total` / `total_pages` | number | list ops | From `X-WP-Total` / `X-WP-TotalPages` headers |
| `error` | string | failure | Mapped error. Never keys or passwords |

## Legal

- WooCommerce.com terms: https://woocommerce.com/terms-and-conditions/ — WordPress.org community support for core: https://woocommerce.com/document/woocommerce-rest-api/
- Rate limits: WooCommerce does **not** publish a global REST quota. Limits are those of WordPress / the host (often nginx 429). Honor `Retry-After` when present; otherwise back off 1s then 4s on 429. Batch webhook create is capped at 100 objects by the official docs.
- PII: customers and orders include emails, phones, and addresses. Keys inherit the WordPress user’s capabilities — scope the WP user. Never log `consumer_secret`, customer passwords, or order PII.
- PCI: `payment_method` is a gateway id, not a card. Do not send PAN. Cross-ref `docs/catalog/finance-accounting.md`.
- Official-API-first: `wc/v3` only. No HTML scrape of `/wp-admin`. No Store API checkout impersonation.

## Implementation notes

- JDK HTTP only. HTTP Basic: `Authorization: Basic base64(consumer_key:consumer_secret)`. Header `Content-Type: application/json` on writes. Header `User-Agent: StudioQuack-WooCommerce`.
- Reject `url` that is not `https://`, that contains userinfo, or that is not allowed by daemon SSRF/egress policy.
- Timeouts 25s default. Response cap 8 MiB. Pagination via `page` + `per_page` (max 100).
- HTTP 4xx/5xx → `{ error }` on OUTPUT (`401` missing/invalid key, `403` capability, `404`, `409`, `429`, `5xx`). Never throw past dispatch.
- `trigger`: WooCommerce signs the body with HMAC-SHA256 (`X-WC-Webhook-Signature`, base64). Verify with the webhook `secret`. After 5 consecutive non-2xx deliveries WooCommerce disables the webhook. Ingress must 200 quickly.
- Never log consumer keys/secrets, Basic auth headers, `password`, emails, or addresses. Log HTTP method, path (no query secrets), status, and resource ids.
