# Heap

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/heap/`
- **Researched:** 2026-08-20
- **Official API:** Heap server-side HTTP APIs (track / identify / add_user_properties / add_account_properties). Docs last updated 2026-04-01. Contentsquare-owned; Heap ingest host still live.
- **Docs:** https://developers.heap.io/reference/server-side-apis-overview
- **Auth:** `app_id` in JSON body for ingest. User Deletion (`/api/public/v0/*`, Basic `app_id`:`api_key`) is out of catalog — no `api_key` config on this node.

## Scope

Server-side enrich of a Heap environment: custom events, identity merge, user properties, account properties. JDK HTTP POST to Heap ingest. This is **not** the Contentsquare Enrichment API (`enrichment-api.{cloud}.contentsquare.com`) and **not** heap.js.

**Verified live (2026-08-20):** `https://heapanalytics.com/api/*` and EU `https://c.eu.heap-api.com/api/v1/*`. Ingest calls take `app_id` (environment ID of Main Production) in the JSON body. Track requires **either** `identity` **or** `user_id`, not both. Events only attach to identified users.

**Do not build:** heatmap or session-replay export; Contentsquare DXA scrape; client-only heap.js methods (`heap.track` in a browser). User Deletion exists (`POST /api/public/v0/user_deletion`) but is **not** a catalog operation — omit unless a later catalog row adds GDPR delete.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `track` | POST | `/api/track` (US) or `/api/v1/track` (EU) | Custom server event |
| `identify` | POST | `/api/identify` (US) or `/api/v1/identify` (EU) | Merge Heap `user_id` with `identity` |
| `addUserProperties` | POST | `/api/add_user_properties` (US) or `/api/v1/add_user_properties` (EU) | Set user properties by `identity` |
| `addAccountProperties` | POST | `/api/add_account_properties` (US) or `/api/v1/add_account_properties` (EU) | Set account properties (`account_id` or bulk `accounts`, max 1000) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `track` | no | Chooses the row above |
| `app_id` | string | yes | empty | no | Heap environment ID (Main Production). Body `app_id`. |
| `region` | select | no | `heapanalytics.com` | no | Official ingest host interpolated into `baseUrl` `https://{region}`. `heapanalytics.com` (US) or `c.eu.heap-api.com` (EU). |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Identities and property maps are PII |
| `output` | OUTPUT | `{ status }` or `{ error }` | Heap 200 body is often `{}` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event` | string | yes | Event name. Max 255 chars. Empty → `{ error }`, no HTTP. |
| `identity` | string | one of `identity`/`user_id` | Case-sensitive identity. Max 255. Do not send with `user_id`. |
| `user_id` | string | one of `identity`/`user_id` | Heap SDK user id (decimal string, 0 … 2^53−1). |
| `timestamp` | string | no | ISO-8601. Heap defaults to now. |
| `properties` | object | no | String/number values. Keys and values &lt; 1024 chars (Heap limit). |

### `operation = identify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | string | yes | Heap SDK user id |
| `identity` | string | yes | Identity to attach. Max 255. |
| `timestamp` | string | no | ISO-8601 |

### `operation = addUserProperties`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `identity` | string | yes | Target identity (server-side API requires identity, not anonymous Heap id) |
| `properties` | object | yes | Key-value map; values string or number, &lt; 1024 chars each |

### `operation = addAccountProperties`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account_id` | string | yes unless `accounts` | Single account id |
| `properties` | object | yes unless `accounts` | Properties for that account |
| `accounts` | array | yes unless `account_id` | Bulk: `{ account_id, properties }[]`. Max **1000**. |

## Output fields (by operation)

All ingest ops share this shape (Heap returns `{}` on success).

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `http_status` | number | always on HTTP | Provider status |
| `error` | string | failure | No `app_id`, no identities, no property values |
| `retry_after_seconds` | number | 429 / 5xx / timeout | `60` / `30` |

## Legal

- Server-side overview: https://developers.heap.io/reference/server-side-apis-overview
- Track: https://developers.heap.io/reference/track-1
- Identify: https://developers.heap.io/reference/identify-1
- Using Identify (identity vs properties): https://developers.heap.io/docs/using-identify
- Add User Properties: https://developers.heap.io/reference/add-user-properties
- Add Account Properties: https://developers.heap.io/reference/add-account-properties
- Bulk accounts: https://developers.heap.io/reference/bulk-add-account-properties (same POST; max 1000)
- User Deletion (out of catalog): https://developers.heap.io/reference/user-deletion
- Contentsquare ToS (parent): https://contentsquare.com/page-legal-terms-of-service/
- Contentsquare Developer Terms: https://contentsquare.com/legal-developer-terms/
- Customer owns the Heap workspace. StudioQuack is the HTTP client. Minimize properties (no government IDs, health data). Never log `identity`, emails, or `api_key`.
- EU residency: `region=eu` is mandatory when the Heap project is EU-hosted; US host will not accept that data.

## Implementation notes

- Overview last updated **2026-04-01** on developers.heap.io (fetched 2026-08-20).
- JDK HTTP POST, `Content-Type: application/json`. Inject `app_id` from config into the body. **Never** put `api_key` on ingest bodies.
- US base `https://heapanalytics.com`. EU base `https://c.eu.heap-api.com` with `/api/v1/{verb}` paths as documented per method. Do not mix hosts.
- Timeout 25 s. Body cap 256 KiB. TLS verified. `Redirect.NEVER`.
- If both `identity` and `user_id` on `track` → `{ error }` without calling Heap.
- 4xx/5xx/timeout → `{ error }` on OUTPUT. No inner retry loop. Map 429/5xx to `retry_after_seconds`.
- Logs: operation, HTTP status, byte sizes. Not event names that include emails, not property maps.
- Property key/value length: Heap documents &lt; 1024 characters; reject oversized maps locally with `{ error }`.
- Bulk `accounts` length: reject `> 1000` locally. Empty `properties` on `addUserProperties` → `{ error }`.
- User Deletion (`POST /api/public/v0/auth_token` then `/api/public/v0/user_deletion`) is official (Basic `app_id`:`api_key`) but **out of catalog**. Do not add a delete operation unless the catalog row gains one.
- Heap.js client methods and Contentsquare DXA/enrichment hosts are out of scope.
