# BigCommerce

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/bigcommerce/`
- **Researched:** 2026-08-20
- **Official API:** BigCommerce REST Management API — **Catalog v3** for products, **Customers v3**, **Orders v2** (orders have no v3 Management replacement as of this date), **Webhooks v3**.
- **Docs:** Catalog products: https://docs.bigcommerce.com/docs/rest-catalog/products — Customers: https://developer.bigcommerce.com/docs/rest-management/customers — Orders: https://developer.bigcommerce.com/docs/rest-management/orders — Webhooks: https://docs.bigcommerce.com/developer/docs/integrations/webhooks/overview — Rate limits: https://developer.bigcommerce.com/docs/start/best-practices/api-rate-limits — Auth: https://developer.bigcommerce.com/
- **Auth:** Store API account — secret binding: `access_token` (`X-Auth-Token`). `X-Auth-Client` is not required (deprecated 2020-10-09).

## Scope

One node for a merchant-owned BigCommerce store. Host is always `https://api.bigcommerce.com` (not the storefront). Path includes `{store_hash}`. The customer owns the store and the API account.

Storefront webhooks are the `trigger` operation on this same node (`POST /v3/hooks`). Callback payloads are **id-only**; the handler (or a following step) must `order_get` / `product_get` for full records.

Card capture is **not** this node. Cross-ref Finance PSP nodes in `docs/catalog/finance-accounting.md`.

## Operations (`operation` config values)

Base: `https://api.bigcommerce.com/stores/{store_hash}`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `product_list` | GET | `/v3/catalog/products` | Paginated catalog (`limit` ≤ 250) |
| `product_get` | GET | `/v3/catalog/products/{product_id}` | One product |
| `product_create` | POST | `/v3/catalog/products` | Create a product |
| `order_list` | GET | `/v2/orders` | Paginated orders (**v2**) |
| `order_get` | GET | `/v2/orders/{id}` | One order (**v2**) |
| `order_update` | PUT | `/v2/orders/{id}` | Update status, staff notes, billing/shipping |
| `customer_get` | GET | `/v3/customers?id:in={id}` | Customers v3 is filter-based; there is no `/customers/{id}` singleton |
| `trigger` | POST/GET/PUT/DELETE | `/v3/hooks` / `/v3/hooks/{id}` | Subscribe, list, update, delete webhooks |

Omitted: Catalog GraphQL Storefront API (shopper), B2B Edition host `api-b2b.bigcommerce.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_get` | no | Chooses the row above |
| `store_hash` | string | yes | | no | Permanent store id from the API path (`stores/{store_hash}`). Alphanumeric; reject slashes |
| `access_token` | password | yes | | yes | API account token. Header `X-Auth-Token` |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default `1` |
| `limit` | number | no | Default `50`, max `250` |
| `sku` | string | no | Filter |
| `name` | string | no | Filter |
| `is_visible` | boolean | no | Visibility |
| `keyword` | string | no | Keyword search |
| `include` | string | no | Comma list e.g. `variants,images,custom_fields` |

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | number | yes | Catalog product id |
| `include` | string | no | Sub-resources |

### `operation = product_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Product name |
| `type` | string | yes | `physical` / `digital` |
| `weight` | number | when physical | Weight |
| `price` | number | yes | Price |
| `sku` | string | no | SKU |
| `description` | string | no | HTML description |
| `is_visible` | boolean | no | Storefront visibility |
| `inventory_level` | number | no | Stock |
| `inventory_tracking` | string | no | `none` / `product` / `variant` |
| `categories` | array | no | Category ids |
| `brand_id` | number | no | Brand |

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default `1` |
| `limit` | number | no | Default `50`, max `250` |
| `min_id` / `max_id` | number | no | Id window |
| `min_date_created` / `max_date_created` | string | no | RFC 2822 or ISO date |
| `status_id` | number | no | Status id |
| `customer_id` | number | no | Customer |
| `email` | string | no | Customer email filter |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order id |

### `operation = order_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Order id |
| `status_id` | number | no | New status |
| `staff_notes` | string | no | Internal notes |
| `customer_message` | string | no | Message |
| `billing_address` | object | no | Address fields |
| `shipping_addresses` | array | no | Address updates (v2 nested resource — prefer status updates here) |

### `operation = customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Customer id (sent as `id:in`) |
| `include` | string | no | e.g. `addresses,formfields` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | yes | `subscribe` / `unsubscribe` / `list` / `update` |
| `scope` | string | when subscribe | Core: `store/order/created`, `store/order/updated`, `store/order/statusUpdated`, `store/product/created`, `store/product/updated`, `store/product/deleted`, `store/customer/created`, `store/customer/updated`, `store/customer/deleted` |
| `destination` | string | when subscribe | HTTPS URL on **port 443 only** (custom ports unsupported) |
| `is_active` | boolean | no | Default `true` |
| `headers` | object | no | Extra callback headers (do not put store tokens here; use a dedicated ingress secret) |
| `id` | number | when unsubscribe/update | Hook id |
| `page` / `limit` | number | when list | Pagination |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `product` | object | product get/create | `id`, `name`, `sku`, `price`, `inventory_level`, `is_visible` |
| `products` | array | `product_list` | `data` array from v3 envelope |
| `meta` | object | v3 lists | Pagination `meta.pagination` |
| `order` | object | order get/update | v2 order: `id`, `status_id`, `status`, `total_inc_tax`, `customer_id`, `billing_address`, `date_created` |
| `orders` | array | `order_list` | v2 array (no `data` wrapper) |
| `customer` | object | `customer_get` | First match: `id`, `email`, `first_name`, `last_name` (PII; do not log) |
| `webhook` | object | `trigger` subscribe/update | `id`, `scope`, `destination`, `is_active` |
| `webhooks` | array | `trigger` list | Hook summaries |
| `rate_limit_requests_left` | string | when header present | `X-Rate-Limit-Requests-Left` |
| `error` | string | failure | Mapped error. Never tokens |

## Legal

- BigCommerce Incorporated Terms / API terms: linked from https://developer.bigcommerce.com/docs/start/best-practices/api-rate-limits (“BigCommerce: Incorporated terms - API terms”). Merchant ToS: https://www.bigcommerce.com/terms/
- Rate limits (published): OAuth quota refreshed every **30 seconds**. **Plus & Standard: 150 / 30s** (20k/hour). **Pro: 450 / 30s** (60k/hour). Enterprise by plan/resource. All apps on a store **share** the store quota. Headers: `X-Rate-Limit-Time-Window-Ms`, `X-Rate-Limit-Time-Reset-Ms`, `X-Rate-Limit-Requests-Quota`, `X-Rate-Limit-Requests-Left`. HTTP **429** — wait `X-Rate-Limit-Time-Reset-Ms` (or `Retry-After`). A 429 **without** those headers means platform overload; retry later.
- Webhooks: max 10 hooks per `store_id`+`client_id`+`scope`. Destination port 443 only. Inactive after 90 days. Deleted on app uninstall / API account delete. Retry window ~48 hours then `is_active=false`.
- PII: orders and customers include emails and addresses. Callback payloads are light (ids) — still treat follow-up GETs as PII.
- PCI: no PAN. Cross-ref `docs/catalog/finance-accounting.md`.
- Official-API-first: `api.bigcommerce.com` only. No control-panel scrape.

## Implementation notes

- JDK HTTP only. Headers: `X-Auth-Token`, optional `X-Auth-Client`, `Accept: application/json`, `Content-Type: application/json`.
- Timeout 25s. Body cap 8 MiB. Product list `limit` cap 250.
- v3 responses wrap `{ data, meta }`; v2 orders return a raw array/object. Normalize onto OUTPUT without leaking headers that contain tokens.
- HTTP 4xx/5xx → `{ error }` (`401`, `403`, `404`, `422`, `429`, `5xx`). Never throw past dispatch. Retry 429 once after `X-Rate-Limit-Time-Reset-Ms` (usleep that many ms; cap wait at 30s).
- `trigger`: destination = env message-event ingress. Respond 200 immediately. Deduplicate using payload `hash`. Then `order_get` / `product_get` as needed.
- Never log `access_token`, client secrets, emails, or addresses. Log method, path, status, resource ids, remaining quota.
