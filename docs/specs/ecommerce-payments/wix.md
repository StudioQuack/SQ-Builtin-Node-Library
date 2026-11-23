# Wix Stores

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/wix/`
- **Researched:** 2026-08-20
- **Official API:** Wix REST — **eCommerce Orders v1** (`ecom/v1/orders`) + **Stores Catalog V1 reader** (`stores-reader/v1/products`) + **Stores Catalog V3 Inventory Items** (`stores/v3/inventory-items`). Host `https://www.wixapis.com`.
- **Docs:** Search orders: https://dev.wix.com/docs/api-reference/business-solutions/e-commerce/orders/orders/search-orders — Get order: https://dev.wix.com/docs/api-reference/business-solutions/e-commerce/orders/orders/get-order — Query products: https://dev.wix.com/docs/api-reference/business-solutions/stores/catalog/query-products — Get product: https://dev.wix.com/docs/api-reference/business-solutions/stores/catalog/get-product.md — Inventory update (V3): https://dev.wix.com/docs/api-reference/business-solutions/stores/catalog-v3/inventory-items-v3/update-inventory-item — OAuth: https://dev.wix.com/docs/build-apps/develop-your-app/access/authentication/use-oauth — Webhooks: https://dev.wix.com/docs/build-apps/develop-your-app/api-integrations/events-and-webhooks/about-webhooks
- **Auth:** OAuth 2 — secret binding: `oauth_client_secret` (and `oauth_refresh_token` when using the authorization-code grant)

## Scope

One node for a merchant-owned Wix site with **Wix Stores** installed. The customer owns the site and the Wix app instance.

**App review:** public App Market apps go through Wix app review (demo access, permissions, webhooks). Self-installed / custom apps used by the merchant who created them can call the same REST host without a Market listing. This is **not** a DO NOT BUILD — it is a distribution gate. Official webhook behaviour: https://dev.wix.com/docs/build-apps/develop-your-app/api-integrations/events-and-webhooks/about-webhooks . Terms: https://www.wix.com/about/terms-of-use (revised 2026-07-09).

Storefront webhooks are the `trigger` operation on this same node. Wix delivers signed JWT webhooks to URLs registered on the app (Developers Center). The node’s `trigger` records topic + ingress URL and documents dashboard registration (the supported public mechanism). Do not invent a sibling trigger node.

Catalog version: **never call `/stores/v3/` product endpoints on a Catalog V1 site** (Wix returns `428 Precondition Required`). Starting product ops use the **V1 reader**. Inventory update uses **V3 Inventory Items**; if the site is V1-only, return `{ error }` instructing the merchant (do not guess a V2 path).

Card capture is **not** this node. Cross-ref `docs/catalog/finance-accounting.md`. Marketing profiles: Klaviyo node in `docs/catalog/crm-marketing.md` if used.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_list` | POST | `/ecom/v1/orders/search` | Cursor search (default limit 100, max 100). Does not return `INITIALIZED`; `PENDING`/`REJECTED` need an explicit filter |
| `order_get` | GET | `/ecom/v1/orders/{id}` | One order by GUID |
| `product_list` | POST | `/stores-reader/v1/products/query` | Up to 100 products (Catalog V1 query) |
| `product_get` | GET | `/stores-reader/v1/products/{id}` | One product GUID |
| `inventory_update` | PATCH | `/stores/v3/inventory-items/{inventoryItem.id}` | Set quantity or in-stock flag. Requires current `revision` |
| `trigger` | n/a (app dashboard) | Wix webhook events | Bind HTTPS message-event ingress to order/product/inventory events. See notes |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_list` | no | Chooses the row above |
| `site_id` | string | yes | | no | Wix site id. Sent as `wix-site-id` on every site-level REST call. |
| `oauth_client_id` | string | yes | | no | Wix app id |
| `oauth_client_secret` | password | yes | | yes | App secret. Token URL `POST https://www.wixapis.com/oauth2/token` |
| `grant_type` | select | yes | `refresh_token` | no | `refresh_token` (authorization code apps) or `client_credentials` (app-instance) |
| `oauth_refresh_token` | password | when refresh_token | | yes | Refresh token from merchant install |
| `instance_id` | string | when client_credentials | | no | App instance id from App Instance Installed (not a password, but treat as sensitive) |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

Access tokens last **4 hours** (official OAuth doc). Refresh in-process; never persist the access token on OUTPUT.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none. File payloads: none on starting verbs.

## Input fields (by operation)

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Cursor page size, default `100`, max `100` |
| `cursor` | string | no | `pagingMetadata.cursors.next` |
| `filter` | object | no | Wix query-language filter (e.g. `{ "status": "APPROVED" }`). Default search **omits** `PENDING` and `REJECTED` |
| `sort` | array | no | Up to 4 sorts; default `createdDate` `DESC` |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Order GUID |

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Default/max `100` |
| `offset` | number | no | Offset paging |
| `filter` | string/object | no | V1 query filter |
| `include_variants` | boolean | no | Include variants |
| `include_hidden_products` | boolean | no | Requires manage-products permission |
| `include_merchant_specific_data` | boolean | no | Cost/profit — requires manage-products |

### `operation = product_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Product GUID |
| `include_merchant_specific_data` | boolean | no | Merchant fields |

### `operation = inventory_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `inventory_item_id` | string | yes | Inventory item GUID (path) |
| `revision` | string | yes | Current revision (optimistic concurrency) |
| `quantity` | number | one of | Tracked qty (`-99999`…`99999`). Mutually exclusive with `in_stock` |
| `in_stock` | boolean | one of | Status tracking (unlimited / made-to-order). Mutually exclusive with `quantity` |
| `reason` | string | no | `ORDER` / `MANUAL` / `REVERT_INVENTORY_CHANGE` |
| `preorder_enabled` | boolean | no | Preorder flag (not for digital/subscriptions) |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | yes | `describe` (return the ingress URL + required event names) or `verify_sample` (validate a posted JWT payload shape — no secret echo) |
| `topic` | string | yes | Core: Wix eCom **Order Created** / **Order Updated** / **Order Canceled**; Stores **Product Changed**; Inventory changed (Catalog V3 events when the site is V3) |
| `callback_url` | string | yes | HTTPS message-event ingress to paste in the Wix Developers Center → Webhooks |

Wix webhook HTTP timeout is **1250 ms**; the ingress must 200 immediately. Failures retry up to 12 times on Wix’s published schedule.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `orders` | array | `order_list` | Order summaries (`id`, `number`, `createdDate`, `paymentStatus`, `fulfillmentStatus`) — buyer PII may be present; do not log |
| `order` | object | `order_get` | Full order (line items, addresses) |
| `paging_metadata` | object | `order_list` | Cursors / hasNext |
| `products` | array | `product_list` | Product summaries (`id`, `name`, `slug`, `visible`, `productType`, `sku`) |
| `product` | object | `product_get` | Product + optional variants |
| `inventory_item` | object | `inventory_update` | Updated item (`id`, `revision`, `quantity`/`inStock`, `availabilityStatus`) |
| `webhook_setup` | object | `trigger` describe | `{ callback_url, topic, instructions }` — no secrets |
| `error` | string | failure | Mapped error. Never tokens |

## Legal

- Wix Terms of Use: https://www.wix.com/about/terms-of-use — Privacy: https://www.wix.com/about/privacy
- App Market / review: public apps must satisfy Wix’s review checklist (permissions least-privilege, working OAuth, documented webhooks). Custom merchant-only apps still accept the Wix Terms by using the APIs.
- Rate limits: the previously linked REST “about rate limits” article **404’d on 2026-08-20** (`https://dev.wix.com/docs/rest/articles/getting-started/about-rate-limits`). Treat quotas as **unpublished in docs**; honor HTTP **429** and any `Retry-After`. Do not guess numeric buckets.
- PII: orders include buyer email, phone, and addresses. Minimize filters. Never log PII or OAuth secrets.
- PCI: eCom orders record payment **status**, not PAN. Cross-ref `docs/catalog/finance-accounting.md` (Wix Payments / Stripe / PayPal as configured by the merchant).
- Official-API-first: `www.wixapis.com` REST only. No Editor scrape.

## Implementation notes

- JDK HTTP only. No Wix SDK. `Authorization: {access_token}` (Wix examples omit the `Bearer` prefix — send the token exactly as Create Access Token returns it; if a 401 indicates a scheme mismatch, retry once with `Bearer ` — document the working variant in the node README when built).
- Token POST `https://www.wixapis.com/oauth2/token` with `grant_type`, `client_id`, `client_secret`, plus `refresh_token` or `instance_id`. Cache access tokens in memory for the invoke only.
- Timeout 25s. Body cap 8 MiB. Order search / product query max 100 rows.
- HTTP 401/403/404/428 (wrong catalog version)/429/5xx → `{ error }` on OUTPUT. Never throw past dispatch. 428 on V3 inventory: tell the operator the site is Catalog V1.
- `trigger`: verify Wix JWT (app public key) on ingress; 401 on bad signature. 200 within 1250 ms. Deduplicate by event id if present (retries can duplicate).
- Never log client secrets, refresh tokens, access tokens, instance ids, buyer emails, or addresses. Log operation, HTTP status, and GUIDs only.
