# Squarespace Commerce

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/squarespace/`
- **Researched:** 2026-08-20
- **Official API:** Squarespace Commerce APIs — **Orders 1.0**, **Inventory 1.0**, **Products v2**, **Webhook Subscriptions 1.0**. Host `https://api.squarespace.com`.
- **Docs:** Overview: https://developers.squarespace.com/commerce-apis/overview — auth: https://developers.squarespace.com/commerce-apis/authentication-and-permissions — orders: https://developers.squarespace.com/commerce-apis/retrieve-all-orders — inventory: https://developers.squarespace.com/commerce-apis/inventory — products: https://developers.squarespace.com/commerce-apis/retrieve-all-products — webhooks: https://developers.squarespace.com/commerce-apis/webhooksubscriptions — rate limits: https://developers.squarespace.com/commerce-apis/rate-limits — making requests: https://developers.squarespace.com/commerce-apis/making-requests — OAuth self-service (2026-07-27): https://developers.squarespace.com/changes/self-service-oauth-credential-management
- **Auth:** Bearer API key **or** OAuth 2 access token — secret binding: `api_key` and/or `oauth_client_secret` + `oauth_refresh_token`. `trigger` is **OAuth-only**.

## Scope

One node for a merchant-owned Squarespace Commerce site. The customer owns the site.

**Partner / plan gating (not a blanket DO NOT BUILD):**

- **Custom (single-site) apps** on a **Commerce Advanced / Premium** plan: the merchant generates a Developer API key (Settings → Advanced → Developer API Keys). Official steps: https://developers.squarespace.com/commerce-apis/authentication-and-permissions . Orders, inventory, and products work with that key. This is the starting path.
- **Squarespace Extensions** (multi-site / App listing) **must use OAuth**. As of **2026-07-27**, OAuth client ids/secrets are created self-service under Developer Apps; new apps start in **Demo Mode** and need a **production review** before live merchants. Changelog: https://developers.squarespace.com/changes/self-service-oauth-credential-management
- **`trigger` (Webhook Subscriptions API) does not support API keys** — OAuth only (same auth doc). If `auth_mode=api_key`, `trigger` returns `{ error }` and does not call Squarespace.

Do **not** treat the whole node as unbuildable: the Commerce REST surface is public and documented. Public Extension distribution remains review-gated.

Donations are **not** on the Orders API (use Transactions API — omitted from starting verbs). Card capture is **not** this node — cross-ref `docs/catalog/finance-accounting.md`. Profiles API is maintenance-mode; omitted.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_list` | GET | `/1.0/commerce/orders` | Paginated orders (max 50, cursor) |
| `order_get` | GET | `/1.0/commerce/orders/{id}` | One order |
| `inventory_get` | GET | `/1.0/commerce/inventory` or `/1.0/commerce/inventory/{variantIdCsvs}` | Stock for variants (max 50). Physical/service only |
| `product_list` | GET | `/v2/commerce/products` | Paginated products (max 50, cursor) |
| `trigger` | POST/GET/DELETE | `/1.0/webhook_subscriptions` | OAuth-only subscribe / list / delete. HTTPS endpoint = message-event ingress |

Omitted (exist but not catalog starting verbs): create-order import, fulfill-order, product write, inventory adjust. Add later only if catalog expands.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_list` | no | Chooses the row above |
| `auth_mode` | select | yes | `api_key` | no | `api_key` or `oauth` |
| `api_key` | password | when `api_key` | | yes | Developer API key. Header `Authorization: Bearer {key}` |
| `oauth_client_id` | string | when oauth | | no | OAuth client id |
| `oauth_client_secret` | password | when oauth | | yes | OAuth client secret |
| `oauth_refresh_token` | password | when oauth | | yes | Refresh token from the merchant grant |
| `user_agent` | string | yes | `StudioQuack-Squarespace` | no | **Required** by Squarespace. Requests without `User-Agent` are rejected. Do not send a generic curl default in production |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens. `trigger` subscribe may return `webhook_secret` once — treat as secret; do not log |

FUNCTION ports: none. File payloads: none on starting verbs (product image upload is out of scope).

## Input fields (by operation)

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cursor` | string | no | `pagination.nextPageCursor`. Cannot mix with other filters |
| `modified_after` | string | with `modified_before` | ISO 8601 UTC |
| `modified_before` | string | with `modified_after` | ISO 8601 UTC |
| `customer_id` | string | no | Filter by customer |
| `fulfillment_status` | string | no | `PENDING` / `FULFILLED` / `CANCELED` |
| `payment_states` | string | no | Comma list. Default if omitted: `NOT_CHARGED,AUTHORIZED,PAID,REFUNDED`. Pass `PARTIALLY_PAID` to include payment-plan orders mid-collection |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Order id |

### `operation = inventory_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `variant_ids` | string | no | Comma-separated variant ids (max 50). Omit to list with cursor |
| `cursor` | string | no | Next page when listing all |

### `operation = product_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cursor` | string | no | Next page |
| `modified_after` / `modified_before` | string | no | Date window |
| `type` | array | no | Product types (`PHYSICAL`, `SERVICE`, `GIFT_CARD`, `DIGITAL`) |
| `query` | string | no | Search |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | yes | `subscribe` / `unsubscribe` / `list` |
| `endpoint_url` | string | when subscribe | HTTPS message-event ingress |
| `topics` | array | when subscribe | Official: `order.create`, `order.update` (plus `extension.uninstall`, contact/address topics if scoped). `order.create` for payment-plan orders fires only when `paymentState` becomes `PAID` |
| `subscription_id` | string | when unsubscribe | Subscription id |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `orders` | array | `order_list` | Up to 50 `Order` objects (`id`, `orderNumber`, `fulfillmentStatus`, `paymentState`, `grandTotal`, `customerEmail`, `createdOn`, `modifiedOn`) |
| `order` | object | `order_get` | Full Order (includes addresses — PII) |
| `inventory_items` | array | `inventory_get` | `InventoryItem` (`variantId`, `sku`, `quantity`, `isUnlimited`) |
| `products` | array | `product_list` | Product summaries (`id`, `name`, `type`, `isVisible`, `modifiedOn`) |
| `pagination` | object | list ops | `nextPageCursor`, `hasNextPage` |
| `webhook` | object | `trigger` subscribe | `id`, `endpointUrl`, `topics`, `websiteId`. `secret` only on create/rotate — map to a daemon secret; **do not log** |
| `webhooks` | array | `trigger` list | Up to 25 subscriptions (no secret) |
| `error` | string | failure | Mapped error. Never tokens |

## Legal

- Squarespace Developer Terms: https://www.squarespace.com/developer-terms (fetched 2026-08-20). Brand / press rules: https://developers.squarespace.com/commerce-apis/faq
- Rate limits (published): **300 requests per minute** (~5/s). Over quota → **429** with a **one-minute** cool-down. Create Order (not in starting verbs) is 100/hour per site **for API keys only**; OAuth is exempt.
- PII: `customerEmail`, billing/shipping addresses, customer ids. Inventory has no buyer PII. Never log order emails/addresses.
- PCI: Orders expose `paymentState`, not PAN. Cross-ref `docs/catalog/finance-accounting.md`.
- Official-API-first: `https://api.squarespace.com` only. HTTP (non-TLS) is rejected by Squarespace. No site HTML scrape.

## Implementation notes

- JDK HTTP only. Headers on every call: `Authorization: Bearer {token}`, `User-Agent: {user_agent}`, `Content-Type: application/json` on writes.
- Timeout 25s. Body cap 8 MiB. Page size is server-capped at 50 (orders/products/inventory) and 25 (webhooks).
- HTTP 400/401/404/409/429/5xx → `{ error }` on OUTPUT. 429: wait **60s** (published cool-down) then retry once. Never throw past dispatch.
- Refresh OAuth access tokens before expiry; never log them.
- `trigger`: verify notification signatures with the subscription secret (Squarespace “Verifying notifications” guide). Ingress must 200 quickly. API-key auth → error without HTTP.
- Never log API keys, OAuth secrets, webhook secrets, emails, or addresses. Log method, path, status, and resource ids.
