# Stamped.io

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/stamped/`
- **Researched:** 2026-08-20
- **Official API:** Stamped **V2 Reviews** (`2.0.0`) on `https://stamped.io`. Loyalty 2.0 uses a separate **V3** API — out of starting ops; one node still, add namespaced loyalty ops later if catalog expands.
- **Docs:** https://developers.stamped.io/docs/getting-started. V2 reviews index: https://developers.stamped.io/v2.0/reference/getreviews-1. NPS: https://developers.stamped.io/v2.0/reference/nps-1. Help: https://stampedsupport.stamped.io/hc/en-us/articles/10152777765659-Stamped-REST-API
- **Auth:** HTTP Basic — public API key (username) + private API secret (password) — secret bindings: `api_key`, `api_secret`. Path `store_hash`. Create-review also sends public `apiKey` + `sId` as query params per V2 OpenAPI.

## Scope

One node for Stamped reviews and NPS. V2 is the current Reviews API; V2 “will be deprecated once V3 Reviews ships” (not shipped as of docs dated 2025-09-16). API access requires a **Professional or Enterprise** Stamped plan.

UGC: moderate before republish. Never log reviewer email or review body.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `review_list` | GET | `/api/v2/{storeHash}/dashboard/reviews` | Dashboard review list (filters: search, rating, state, dateFrom/dateTo, email) |
| `review_create` | POST | `/api/reviews3` | Create a review. Query `apiKey` (public) + `sId` (`store_hash`) |
| `nps` | GET | `/api/v2/{storeHash}/dashboard/nps/` | List/filter NPS scores |

Public widget GETs exist (`Get Widget Reviews`) — do not use them with the private secret in a browser. Photo/video on create: `files` is type **`file`** (platform ref-ids), never inline bytes.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `review_list` | no | Chooses the row above |
| `store_hash` | string | yes | empty | no | ShopId / Store Hash (path `{storeHash}` / query `sId`) |
| `api_key` | password | yes | empty | yes | Public API key (Basic username; create-review `apiKey`) |
| `api_secret` | password | yes | empty | yes | Private API key (Basic password). Header alternative documented as `stamped-api-key` for some V3 calls — V2 dashboard uses Basic |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `files` on create is type `file`. No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = review_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Search string |
| `rating` | number | no | Star filter |
| `state` | string | no | Publish/moderation state |
| `date_from` | number | no | `dateFrom` (provider numeric timestamp) |
| `date_to` | number | no | `dateTo` |
| `email` | string | no | Author email filter — PII; OpenAPI marks required but treat as optional unless 4xx says otherwise |

### `operation = review_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `review_message` | string | no | Body (`reviewMessage`) — PII, never log |
| `review_title` | string | no | Title |
| `author` | string | no | Display name |
| `email` | string | no | Author email — PII |
| `review_rating` | string | no | `"1"`–`"5"` |
| `product_id` | string | no | Catalog product id |
| `product_name` | string | no | Product name |
| `product_url` | string | no | Product URL |
| `product_sku` | string | no | SKU |
| `product_image_url` | string | no | Public product image URL |
| `review_recommend_product` | string | no | `"true"` \| `"false"` |
| `review_source` | string | no | Source label |
| `user_reference` | string | no | Author reference |
| `files` | file | no | Photo/video **ref-ids** (jpg/png/gif/mp4/mov). Multipart only if dashboard media upload is enabled |

### `operation = nps`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Search (OpenAPI marks required — send empty string if listing all fails) |
| `state` | string | no | State filter |
| `rating` | number | no | Score filter |
| `date_from` | number | no | `dateFrom` |
| `date_to` | number | no | `dateTo` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `reviews` | array | `review_list` / `review_create` | Review ids, rating, state — omit email/body from logs |
| `nps` | array | `nps` | NPS rows / aggregate as returned |
| `error` | string | failure | Actionable, no keys |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Stamped merchant ToS + Professional/Enterprise plan requirement for API.
- Shopify/BigCommerce platform terms when the shop is on those platforms.
- NPS is trademarked methodology; Stamped collects scores — do not claim certification.
- Official-API-first: no scrape of dashboard or widgets.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- `Authorization: Basic base64(api_key + ":" + api_secret)` on V2 dashboard GETs. Create: query `apiKey` + `sId` — still send Basic if the endpoint requires it; never log the URL.
- Empty keys/`store_hash` → `{ error }` — no HTTP.
- Multipart for `files`: resolve `file` ref-ids to bytes in-sandbox only; cap each part; do not log filenames that contain emails.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Logs: operation, store_hash, HTTP status, counts — never keys, emails, or review/NPS comment text.
