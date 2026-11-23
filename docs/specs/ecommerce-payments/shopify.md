# Shopify

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/shopify/`
- **Researched:** 2026-08-20
- **Official API:** Shopify Admin GraphQL API `2026-07` (current stable). REST Admin is a legacy API (deprecated 2024-10-01; new public apps must use GraphQL as of 2025-04-01). Do not call REST Admin.
- **Docs:** https://shopify.dev/docs/api/admin-graphql/2026-07 — webhooks: https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/webhookSubscriptionCreate — GDPR: https://shopify.dev/docs/apps/build/compliance/privacy-law-compliance — limits: https://shopify.dev/docs/api/usage/limits
- **Auth:** `access_token` (custom Admin token) or OAuth 2 access token — secret binding: `access_token` (and `oauth_client_secret` / `oauth_refresh_token` when `auth_mode=oauth`)

## Scope

One node for the merchant’s Shopify Admin. The customer owns the shop and the Admin app/token. Every operation is a JDK `POST` to `https://{shop}.myshopify.com/admin/api/{api_version}/graphql.json` with header `X-Shopify-Access-Token`. Storefront webhooks are the `trigger` operation on this same node (subscribe via `webhookSubscriptionCreate`); there is no sibling trigger node.

Card capture is **not** this node — use the Finance PSP nodes (Stripe / PayPal / Square) in `docs/catalog/finance-accounting.md`. Marketing profiles belong on the Klaviyo node (`docs/catalog/crm-marketing.md`), not here.

Public App Store apps **must** also subscribe to Shopify’s mandatory compliance topics (`customers/data_request`, `customers/redact`, `shop/redact`). Custom Admin apps used only by the merchant who created the token are not App Store listings and do not use those three topics as `trigger` events.

## Operations (`operation` config values)

All rows use HTTP `POST` to `/admin/api/{api_version}/graphql.json`. The RPC column is the GraphQL field.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_create` | POST | `orderCreate` | Create a draft/manual order (`write_orders`) |
| `order_delete` | POST | `orderDelete` | Permanently delete an eligible order (`write_orders` + delete permission). Other orders must be cancelled, not deleted |
| `order_get` | POST | `order` | Load one order by GID |
| `order_list` | POST | `orders` | Paginated order search (`first` + optional `query` + `after`) |
| `order_update` | POST | `orderUpdate` | Update email, shipping address, note, tags, metafields. Line-item edits use Shopify’s order-edit session — out of this node’s starting verbs |
| `product_create` | POST | `productCreate` | Create a product (`write_products`) |
| `product_delete` | POST | `productDelete` | Permanently delete a product (irreversible) |
| `product_get` | POST | `product` | Load one product by GID |
| `product_list` | POST | `products` | Paginated product search |
| `product_update` | POST | `productUpdate` | Update title, status, description, options |
| `customer_get` | POST | `customer` | Load one customer by GID (`read_customers`) |
| `inventory_adjust` | POST | `inventoryAdjustQuantities` | Delta-adjust inventory at a location. `@idempotent(key:)` is **required** on API `2026-04+` |
| `fulfillment_create` | POST | `fulfillmentCreate` | Fulfill items on fulfillment order(s) assigned to the same location |
| `trigger` | POST | `webhookSubscriptionCreate` / `webhookSubscriptionDelete` / `webhookSubscriptions` | Subscribe, list, or delete HTTPS webhooks. Destination is the environment message-event ingress |

Retired / unofficial: REST Admin `/admin/api/{version}/orders.json` (and sibling REST resources) are omitted. Do not add `order_cancel` unless a later catalog pass asks for `orderCancel`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_get` | no | Chooses the row above |
| `auth_mode` | select | yes | `access_token` | no | `access_token` (custom Admin token) or `oauth` |
| `shop` | string | yes | | no | Shop subdomain only (`example` → `example.myshopify.com`). Reject hosts that are not `*.myshopify.com` |
| `api_version` | string | no | `2026-07` | no | Year-month Admin API version. Pin; do not send `unstable` |
| `access_token` | password | when `auth_mode=access_token` | | yes | Admin API access token (`shpat_…` or OAuth access token). Header `X-Shopify-Access-Token` |
| `oauth_client_id` | string | when `auth_mode=oauth` | | no | App client id for token refresh |
| `oauth_client_secret` | password | when `auth_mode=oauth` | | yes | App secret |
| `oauth_refresh_token` | password | when `auth_mode=oauth` | | yes | Offline refresh token; exchange at `https://{shop}.myshopify.com/admin/oauth/access_token` |
| `webhook_api_secret` | password | for `trigger` HMAC | | yes | App secret used to verify `X-Shopify-Hmac-Sha256` on inbound deliveries |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

File payloads: none on this node (product media URLs are strings, not platform `file` refs).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens. Order emails/addresses may appear on success when the operation requested them |

FUNCTION ports: none.

## Input fields (by operation)

Numeric Shopify ids are accepted and converted to GIDs (`gid://shopify/Order/{id}`, `Product`, `Customer`, `InventoryItem`, `Location`, `FulfillmentOrder`, `WebhookSubscription`).

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order` | object | yes | `OrderCreateOrderInput`: `lineItems` (variant GID + quantity), optional `email`, `phone`, `shippingAddress`, `billingAddress`, `note`, `tags`, `financialStatus` |
| `options` | object | no | `OrderCreateOptionsInput` (inventory behaviour, send receipt) |

### `operation = order_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Order GID or numeric id. Only certain order types can be deleted |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Order GID or numeric id |

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `first` | number | no | Page size, default `25`, max `250` |
| `after` | string | no | Cursor from previous `page_info.end_cursor` |
| `query` | string | no | Shopify search syntax (`financial_status:paid`, `created_at:>2026-01-01`) |

### `operation = order_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Order GID |
| `email` | string | no | Customer email |
| `note` | string | no | Order note |
| `tags` | array | no | Replacement tag list |
| `shipping_address` | object | no | `MailingAddressInput` (`address1`, `city`, `province`, `zip`, `country`) |
| `metafields` | array | no | `MetafieldInput` list |

### `operation = product_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product` | object | yes | `ProductCreateInput`: `title` required; optional `descriptionHtml`, `status`, `productType`, `vendor`, `tags` |
| `media` | array | no | Remote image URLs (`originalSource` + `mediaContentType`) — not platform `file` |

### `operation = product_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Product GID |
| `synchronous` | boolean | no | Default `true`. Set `false` for large catalogs to avoid timeouts |

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Product GID |

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `first` | number | no | Page size, default `25`, max `250` |
| `after` | string | no | Cursor |
| `query` | string | no | Product search syntax |

### `operation = product_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Product GID |
| `title` | string | no | New title |
| `description_html` | string | no | Description |
| `status` | string | no | `ACTIVE` / `ARCHIVED` / `DRAFT` |
| `tags` | array | no | Tags |

### `operation = customer_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customer_id` | string | yes | Customer GID |

### `operation = inventory_adjust`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `idempotency_key` | string | yes | UUID for `@idempotent(key:)` (required on `2026-04+`) |
| `reason` | string | yes | Adjustment reason (`correction`, `restock`, …) |
| `name` | string | yes | Quantity name, typically `available` |
| `changes` | array | yes | Items: `delta` (int), `inventory_item_id`, `location_id`. Max 250 |
| `reference_document_uri` | string | no | Audit URI |

### `operation = fulfillment_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fulfillment` | object | yes | `FulfillmentInput`: `lineItemsByFulfillmentOrder` (fulfillment order GID + optional line items), optional `trackingInfo` (`number`, `url`, `company`), `notifyCustomer` |
| `message` | string | no | Optional fulfillment message |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | yes | `subscribe` / `unsubscribe` / `list` |
| `topic` | string | when subscribe | GraphQL `WebhookSubscriptionTopic` (core storefront: `ORDERS_CREATE`, `ORDERS_UPDATED`, `ORDERS_CANCELLED`, `PRODUCTS_CREATE`, `PRODUCTS_UPDATE`, `PRODUCTS_DELETE`, `INVENTORY_LEVELS_UPDATE`, `CUSTOMERS_CREATE`, `CUSTOMERS_UPDATE`, `FULFILLMENTS_CREATE`) |
| `callback_url` | string | when subscribe | HTTPS message-event ingress URL (port 443) |
| `webhook_id` | string | when unsubscribe | Subscription GID |
| `first` | number | when list | Page size, default `25` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `order` | object | `order_create`, `order_get`, `order_update` | `id`, `name`, `displayFinancialStatus`, `displayFulfillmentStatus`, `email`, `totalPriceSet`, `createdAt`, `updatedAt`, `lineItems` (id, title, quantity, sku) |
| `deleted_id` | string | `order_delete`, `product_delete` | Deleted GID |
| `orders` | array | `order_list` | Same shape as `order`, page of nodes |
| `product` | object | `product_create`, `product_get`, `product_update` | `id`, `title`, `status`, `handle`, `createdAt`, `updatedAt`, `variants` (id, sku, title) |
| `products` | array | `product_list` | Page of product summaries |
| `customer` | object | `customer_get` | `id`, `displayName`, `email`, `phone`, `createdAt` — PII; do not log |
| `inventory_adjustment_group` | object | `inventory_adjust` | `createdAt`, `reason`, `changes[]` (`name`, `delta`) |
| `fulfillment` | object | `fulfillment_create` | `id`, `status`, `trackingInfo` |
| `webhook` | object | `trigger` subscribe | `id`, `topic`, `uri` |
| `webhooks` | array | `trigger` list | Subscription summaries |
| `page_info` | object | list ops | `has_next_page`, `end_cursor` |
| `cost` | object | success | GraphQL `extensions.cost.throttleStatus` (`currentlyAvailable`, `restoreRate`) — no secrets |
| `error` | string | failure | Mapped error. Never tokens, never raw order dumps |

## Legal

- Shopify API License and Terms of Use: https://www.shopify.com/legal/api-terms — Partner Program if distributing an app: https://www.shopify.com/partners/terms
- Rate limits (published): GraphQL Admin calculated query cost. Standard **100 points/second** restore; Advanced 200; Plus 1000; Enterprise 2000. Single-query max cost **1000**. Input arrays max **250**. Pagination/count capped at **25,000**. HTTP `200` with `errors[].extensions.code=THROTTLED` is the GraphQL equivalent of 429 — wait using `restoreRate`. Resource throttle: after 500,000 variants, max 10,000 new variants/day (not Plus).
- PII: orders include emails, phones, shipping/billing addresses. Minimize fields requested. Do not log PII. GDPR/CPRA: public App Store apps must implement mandatory compliance webhooks (https://shopify.dev/docs/apps/build/compliance/privacy-law-compliance) and respond with 200; invalid HMAC → 401.
- PCI: this node never collects PAN/CVV. Payments stay on Shopify Payments / the merchant’s PSP. Cross-ref `docs/catalog/finance-accounting.md` (Stripe, PayPal, Square).
- Official-API-first: Admin GraphQL only. No Admin HTML scrape, no Storefront API impersonation.

## Implementation notes

- JDK HTTP only (`HttpClient`). Do not ship Shopify’s Node/Ruby SDKs.
- `POST` JSON `{ "query", "variables" }`. Header `Content-Type: application/json`. Timeout default 25s (raise toward 60s only for `product_delete` async). Response body cap 8 MiB.
- Map `userErrors` and GraphQL `errors` to `{ error }` on OUTPUT. HTTP 4xx/5xx (402 frozen shop, 403, 404, 423 locked, 5xx) → `{ error }` with status code, never throw past dispatch. `THROTTLED` / HTTP 429: retry once after `Retry-After` or `restoreRate` delay (min 1s).
- `inventory_adjust`: always send `@idempotent(key: $idempotencyKey)`.
- `trigger` destination must be the StudioQuack engine's message-event ingress. Verify HMAC with `webhook_api_secret`. Return 200 quickly; fetch full resources with `order_get` / `product_get` if the payload is id-only.
- Never log `access_token`, OAuth secrets, HMAC, customer email/phone, or full addresses. Log operation name, HTTP status, GraphQL cost, and resource GIDs only.
