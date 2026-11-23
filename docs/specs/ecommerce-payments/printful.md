# Printful

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/printful/`
- **Researched:** 2026-08-20
- **Official API:** Printful REST API v1 (stable default); v2 Open Beta under `/v2`
- **Docs:** https://developers.printful.com/docs/ (v1); https://developers.printful.com/docs/v2-beta/ (v2)
- **Auth:** api_key — secret binding: `api_token`

## Scope

One node for the merchant’s own Printful store. It calls Printful’s official REST API at `https://api.printful.com` with a Developer Portal **private token** (`Authorization: Bearer`). The customer owns the Printful merchant account. Default `api_version` is **v1** because catalog verbs (`sync_product`) map to v1 Sync Products / Orders / Files / Shipping / Webhooks. v2 is official Open Beta (live-usable, still refining; same private token, paths prefixed `/v2`). Public-app OAuth 2.0 exists for multi-merchant apps; this node does not implement a StudioQuack OAuth app — merchants bind a private token. Account-level tokens must send `X-PF-Store-Id`. **Do not build:** unofficial storefront scrapers; Printful mockup-generator as a starting verb (not in the catalog row); logging of print-file bytes.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `sync_product` | POST | `/store/products` | Create a Sync Product + variants (v1). v2: use catalog/store product endpoints under `/v2` when `api_version=v2`. |
| `order_create` | POST | `/orders` | Create a draft/pending order for fulfillment. |
| `order_get` | GET | `/orders/{id}` | Fetch one order by Printful id or `@external_id`. |
| `file_upload` | POST | `/files` | Add artwork to the File Library. INPUT uses type `file` (ref-id). Printful ingest is URL-based JSON (`url` + `filename`); never inline bytes in logs. |
| `shipping_rates` | POST | `/shipping/rates` | Quote shipping for recipient + items before order submit. |
| `trigger` | POST | `/webhooks` | Register the store webhook URL (one URL per store) for official events (`package_shipped`, `stock_updated`, …). Incoming POSTs are delivered through the StudioQuack engine's message-event ingress, not a second node. GET `/webhooks` to read; DELETE `/webhooks` to disable. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `sync_product` | no | Chooses the row above |
| `api_token` | password | yes | | yes | Printful private token (Bearer). Never log. |
| `store_id` | string | no | | no | Printful store id. Sent as `X-PF-Store-Id`. Required for account-level tokens. |
| `api_version` | select | no | `v1` | no | `v1` (stable) or `v2` (Open Beta `/v2` prefix). |
| `webhook_url` | string | no | | no | HTTPS StudioQuack ingress URL used by `trigger`. |
| `timeout_ms` | number | no | `60000` | no | JDK HTTP timeout. File library + rates can exceed 15s. |

File payloads (upload, download, convert) use type `file` (platform ref-id),
never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Recipient / order PII only when the operation needs it. Artwork as `file`, never bytes. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets. Labels/print files as `file` refs if returned. |

FUNCTION ports: none unless this node must invoke another node N times.

## Input fields (by operation)

### `operation = sync_product`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Sync Product name. |
| `external_id` | string | no | Merchant SKU / storefront id. |
| `thumbnail` | string | no | Public HTTPS thumbnail URL. |
| `sync_variants` | object[] | yes | Variants: `variant_id` (catalog), `retail_price`, `sku`, `files[]` (`type`, `url` or Printful file `id`). Prefer File Library ids from `file_upload`. |
| `is_ignored` | boolean | no | Skip ecommerce sync. |

### `operation = order_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `recipient` | object | yes | `name`, `address1`, `city`, `country_code`, `zip`; optional `state_code`, `phone`, `email`. |
| `items` | object[] | yes | Line items: `variant_id` or `sync_variant_id` or `external_variant_id`, `quantity`, optional print `files`. |
| `external_id` | string | no | Merchant order id. |
| `shipping` | string | no | Shipping method id from `shipping_rates` (e.g. `STANDARD`). |
| `confirm` | boolean | no | If true, confirm immediately (charges the Printful wallet). Default draft. |

### `operation = order_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Printful numeric id, or `@` + `external_id`. |

### `operation = file_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes* | Platform ref-id. Handler resolves a fetchable HTTPS URL for Printful; never logs bytes. |
| `url` | string | yes* | Public HTTPS artwork URL if already hosted. *Provide `file` and/or `url`. |
| `filename` | string | no | File name Printful stores. |
| `type` | string | no | Print file type, default `default`. |

### `operation = shipping_rates`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `recipient` | object | yes | Address fields as in `order_create`. |
| `items` | object[] | yes | `variant_id` (or warehouse/external variant id) + `quantity`. |
| `currency` | string | no | ISO currency (e.g. `USD`). |
| `locale` | string | no | Locale for delivery-date strings. |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | select | no | `subscribe` (default) or `unsubscribe`. |
| `types` | string[] | no | Event types (`package_shipped`, `order_updated`, `stock_updated`, …). Required on subscribe. |
| `url` | string | no | Overrides `webhook_url`. Must be HTTPS. |
| `params` | object | no | Per-type params (e.g. `stock_updated.product_ids`). |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | HTTP/result status (`ok`). |
| `id` | string/number | create/get | Printful resource id (product, order, file, webhook). |
| `external_id` | string | when present | Merchant id echo. |
| `order_status` | string | `order_*` | Printful status (`draft`, `pending`, `inprocess`, `fulfilled`, …). |
| `sync_product` | object | `sync_product` | Created product + variant ids (no file bytes). |
| `file_id` | number | `file_upload` | File Library id. |
| `file_status` | string | `file_upload` | Processing status (`ok`, `waiting`, `failed`). |
| `rates` | object[] | `shipping_rates` | `{ id, name, rate, currency, minDeliveryDays, maxDeliveryDays }`. |
| `shipments` | object[] | `order_get` | Tracking numbers/urls only; no extra buyer PII. |
| `webhook` | object | `trigger` | `{ url, types }`. |
| `error` | object | failure | `{ message, http_status, code }` — no token, no file bytes. |

## Legal

- ToS / developer policy URL: [Printful Terms of Service](https://www.printful.com/policies/terms-of-service); [Intellectual Property Policy](https://www.printful.com/policies/intellectual-property) (linked from ToS §3). Developer docs: https://developers.printful.com/docs/
- Rate limits / quota notes (as published): general **120 requests / minute**. Resource-intensive endpoints (e.g. mockup generator, not a starting verb) are lower. v1 resets per window; v2 uses leaky-bucket (`X-Ratelimit-Limit/Remaining/Reset/Policy`). HTTP **429** → honor `Retry-After` / reset headers. Unauthenticated catalog traffic is tighter (docs: 30/60s) — this node always authenticates.
- PII / residency / prohibited uses: Customer warrants they have **rights to all artwork** (ToS §3 Content). Do not log print-file bytes, storefront passwords, or order PII beyond ids/status needed by the operation. Recipient address is required for orders/rates — pass through, do not persist in handler logs. PCI: Printful is not a card-capture PSP; do not send pan/cvc. Cross-ref `docs/catalog/finance-accounting.md` only if a later workflow charges a card via a Finance node.
- Official-API-first: no scrape, no unofficial Discord/web wrapper

## Implementation notes

- JDK HTTP only (no vendor SDK) unless a reason is stated.
- Timeouts, size caps, retry posture. Connect+read timeout default 60s (`timeout_ms`). Cap JSON bodies (suggest 2 MiB) and artwork URL length. File Library: do not buffer print bytes into logs or `{ error }`. Retry **429** and transient **5xx** with exponential backoff + jitter; stop after 3 retries. Do not retry POST `/orders` on unknown outcome (idempotency: send `external_id`).
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
- Headers: `Authorization: Bearer {api_token}`, `Content-Type: application/json`, `X-PF-Store-Id` when `store_id` set. `User-Agent` identifying the StudioQuack node.
- v2 path mapping (when `api_version=v2`): prefix `/v2` (e.g. `/v2/orders`, `/v2/files`). Confirm live v2 resource names at build time — v2 remains Open Beta as of 2026-08-20.
- Webhook verify: v2 docs add request signing + HTTPS-only; v1 POSTs JSON `{ type, data }`. Verify authenticity per current docs before correlating a message event. Respond 2xx quickly; Printful retries on non-2xx.
