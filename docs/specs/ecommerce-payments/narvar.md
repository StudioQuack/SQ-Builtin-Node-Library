# Narvar

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/narvar/`
- **Researched:** 2026-08-20
- **Official API:** Narvar Orders / Shipment APIs (`api/v1`) — **enterprise / partner-gated**
- **Docs:** https://support.narvar.com/hc/en-us (Hub; Cloudflare/login gated). Public path summary: https://learn.microsoft.com/en-us/connectors/narvar/ (Microsoft connector, updated 2025-04-25). Product site: https://www.narvar.com/
- **Auth:** HTTP Basic (`Account ID` + `Auth Tokens`) — secret binding: `api_key` (password) plus `account_id`; `retailer_moniker` is the Hub retailer slug, not a secret

## Scope

One node for the merchant’s **Narvar Hub** post-purchase products (Track / Notify / Returns). The customer owns the Narvar contract. There is **no public self-serve developer portal** as of 2026-08-20: `support.narvar.com` is Hub-authenticated; OpenAPI used by the Microsoft Power Platform connector documents production host `https://api.narvar.com/api/v1` with Basic auth.

**DO NOT BUILD** a hardcoded public integration (no self-serve Narvar developer portal). **BUILD only** with customer-provided `base_url` + Hub credentials. Do not hard-code a guessed Returns host. If credentials are missing, return `{ error }` and do not call Narvar.

`order_push` and `shipment_push` match the connector’s documented Orders API. `return_create` is a Narvar product capability (Returns) whose **public HTTP path is unverified** without Hub docs — do not invent a Returns URL.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `order_push` | POST | `/orders` | Create or update an order (`order_info`) so Track/Notify can render it |
| `shipment_push` | PUT | `/orders/{order_number}/shipments` | Attach or update shipment / tracking on an existing order |
| `return_create` | POST | **customer-supplied** `returns_path` only | Open a return in Narvar Returns. **Unverified public path** — skip HTTP unless `returns_path` is set |

Default origin: `https://api.narvar.com/api/v1` (overridable via `base_url`). Some retailers are routed by `retailer_moniker` in Hub; send it as Hub documents (header or path). Do not scrape Hub HTML.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `order_push` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Narvar **Auth Tokens** (Basic password). Never on INPUT, never logged |
| `account_id` | password | yes | empty | yes | Narvar **Account ID** (Basic username) |
| `retailer_moniker` | string | yes | empty | no | Hub retailer slug (e.g. brand key). Used only as Hub documents |
| `base_url` | string | no | `https://api.narvar.com/api/v1` | no | Customer Hub origin. HTTPS only; SSRF-guarded |
| `returns_path` | string | no | empty | no | Absolute or origin-relative path for `return_create` from Hub docs. Empty → no HTTP |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Order/shipment payloads may contain buyer PII — do not log bodies |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` / `account_id` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = order_push`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_info` | object | yes | Narvar `order_info` JSON (order_number, order_date, items, customer, billing/shipping as Hub schema). Prefer ids over emails in logs |
| `order_number` | string | no | Alias if `order_info.order_number` omitted |

### `operation = shipment_push`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_number` | string | yes | Path `{OrderNumber}` |
| `shipments` | object | yes | Shipment record(s): carrier, tracking_number, items, destination as Hub schema |

### `operation = return_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `return` | object | yes | Return payload **exactly as Hub documents**. No HTTP if `returns_path` empty |
| `order_number` | string | no | Correlation id for output only |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `order_number` | string | success | Echo / provider order id |
| `narvar_status` | string | success | Provider `status` field when present |
| `messages` | array | success | Provider `messages[]` (code + message), truncated |
| `error` | string | failure | Actionable, no secrets |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- Hub / terms via merchant contract and https://www.narvar.com/. Privacy: https://corp.narvar.com/privacy-policy
- API access is **enterprise / partner-gated**. StudioQuack does not accept Narvar terms on the merchant’s behalf.
- Microsoft connector throttle note: 100 calls / 60 s per connection (not a Narvar SLA). Honor provider `Retry-After` when present.
- Buyer name, email, phone, and address are PII. Never log passwords, Auth Tokens, or order PII beyond ids/status/sizes.
- Official-API-first: no scrape of Hub, Track pages, or carrier sites.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. HTTPS only.
- Auth: `Authorization: Basic base64(account_id + ":" + api_key)`. Empty credentials → `{ error }` — no HTTP.
- Headers: `Accept: application/json`, `Content-Type: application/json`.
- Response body cap **1_000_000** bytes. Timeout default 30 s (floor 1).
- 401/403 → `{ error }` “Narvar rejected credentials or retailer access” — no retry. 429 → `retry_after_seconds: 60`. Do not auto-retry inside the handler.
- `return_create` with empty `returns_path` → `{ error: "Narvar Returns path not provided. Set returns_path from Hub docs." }` — **DO NOT BUILD** a guessed Returns URL.
- Logs: operation, `retailer_moniker`, HTTP status, body size — never tokens, emails, addresses, or tracking payloads.
