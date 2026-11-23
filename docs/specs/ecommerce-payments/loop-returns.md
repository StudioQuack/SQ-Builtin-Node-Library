# Loop Returns

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/loop-returns/`
- **Researched:** 2026-08-20
- **Official API:** Loop Returns API **v1** (`https://api.loopreturns.com/api/v1`)
- **Docs:** https://docs.loopreturns.com/api-reference/getting-started (canonical as of 2026-08-20; `developers.loopreturns.com` is the older hostname). Auth: https://docs.loopreturns.com/api-reference/authentication. Webhooks: https://docs.loopreturns.com/webhook-reference/getting-started
- **Auth:** api_key — secret binding: `api_key` (header `X-Authorization`). Webhook HMAC secret: `webhook_secret`

## Scope

One node for Loop’s Shopify-centric returns platform (also used on other commerce stacks via Loop’s commerce APIs). The customer owns the Loop shop and API key. Starting verbs: list/get/process returns, plus `trigger` for Loop webhooks on **this same node** (not a second node).

Do not scrape the Loop Admin or customer portal. Card capture is not this node — refunds/exchanges are Loop outcomes against the merchant’s commerce provider.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `return_list` | GET | `/warehouse/return/list` | Paginated detailed returns in a time window (max 120 days; default last 24 h if `from`/`to` omitted) |
| `return_get` | GET | `/warehouse/return/details` | One return by `return_id`, commerce `order_id`, or `order_name` |
| `return_process` | POST | `/warehouse/return/{id}/process` | Queue processing (archive + fulfill remaining outcomes). **200 means queued**, not finished |
| `trigger` | inbound POST | Loop webhook → StudioQuack ingress | Listen for Loop topics (`return.created`, `return.updated`, `return.closed`, `label.*`, `restock.requested`, `giftcard.requested`, `shipment.processed`, …) |

Base origin: `https://api.loopreturns.com/api/v1`. Programmatic webhook CRUD is `POST/GET/PATCH/DELETE /webhooks` and currently requires **OAuth 2.0** + `developer_tools` scope; merchants may also create subscriptions in Loop Admin. This node’s `trigger` verifies inbound payloads; optional `webhook_register` is out of starting ops.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `return_list` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Loop API key (`X-Authorization`). Needs `returns` scope for list/get/process. Never on INPUT |
| `webhook_secret` | password | no | empty | yes | HMAC secret from Loop Developer Tools. Required for `trigger` verify |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Return payloads include shopper PII — do not log |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` / `webhook_secret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = return_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | no | Window start (ISO). With `to`, span ≤ 120 days |
| `to` | string | no | Window end |
| `state` | string | no | Filter. Default provider behavior: open, closed, expired |
| `page` | number | no | Pagination page |
| `page_size` | number | no | Page size as Loop documents |

### `operation = return_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `return_id` | number | conditional | Loop return id (one of return_id / order_id / order_name) |
| `order_id` | number | conditional | Commerce (e.g. Shopify) order id |
| `order_name` | string | conditional | Commerce order name |
| `currency_type` | string | no | `shop` \| `presentment` |

### `operation = return_process`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `return_id` | number | yes | Path `{id}` |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `headers` | object | yes | Inbound headers including `X-Loop-Signature` |
| `raw_body` | string | yes | **Exact** raw POST body for HMAC (do not re-serialize JSON) |
| `topic` | string | no | Expected Loop topic for filtering |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx / verified trigger | `ok` |
| `returns` | array | `return_list` | Return summaries (ids, state, order refs). Strip emails from logs |
| `return` | object | `return_get` | Full return JSON capped by body limit |
| `queued` | boolean | `return_process` | Provider queued processing |
| `event` | object | `trigger` | `{ topic, trigger, return_id, … }` — no raw PII in logs |
| `error` | string | failure | Actionable, no secrets |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- Docs / ToS: merchant Loop contract; API: https://docs.loopreturns.com/api-reference/getting-started
- Rate limit: **300 requests per minute per API key**. HTTP 429 → pause; do not tight-loop.
- Shopper name, email, address, and return photos are PII. GDPR/CCPA: merchant is controller; Loop is processor per their DPA.
- Shopify-centric: also obey Shopify API / app terms when the shop is on Shopify.
- Official-API-first: no scrape of admin.loopreturns.com or the returns portal.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Header: `X-Authorization: {api_key}`, `Accept: application/json`. Empty key → `{ error }` — no HTTP.
- Response body cap **1_000_000** bytes. Timeout default 30 s (floor 1).
- `trigger`: HMAC-SHA256 of **raw body** with `webhook_secret`, Base64; compare to `X-Loop-Signature` (constant-time). Mismatch → `{ error }` HTTP 401 to Loop conceptually; do not process. Loop expects 200/202/204 within **10 s**. Ten consecutive failures deactivate the webhook in Loop Admin.
- Logs: operation, return id, HTTP status, sizes — never API key, signature, shopper email, or return body text.
