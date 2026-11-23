# PrestaShop

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/prestashop/`
- **Researched:** 2026-08-20
- **Official API:** PrestaShop **Webservice API** (CRUD over HTTP), current docs **PrestaShop 9**. JSON **output** via `output_format=JSON`. Writes remain **XML** (as of PrestaShop 8.1+ the webservice cannot read JSON bodies).
- **Docs:** https://devdocs.prestashop.com/9/webservice/ — getting started: https://devdocs.prestashop.com/9/webservice/getting-started/ — resource index: https://devdocs.prestashop.com/9/webservice/resources/
- **Auth:** Webservice key — secret binding: `api_key` (HTTP Basic username, empty password; or `Authorization: Basic` of `api_key:`)

## Scope

One node for a merchant-owned PrestaShop shop. The customer enables the webservice in the back office, creates a key, and **scopes that key** to the resources this node will call (catalog note: “Key is shop-wide; scope it in PrestaShop”). Base URL is `{url}/api/` on the merchant’s egress.

This node exposes generic `resource_*` verbs (not one operation per resource) against an **allowlist** of storefront resources. Employees, configurations, and shop-group admin resources are excluded.

There is **no** first-class webhook API on the webservice. Catalog has no `trigger` — do not invent one.

Card capture is **not** this node. Cross-ref `docs/catalog/finance-accounting.md`.

JSON input is **not** available on the official webservice as of PS 9 docs (“cannot read JSON inputs”). The handler must serialize writes to XML.

## Operations (`operation` config values)

Base: `{url}/api/{resource}`. Always add `output_format=JSON` on reads.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `resource_list` | GET | `/api/{resource}` | List ids (add `display=full` or `display=[fields]` for records) |
| `resource_get` | GET | `/api/{resource}/{id}` | One record |
| `resource_create` | POST | `/api/{resource}` | Create (XML body) |
| `resource_update` | PUT or PATCH | `/api/{resource}/{id}` | Full replace (`PUT`) or partial (`PATCH`) |
| `resource_delete` | DELETE | `/api/{resource}/{id}` | Delete |

### Allowlisted `resource` values

From https://devdocs.prestashop.com/9/webservice/resources/ — **only** these (core storefront):

`addresses`, `carriers`, `carts`, `categories`, `combinations`, `countries`, `currencies`, `customers`, `deliveries`, `languages`, `manufacturers`, `order_carriers`, `order_details`, `order_histories`, `order_invoices`, `order_payments`, `order_states`, `orders`, `product_option_values`, `product_options`, `products`, `specific_prices`, `states`, `stock_availables`, `stocks`, `tags`, `taxes`, `zones`

`search` is GET-only (not create/update/delete). `images` uses a special upload path — if `resource=images`, `resource_create` accepts platform type `file` (see inputs). Reject any other resource name before HTTP.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `resource_get` | no | Chooses the row above |
| `url` | string | yes | | no | Shop origin `https://shop.example.com` (install root). HTTPS required. Daemon SSRF/egress |
| `api_key` | password | yes | | yes | Webservice key (32-char). HTTP Basic user; password empty |
| `io_format` | select | no | `JSON` | no | Read format. Always send `output_format=JSON` when JSON. Writes still XML |
| `timeout_seconds` | number | no | `25` | no | Per-request timeout |

Never put `api_key` in the URL (`https://KEY@host/api/` is documented but leaks into logs/proxies). Use the Authorization header only.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `file` only for image create |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include the key |

FUNCTION ports: none.

## Input fields (by operation)

### Shared

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource` | select | yes | Allowlisted resource name |

### `operation = resource_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `display` | string | no | `full` or `[id,name,…]` |
| `filter` | object | no | Map of field → filter expression (`1`, `[saint]%`, `[1\|7]`, `![hubert]`, `>[2000-01-01]`) |
| `sort` | string | no | `[field1_ASC,field2_DESC]` |
| `limit` | string | no | `0,100` (offset,count) |
| `language` | string | no | `1` or `[1\|2]` |
| `date` | boolean | no | `true` to allow date filters (`date=1`) |

### `operation = resource_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |
| `display` | string | no | With `filter` only for listings; for a single id use default full record |

### `operation = resource_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | object | yes (except images) | Record fields. Handler wraps as `<prestashop><{singular}>…` XML. Fetch `/api/{resource}?schema=blank` when unsure |
| `schema` | select | no | If `blank`, GET blank schema first then merge `fields` |
| `sendemail` | boolean | no | For `order_histories` / `order_carriers`: `sendemail=1` |
| `file` | file | when `resource=images` | Platform file ref-id for image upload (not inline bytes) |

### `operation = resource_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |
| `method` | select | no | `PUT` (full) or `PATCH` (partial; `id` + changed fields) |
| `fields` | object | yes | Fields to write (XML). No pretty-print/newlines inside tags |

### `operation = resource_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok` |
| `resource` | string | success | Resource name used |
| `id` | number | get/create/update/delete | Record id |
| `record` | object | get/create/update | JSON object for the singular resource (from `output_format=JSON`) |
| `records` | array | `resource_list` | Array of records or id stubs |
| `schema` | object | when schema fetched | Blank/synopsis schema |
| `error` | string | failure | Mapped error. Never the API key |

PII: `customers`, `addresses`, `orders` include names, emails, phones. Do not log `record` for those resources — log ids only.

## Legal

- PrestaShop software license on the merchant install; developer docs: https://devdocs.prestashop.com/9/webservice/
- Rate limits: **not published** for the webservice. Honor HTTP 429/`Retry-After` if a reverse proxy returns them. Optional `Content-Sha1` / `Local-Content-Sha1` cache (304) from PS 8.0+.
- PII: customer and address resources. Scope the webservice key in the back office (GET/PUT/POST/DELETE per resource). Never place the key in URLs.
- PCI: no card fields on these resources. Cross-ref `docs/catalog/finance-accounting.md`.
- Official-API-first: `/api/` webservice only. No back-office scrape. BO API (Vue admin JSON) is a different surface and is omitted.

## Implementation notes

- JDK HTTP only. Header `Authorization: Basic {base64(api_key + ":")}`. Reads: query `output_format=JSON`. Writes: `Content-Type: text/xml` (or `application/xml`) with `<prestashop>` root. Do not send JSON bodies.
- HTTPS only. Timeout 25s. Body cap 8 MiB.
- Reject unknown `resource` values with `{ error }` and no HTTP call.
- HTTP 401 (bad key), 404, 405 (method not allowed for that key/resource), 400 (XML validation), 304 (unchanged with SHA1 header), 429, 5xx → `{ error }` on OUTPUT except 304 may be `{ status: ok, not_modified: true }`. Never throw past dispatch.
- `PUT` validation requires a full record; `PATCH` allows partial. Do not pretty-print XML.
- Image create: read the platform `file` ref, multipart upload per official images resource — never inline bytes on the port.
- Never log `api_key`, Basic auth, or customer/order payloads. Log method, resource, id, and HTTP status.
