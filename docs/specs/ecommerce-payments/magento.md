# Magento

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/magento/`
- **Researched:** 2026-08-20
- **Official API:** Magento Open Source / Adobe Commerce (PaaS) REST, resource path **`/V1`**. Same framework on Adobe Commerce cloud, on-premises, and Magento Open Source 2.4.x.
- **Docs:** https://developer.adobe.com/commerce/webapi/rest/ — authentication: https://developer.adobe.com/commerce/webapi/get-started/authentication/gs-authentication-token — tutorials: https://developer.adobe.com/commerce/webapi/rest/tutorials/ — invoice example: https://developer.adobe.com/commerce/webapi/rest/tutorials/orders/order-create-invoice/
- **Auth:** Integration OAuth 1.0a (preferred) or standalone Bearer `access_token` when the merchant enables that Admin setting — secret binding: `access_token` plus OAuth secrets when `auth_mode=oauth`

## Scope

One node for a merchant-owned Magento 2 / Adobe Commerce **PaaS** store. The customer owns the shop URL and the integration. All Admin calls go to `{url}/rest/{store_code}/V1/…` on the merchant’s egress (daemon SSRF/egress policy). Default `store_code` is `default`.

This node targets self-hosted / Adobe Commerce on Cloud **PaaS** (catalog note: “Self-host URL on customer egress”). **Adobe Commerce as a Cloud Service (SaaS)** uses IMS OAuth 2, omits `/rest` from the path, and is **out of scope** for the starting build.

There is **no** first-class Magento Open Source webhook REST comparable to Shopify/WooCommerce. Catalog has no `trigger` verb — do not invent one. Adobe I/O Events (Adobe Commerce only) is a different product and is omitted.

Card capture is **not** this node. Cross-ref Finance PSP nodes in `docs/catalog/finance-accounting.md`.

## Operations (`operation` config values)

Base: `{url}/rest/{store_code}/V1`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `customer_create` | POST | `/customers` | Create a customer (`customer.email` required) |
| `customer_get` | GET | `/customers/{id}` | Load one customer |
| `customer_list` | GET | `/customers/search` | SearchCriteria list |
| `customer_update` | PUT | `/customers/{id}` | Update customer (body wraps `customer`) |
| `customer_delete` | DELETE | `/customers/{id}` | Delete customer |
| `invoice_create` | POST | `/order/{orderId}/invoice` | Capture/invoice a paid order |
| `order_cancel` | POST | `/orders/{id}/cancel` | Cancel an order |
| `order_get` | GET | `/orders/{id}` | Load one order |
| `order_list` | GET | `/orders` | SearchCriteria list |
| `order_ship` | POST | `/order/{orderId}/ship` | Create a shipment (preferred over `POST /shipment`) |
| `product_create` | POST | `/products` | Create a product (`product.sku` required) |
| `product_get` | GET | `/products/{sku}` | Load by SKU (SKU is the path key, not entity id) |
| `product_list` | GET | `/products` | SearchCriteria list |
| `product_update` | PUT | `/products/{sku}` | Update by SKU |
| `product_delete` | DELETE | `/products/{sku}` | Delete by SKU |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_get` | no | Chooses the row above |
| `url` | string | yes | | no | Store origin `https://magento.example.com` (no `/rest`). HTTPS required. Daemon SSRF/egress |
| `store_code` | string | no | `default` | no | Store view in the REST path |
| `auth_mode` | select | yes | `bearer` | no | `bearer` (standalone integration token) or `oauth` (OAuth 1.0a HMAC-SHA256) |
| `access_token` | password | yes | | yes | Integration access token. Bearer header when `auth_mode=bearer` |
| `consumer_key` | string | when oauth | | no | Integration consumer key |
| `consumer_secret` | password | when oauth | | yes | Integration consumer secret |
| `access_token_secret` | password | when oauth | | yes | Integration token secret (OAuth 1.0a signing) |
| `timeout_seconds` | number | no | `30` | no | Per-request timeout (invoices/shipments can be slower) |

Adobe documents that using the integration access token as a **standalone Bearer** is disabled by default (`oauth/consumer/enable_integration_as_bearer`). Merchants who choose `auth_mode=bearer` must have enabled that setting. Prefer `oauth`.

Do **not** collect Admin username/password (Admin token expires in 4h and requires 2FA). Do not use customer tokens (self-scoped).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

SearchCriteria (list ops) uses Magento’s query encoding: `searchCriteria[pageSize]`, `searchCriteria[currentPage]`, `searchCriteria[filterGroups][0][filters][0][field|value|conditionType]`.

### `operation = customer_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Customer email |
| `firstname` | string | yes | First name |
| `lastname` | string | yes | Last name |
| `website_id` | number | no | Website id |
| `store_id` | number | no | Store id |
| `password` | string | no | Account password (write-only; never on output) |
| `addresses` | array | no | Address objects (`street[]`, `city`, `region`, `postcode`, `country_id`, `telephone`) |

Body: `{ "customer": { … }, "password": "…" }`.

### `operation = customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer entity id |

### `operation = customer_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default `20`, max **300** (Commerce default page cap) |
| `current_page` | number | no | Default `1` |
| `filter_field` | string | no | e.g. `email` |
| `filter_value` | string | no | Filter value |
| `filter_condition` | string | no | Default `eq` (`like`, `gt`, …) |

### `operation = customer_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id (must also be inside `customer.id`) |
| Same optional fields as create (no password unless resetting) | | | Body `{ "customer": { "id", … } }` |

### `operation = customer_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id |

### `operation = invoice_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | number | yes | Order entity id |
| `capture` | boolean | no | `true` to capture payment (offline/bank) |
| `notify` | boolean | no | Email the customer |
| `items` | array | no | Partial invoice: `{ order_item_id, qty }`. Omit for full invoice |

### `operation = order_cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order entity id |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order entity id |

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default `20`, max 300 |
| `current_page` | number | no | Default `1` |
| `filter_field` | string | no | e.g. `status`, `increment_id`, `customer_email` |
| `filter_value` | string | no | Filter value |
| `filter_condition` | string | no | Default `eq` |

### `operation = order_ship`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | number | yes | Order entity id |
| `items` | array | no | `{ order_item_id, qty }` — omit to ship all remaining |
| `notify` | boolean | no | Email the customer |
| `tracks` | array | no | `{ track_number, title, carrier_code }` |
| `comment` | object | no | `{ comment, is_visible_on_front }` |
| `source_code` | string | no | MSI source (extension attribute) |

### `operation = product_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sku` | string | yes | Unique SKU (path key later) |
| `name` | string | yes | Name |
| `attribute_set_id` | number | yes | Attribute set |
| `price` | number | no | Price |
| `status` | number | no | `1` enabled / `2` disabled |
| `visibility` | number | no | Catalog visibility |
| `type_id` | string | no | Default `simple` |
| `weight` | number | no | Weight |
| `extension_attributes` | object | no | e.g. `stock_item` `{ qty, is_in_stock }` |

Body: `{ "product": { … } }`.

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sku` | string | yes | URL-encoded SKU |

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default `20`, max 300 |
| `current_page` | number | no | Default `1` |
| `filter_field` | string | no | e.g. `sku`, `name`, `status` |
| `filter_value` | string | no | Filter value |
| `filter_condition` | string | no | Default `eq` |

### `operation = product_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sku` | string | yes | Path SKU |
| Same optional product fields as create | | | Body `{ "product": { … } }` |

### `operation = product_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sku` | string | yes | SKU to delete |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `customer` | object | customer write/get | `id`, `email`, `firstname`, `lastname`, `created_at` (no password) |
| `customers` | array | `customer_list` | `items` from search result |
| `order` | object | `order_get` | `entity_id`, `increment_id`, `status`, `grand_total`, `customer_email`, `items[]` (`item_id`, `sku`, `qty_ordered`) |
| `orders` | array | `order_list` | `items` from search result |
| `invoice_id` | string/number | `invoice_create` | New invoice id (Commerce returns the id) |
| `canceled` | boolean | `order_cancel` | `true` when the API returns success |
| `shipment_id` | string/number | `order_ship` | New shipment id |
| `product` | object | product write/get | `id`, `sku`, `name`, `price`, `status`, `type_id` |
| `products` | array | `product_list` | `items` from search result |
| `total_count` | number | list ops | Search `total_count` |
| `error` | string | failure | Mapped error. Never tokens |

## Legal

- Adobe General Terms of Use: https://www.adobe.com/legal/terms.html — Magento Open Source is under its Open Source license on the merchant’s install. API use must stay within the merchant’s own store.
- Rate limits (published for 2.4.3+): REST list inputs default max **20** entities; paginated results default max **300** per page. Optional backpressure returns HTTP **429**. Exact buckets are store-configured (`devdocs` API security / rate limiting). Honor `Retry-After`.
- PII: customer emails, phones, and addresses; order `customer_email` and shipping/billing. Minimize SearchCriteria fields. Never log tokens or PII.
- PCI: invoicing marks Magento payment capture — still not PAN capture. Cross-ref `docs/catalog/finance-accounting.md`.
- Official-API-first: `/rest/…/V1` only. No Adminhtml scrape.

## Implementation notes

- JDK HTTP only. No Magento SDK.
- `auth_mode=bearer`: `Authorization: Bearer {access_token}`. `auth_mode=oauth`: sign with HMAC-SHA256 OAuth 1.0a using all four integration credentials (Adobe’s documented standalone-OAuth pattern). Implement signing in-process; do not add a vendor library unless JDK crypto is insufficient (state the reason if you do).
- HTTPS only. Timeout 30s default. Body cap 8 MiB. URL-encode SKUs (`/` in SKU is `%2F`).
- HTTP 4xx/5xx → `{ error }` on OUTPUT (`401` token, `403` ACL, `400` validation, `404`, `429`, `5xx`). Commerce error bodies often `{ "message", "parameters" }` — surface `message` only. Never throw past dispatch.
- Retry 429 once after `Retry-After` or 1s. Do not retry 401/403/404.
- Never log tokens, OAuth signatures, passwords, emails, or addresses. Log method, `/V1/…` path without query PII, status, and entity ids / SKUs.
