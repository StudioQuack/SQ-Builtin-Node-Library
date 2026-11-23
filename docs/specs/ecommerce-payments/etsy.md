# Etsy

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/etsy/`
- **Researched:** 2026-08-20
- **Official API:** Etsy Open API v3 (current; v2 retired)
- **Docs:** https://developer.etsy.com/documentation/ · OpenAPI: https://www.etsy.com/openapi/generated/oas/3.0.0.json · Reference UI: https://developer.etsy.com/documentation/reference
- **Auth:** oauth2 — secret binding: `api_keystring`, `shared_secret`, `refresh_token`

## Scope

Seller-owned Etsy shop automation: listings, shop receipts (Etsy’s order object), shop profile, and inbound order-lifecycle webhooks. The customer owns the Etsy seller account and the Etsy Developer Portal app (Seller App = own shop only; Personal App / Commercial Access for other shops). Calls go only to `https://api.etsy.com/v3/` (or the equivalent host `https://openapi.etsy.com/v3/`). **DO NOT BUILD** HTML scraping of etsy.com, Etsy v2 OAuth 1.0a, or any unofficial receipt/listing dump. Etsy Payments stays on Etsy; this node never captures cards (PCI). Commercial Access apps that request `transactions_r` must separately request `buyer_email` if they need it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `listing_get` | GET | `/v3/application/listings/{listing_id}` | Load one listing (`getListing`). Optional `includes` associations. |
| `listing_list` | GET | `/v3/application/shops/{shop_id}/listings` | Page a shop’s listings (`getListingsByShop`). Filter by `state`. |
| `listing_create` | POST | `/v3/application/shops/{shop_id}/listings` | Create a **physical draft** listing (`createDraftListing`). Activate later with PATCH (out of starting set). |
| `order_list` | GET | `/v3/application/shops/{shop_id}/receipts` | List shop receipts (`getShopReceipts`). This is the official order list. |
| `receipt_get` | GET | `/v3/application/shops/{shop_id}/receipts/{receipt_id}` | One receipt (`getShopReceipt`), including buyer address only when Etsy grants it. |
| `shop_get` | GET | `/v3/application/shops/{shop_id}` | Shop profile (`getShop`). |
| `trigger` | POST (inbound) | Etsy → operator HTTPS callback; subscriptions are created in the Developer Portal Webhook portal, not via Open API REST | Verify and map `order.paid` / `order.canceled` / `order.shipped` / `order.delivered`. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `listing_get` | no | Chooses the row above |
| `api_keystring` | password | yes | | yes | Etsy App API keystring (`client_id` for OAuth) |
| `shared_secret` | password | yes | | yes | App shared secret. Sent as `x-api-key: {keystring}:{shared_secret}` on every call |
| `refresh_token` | password | yes (except public-only; all starting ops need OAuth) | | yes | OAuth 2 refresh token from authorization-code + PKCE. Rotates on every refresh — persist the new value |
| `shop_id` | number | yes | | no | Etsy shop numeric id used on shop-scoped paths |
| `webhook_signing_secret` | password | `trigger` only | | yes | Portal signing secret (`whsec_…`). Used to HMAC-verify inbound webhooks |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = listing_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listing_id` | number | yes | Listing id |
| `includes` | string[] | no | Associations: `Images`, `Shop`, `User`, `Translations`, `Videos`, `Personalization`, `BuyerPrice` |
| `language` | string | no | IETF tag for translations (`en`, `de`, …) |

### `operation = listing_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `state` | string | no | `active` (default), `inactive`, `sold_out`, `draft`, `expired` |
| `limit` | number | no | 1–100, default 25 |
| `offset` | number | no | Skip count, default 0 |
| `sort_on` | string | no | `created`, `price`, `updated`, `score` |
| `sort_order` | string | no | `asc` / `desc` |

### `operation = listing_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `quantity` | number | yes | Offering quantity (> 0) |
| `title` | string | yes | Listing title (Etsy character rules) |
| `description` | string | yes | Listing body |
| `price` | number | yes | Base price (> 0) |
| `who_made` | string | yes | `i_did` \| `someone_else` \| `collective` |
| `when_made` | string | yes | Etsy era enum (e.g. `made_to_order`, `2020_2026`) |
| `taxonomy_id` | number | yes | Seller taxonomy id |
| `shipping_profile_id` | number | no | Required by Etsy when activating a physical listing |
| `return_policy_id` | number | no | Shop return policy |
| `item_weight` / `item_length` / `item_width` / `item_height` | number | no | Physical package; units per OpenAPI |
| `type` | string | no | `physical` (starting set). Digital listings need file upload (out of starting set) |

### `operation = order_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `min_created` / `max_created` | number | no | Unix seconds receipt created range |
| `min_last_modified` / `max_last_modified` | number | no | Unix seconds last-modified range |
| `was_paid` | boolean | no | Filter paid / unpaid |
| `was_shipped` | boolean | no | Filter shipped |
| `was_delivered` | boolean | no | Filter delivered (when supported by receipt schema) |
| `limit` | number | no | Page size |
| `offset` | number | no | Skip count |

### `operation = receipt_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `receipt_id` | number | yes | Shop receipt id |

### `operation = shop_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Uses config `shop_id` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | yes | Exact JSON bytes Etsy POSTed (before parse) |
| `webhook_id` | string | yes | Header `webhook-id` |
| `webhook_timestamp` | string | yes | Header `webhook-timestamp` (unix seconds) |
| `webhook_signature` | string | yes | Header `webhook-signature` |

Register the StudioQuack message-event HTTPS URL in Etsy’s Webhook portal (`+Add Endpoint`). There is **no** Open API REST to create subscriptions as of 2026-08-20.

## Output fields (by operation)

### `operation = listing_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `listing_id` | number | success | Listing id |
| `listing` | object | success | ShopListing (+ requested associations) |

### `operation = listing_list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `count` | number | success | Page count |
| `listings` | object[] | success | Listing rows |
| `pagination` | object | success | `limit`, `offset`, `total` when present |

### `operation = listing_create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `listing_id` | number | success | New draft listing id |
| `listing` | object | success | Created ShopListing (`state` is draft until activated) |

### `operation = order_list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `count` | number | success | Receipt count on this page |
| `receipts` | object[] | success | ShopReceipt list. Omit buyer address fields from logs |

### `operation = receipt_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `receipt_id` | number | success | Receipt id |
| `receipt` | object | success | ShopReceipt. Address fields may be null without preferred-partner status |

### `operation = shop_get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `shop` | object | success | Shop resource |

### `operation = trigger`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | `200` after signature + freshness checks |
| `event_type` | string | success | `order.paid` \| `order.canceled` \| `order.shipped` \| `order.delivered` |
| `shop_id` | number | success | Shop on the event |
| `resource_url` | string | success | Prepared Open API URL (usually a receipt GET) |
| `receipt_id` | number | success | Parsed from `resource_url` when present |

All operations: HTTP 4xx/5xx or verify failure → `{ error }` only (message, `http_status` when known). Never echo tokens, `x-api-key`, or buyer PII in `error`.

## Legal

- ToS / developer policy: [Etsy API Terms of Use](https://www.etsy.com/legal/api/) · [Open API docs / app paths](https://developer.etsy.com/documentation/) (Seller App vs Personal vs Commercial Access). Commercial apps must display: “The term 'Etsy' is a trademark of Etsy, Inc. This application uses the Etsy API but is not endorsed or certified by Etsy, Inc.”
- Rate limits: per API key, QPS then sliding 24h QPD. Values are shown in the Developer Portal and on success headers `x-limit-per-second`, `x-remaining-this-secon`, `x-limit-per-day`, `x-remaining-today`. Over limit → HTTP 429 + `retry-after`. Request higher quota at developer@etsy.com. Do not mint extra keys to dodge limits.
- PII / residency / prohibited uses: receipts include buyer name/email/address; address lines are regionally gated. Do not log them. `buyer_email` needs extra Commercial Access approval. No screen-scraping. Seller App must not access other shops. Customer owns the shop and accepts Etsy seller + API terms.
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Every request: `https` only; header `x-api-key: {keystring}:{shared_secret}`; scoped calls also `Authorization: Bearer {user_id}.{access_token}` (Etsy concatenates numeric user id, a period, and the access token).
- Token: POST `https://api.etsy.com/v3/public/oauth/token` with `grant_type=refresh_token`, `client_id` = keystring, `refresh_token`. Access token ~1 hour. **Refresh tokens rotate**; store the new refresh token in the secret store. Never log refresh or access tokens.
- Timeouts: connect 10s, read 30s for listing/receipt/shop; `trigger` is CPU-only (HMAC). Cap JSON responses at 2 MiB.
- Retry: on 429 honor `retry-after` (else exponential backoff + jitter, max 3). Do not retry 401/403 without a single token refresh.
- `trigger` verify: `signed_content = webhook-id + "." + webhook-timestamp + "." + raw_body`; HMAC-SHA256 with Base64-decoded secret after stripping `whsec_`; reject if `|now - timestamp| > 300s`. Then parse JSON.
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
- Minimize receipt fields on OUTPUT when the workflow only needs `receipt_id` / paid / shipped flags.
