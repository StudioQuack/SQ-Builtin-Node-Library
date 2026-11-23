# Printify

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/printify/`
- **Researched:** 2026-08-20
- **Official API:** Printify REST API v1
- **Docs:** https://developers.printify.com/
- **Auth:** api_key — secret binding: `api_token`

## Scope

One node for the merchant’s own Printify account. It calls `https://api.printify.com/v1/` with a **Personal Access Token** (`Authorization: Bearer`) plus a required `User-Agent`. The customer owns the Printify merchant account and shops. Official webhooks exist (`/v1/shops/{shop_id}/webhooks.json`); this spec adds catalog `trigger` so inbound shop/order/product events land on the **same** node. OAuth 2.0 exists for multi-merchant platforms; this node binds a merchant PAT, not a StudioQuack public app. **Do not build:** HTML scrapers of printify.com; AfterShip/Printful mashups; sending `contents` (base64 artwork) in logs. Artwork upload is not a starting catalog verb — `product_create` references Printify image ids or HTTPS URLs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `shop_list` | GET | `/v1/shops.json` | List shops on the token’s account; pick `shop_id`. |
| `product_create` | POST | `/v1/shops/{shop_id}/products.json` | Create a shop product from a catalog blueprint + print provider + print areas. |
| `product_publish` | POST | `/v1/shops/{shop_id}/products/{product_id}/publish.json` | Start publishing selected fields to the connected sales channel. |
| `order_submit` | POST | `/v1/shops/{shop_id}/orders.json` | Submit an order (existing product, SKU, or on-the-fly print areas). |
| `order_get` | GET | `/v1/shops/{shop_id}/orders/{order_id}.json` | Fetch one order (status, tracking, costs). |
| `trigger` | POST | `/v1/shops/{shop_id}/webhooks.json` | Subscribe the shop to an official topic (`order:created`, `order:updated`, `product:publish:started`, …). Incoming POSTs use StudioQuack message-event ingress. GET lists; PUT updates URL; DELETE removes. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `shop_list` | no | Chooses the row above |
| `api_token` | password | yes | | yes | Personal Access Token (Bearer). Never log. |
| `shop_id` | string | no | | no | Shop id from `shop_list`. Required except `shop_list`. |
| `user_agent` | string | no | `StudioQuack-Printify-Node` | no | Printify requires `User-Agent` on every request. |
| `webhook_url` | string | no | | no | HTTPS ingress URL for `trigger`. |
| `webhook_secret` | password | no | | yes | HMAC secret for `x-pfy-signature` (if the shop webhook is signed). |
| `timeout_ms` | number | no | `60000` | no | JDK HTTP timeout (publish/order can be slow). |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Artwork as `file` or Printify image id / HTTPS URL — never raw bytes. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = shop_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| _(none)_ | | | Uses `api_token` only. |

### `operation = product_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Product title. |
| `description` | string | yes | Product description (may include legally required safety/GPSR text). |
| `blueprint_id` | number | yes | Catalog blueprint id. |
| `print_provider_id` | number | yes | Print provider id. |
| `variants` | object[] | yes | `{ id, price, is_enabled }` catalog variant rows. |
| `print_areas` | object[] | yes | Placeholders/images. Images: Printify upload id and/or HTTPS `src`. Optional `file` (ref-id) per placeholder — handler must not log bytes; prefer URL ingest (`POST /v1/uploads/images.json` with `url`, not `contents`). |
| `safety_information` | string | no | GPSR / safety copy where required. |
| `tags` | string[] | no | Tags. |

### `operation = product_publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Printify product id. |
| `title` | boolean | no | Publish title (default true). |
| `description` | boolean | no | Publish description. |
| `images` | boolean | no | Publish images. |
| `variants` | boolean | no | Publish variants. |
| `tags` | boolean | no | Publish tags. |
| `keyFeatures` | boolean | no | Publish key features. |
| `shipping_template` | boolean | no | Publish shipping template. |

### `operation = order_submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `external_id` | string | yes | Merchant order id (idempotency). |
| `line_items` | object[] | yes | `product_id`+`variant_id`, or `sku`, or `blueprint_id`+`print_provider_id`+`print_areas`. `quantity` required. |
| `address_to` | object | yes | `first_name`, `last_name`, `address1`, `city`, `zip`, `country`, `email`; optional `phone`, `region`. |
| `shipping_method` | number | yes | Printify shipping method id. |
| `label` | string | no | Human order label. |
| `send_shipping_notification` | boolean | no | Let Printify email the buyer. Default false (minimize PII fan-out). |
| `is_printify_express` | boolean | no | Express production. |
| `is_economy_shipping` | boolean | no | Economy shipping. |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Printify order id. |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | no | `subscribe` (default), `list`, `unsubscribe`. |
| `topic` | string | no | Required on subscribe. Official topics include `order:created`, `order:updated`, `order:sent-to-production`, `order:shipment:created`, `order:shipment:delivered`, `product:publish:started`. |
| `url` | string | no | HTTPS callback; defaults to `webhook_url`. |
| `webhook_id` | string | no | Required for `unsubscribe`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `shops` | object[] | `shop_list` | `{ id, title, sales_channel }`. |
| `product_id` | string | `product_create` / `product_publish` | Printify product id. |
| `order_id` | string | `order_submit` / `order_get` | Printify order id. |
| `order` | object | `order_get` | Status, line costs, shipments/tracking — strip extra buyer PII from logs. |
| `webhook` | object | `trigger` | `{ id, topic, url }`. |
| `webhooks` | object[] | `trigger` list | Installed hooks (ids/topics/urls only). |
| `error` | object | failure | `{ message, http_status, code }` — no token, no artwork bytes. |

## Legal

- ToS / developer policy URL: [Printify Terms of Service](https://printify.com/terms-of-service/); developer docs https://developers.printify.com/
- Rate limits / quota notes (as published): **600 requests / minute** per integration (account, not per token). Catalog endpoints: additional **100 / minute**. Product publish: **200 / 30 minutes**. Product creation as a result of order creation is not under that publish cap. HTTP **429** = too many requests.
- PII / residency / prohibited uses: Customer warrants **artwork / IP rights** for all print files. Never log print-file bytes or Printify `contents` base64. Minimize `address_to` in logs (ids only). PCI: Printify is not a card-capture PSP. Cross-ref `docs/catalog/finance-accounting.md` only if the workflow charges a card elsewhere.
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Timeouts, size caps, retry posture. Default 60s. Cap request JSON (suggest 2 MiB). If an implementer later adds uploads, use URL ingest only; never log `contents`. Retry 429/5xx with backoff; do not retry `order_submit` without the same `external_id`.
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Printify error bodies use `{ status, code, message, errors }`.
- Headers: `Authorization: Bearer {api_token}`, `User-Agent: {user_agent}`, `Content-Type: application/json`.
- `trigger`: verify `x-pfy-signature` (HMAC-SHA256) when a secret is configured. Do not log full webhook bodies (buyer PII). Return 2xx to ingress quickly.
- Publishing is asynchronous: `product_publish` starts the handshake; sales-channel push is the merchant’s (or webhook `product:publish:started`) concern. Optional follow-up endpoints `publishing.json` succeeded/failed exist on the API but are not starting catalog verbs.
