# Zoho CRM

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/zoho-crm/`
- **Researched:** 2026-08-20
- **Official API:** Zoho CRM REST **v8** (current documented version; catalog “v3+” is satisfied — **pin v8**, do not default to v3)
- **Docs:** OAuth https://www.zoho.com/crm/developer/docs/api/v8/oauth-overview.html — tokens https://www.zoho.com/crm/developer/docs/api/v8/access-refresh.html — multi-DC https://www.zoho.com/crm/developer/docs/api/v8/multi-dc.html — records https://www.zoho.com/crm/developer/docs/api/v8/get-records.html — insert https://www.zoho.com/crm/developer/docs/api/v8/insert-records.html — upsert https://www.zoho.com/crm/developer/docs/api/v8/upsert-records.html — credits https://www.zoho.com/crm/developer/docs/api/v8/api-limits.html — notifications https://www.zoho.com/crm/developer/docs/api/v8/notifications/overview.html
- **Auth:** **OAuth 2.0 only** (auth-token dead) — secret bindings: `clientSecret`, `refreshToken`, `accessToken`

## Scope

One node for Zoho CRM records. **Pick the data center** (`dataCenter`); hosts are not interchangeable. Default **US**. Authorization header is **`Zoho-oauthtoken {access_token}`** (not `Bearer`). Access tokens last **~1 hour**; refresh tokens until revoked. **API credits** are a rolling 24-hour pool (not simple RPS). **Customer owns the Zoho org.** Records are PII. Do not scrape crm.zoho.com.

**Picked DC: US** (`https://www.zohoapis.com/crm/v8`). Operator overrides via `dataCenter`.

## Data centers (official accounts hosts)

| `dataCenter` | Accounts (OAuth) | API origin |
|---|---|---|
| `US` (default) | `https://accounts.zoho.com` | `https://www.zohoapis.com` |
| `EU` | `https://accounts.zoho.eu` | `https://www.zohoapis.eu` |
| `IN` | `https://accounts.zoho.in` | `https://www.zohoapis.in` |
| `AU` | `https://accounts.zoho.com.au` | `https://www.zohoapis.com.au` |
| `CN` | `https://accounts.zoho.com.cn` | `https://www.zohoapis.com.cn` |
| `JP` | `https://accounts.zoho.jp` | `https://www.zohoapis.jp` |
| `CA` | `https://accounts.zohocloud.ca` | **Use token `api_domain`** (v8 multi-DC page lists the CA *accounts* host only; do not guess) |

Official token docs also list **SA** (`https://accounts.zoho.sa`) — not a catalog `dataCenter` value. If `api_domain` returns a Saudi or other host, use it; do not invent a catalog row.

**Always prefer `api_domain` from the OAuth token JSON** over this table. Multi-DC examples still show `/crm/v7/` in sample URLs — **this node uses `/crm/v8/`**. Client id is shared; client secret **may differ per DC**.

## Module map (`operation` prefix → API name)

| Prefix | `{module}` path |
|---|---|
| `account` | `Accounts` |
| `contact` | `Contacts` |
| `deal` | `Deals` |
| `lead` | `Leads` |
| `product` | `Products` |
| `invoice` | `Invoices` |
| `quote` | `Quotes` |
| `salesOrder` | `Sales_Orders` |
| `purchaseOrder` | `Purchase_Orders` |
| `vendor` | `Vendors` |

Inventory modules use line-item subforms (`Quoted_Items`, `Invoiced_Items`, `Ordered_Items`, `Purchased_Items`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `{mod}.create` | POST | `/crm/v8/{module}` | Insert ≤100 records |
| `{mod}.upsert` | POST | `/crm/v8/{module}/upsert` | Insert or update on duplicate fields |
| `{mod}.get` | GET | `/crm/v8/{module}/{id}` | One record |
| `{mod}.list` | GET | `/crm/v8/{module}` | Page records (`per_page` max 200) |
| `{mod}.update` | PUT | `/crm/v8/{module}` or `/{id}` | Update ≤100 |
| `{mod}.delete` | DELETE | `/crm/v8/{module}?ids=` | Delete ≤100 |
| `lead.fields` | GET | `/crm/v8/settings/fields?module=Leads` | Lead field metadata |
| `trigger.listen` | POST | `/crm/v8/actions/watch` | Notification channel → SQ ingress |

`{mod}` is every prefix in the map. Catalog upsert exists for account, contact, deal, lead, product, invoice, vendor (not quote / salesOrder / purchaseOrder).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `lead.list` | no | Chooses the row above |
| `dataCenter` | select | yes | `US` | no | DC table above |
| `clientId` | string | yes | | no | Connected app client id |
| `clientSecret` | password | yes | | yes | Per-DC secret as generated |
| `refreshToken` | password | yes | | yes | Long-lived refresh |
| `accessToken` | password | no | | yes | Optional cached access; refresh if 401 |
| `soid` | string | no | | no | Zoho org / One id when the token request requires it |
| `apiVersion` | string | no | `v8` | no | Path version; do not silently fall back to v3 |
| `timeout_seconds` | number | no | `30` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Attachments: type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens; do not log record field values |

FUNCTION ports: none.

## Input fields (by operation)

### `{mod}.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data` | object[] | yes | Records (1–100). Keys are Zoho **API names** (`Last_Name`, `Deal_Name`, …). System-mandatory fields per module (e.g. Lead `Last_Name`) |
| `trigger` | string[] | no | Workflow triggers as documented |

### `{mod}.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data` | object[] | yes | 1–100 records |
| `duplicate_check_fields` | string[] | no | Override default unique fields (Lead Email, Account Account_Name, …) |

### `{mod}.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Record id |
| `fields` | string | no | Comma API names (max 50) |

### `{mod}.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | string | yes | Comma API names (required when listing; max 50) |
| `per_page` | number | no | Default/max **200** |
| `page` | number | no | 1–10 without `page_token` (first 2000) |
| `page_token` | string | no | `next_page_token` beyond 2000 (max 100_000) |
| `sort_by` / `sort_order` | string | no | `id` / `Created_Time` / `Modified_Time`; `asc`/`desc` |
| `cvid` | string | no | Custom view id (not with `sort_by`) |
| `ids` | string | no | Comma ids |
| `converted` | string | no | Leads: `true`/`false`/`both` |

### `{mod}.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | no | Single-record path if set |
| `data` | object[] | yes | Records with `id` when using collection PUT |

### `{mod}.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | string | yes | Comma ids (max 100) |

### `lead.fields`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | `module=Leads`. Optional later: other modules via a `module` field if needed |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `notify_url` | string | yes | HTTPS SQ message-event URL |
| `channel_id` | string | yes | Unique channel id |
| `events` | string[] | yes | e.g. `Leads.create`, `Contacts.edit`, `Deals.delete`, `users.all` |
| `token` | password | no | Verification token Zoho echoes (secret) |
| `notify_on_related_action` | boolean | no | Related-record events |

## Output fields (by operation)

### `{mod}.create` | `{mod}.upsert` | `{mod}.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object[] | success | Per-record `code`, `status`, `details.id` |
| `credits_remaining` | number | when header present | `X-API-CREDITS-REMAINING` |
| `error` | string | failure | Zoho status/code; no token |

### `{mod}.get` | `{mod}.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object[] | success | Records (**PII**) |
| `info` | object | list | `per_page`, `count`, `more_records`, `next_page_token` |
| `credits_remaining` | number | when header present | |
| `error` | string | failure | |

### `{mod}.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object[] | success | Delete results per id |
| `error` | string | failure | |

### `lead.fields`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `fields` | object[] | success | API names, types, mandatory |
| `error` | string | failure | |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `watch` | object | success | Channel / expiry |
| `error` | string | failure | Never return notify `token` |

Zoho may return HTTP 200 with `data[].status=error` — treat as `{ error }` if all failed.

## Legal

- OAuth overview: https://www.zoho.com/crm/developer/docs/api/v8/oauth-overview.html
- Credits (24h rolling): Free **5,000**; Standard/Starter 50,000+(users×250) cap 100,000; Professional 50,000+(×500) cap 3,000,000; Enterprise/One 50,000+(×1,000) cap 5,000,000; Ultimate/CRM Plus 50,000+(×2,000) **unlimited**. Typical call = 1 credit; insert/update/upsert = 1 per 10 records; convert lead = 5; bulk write init = 500. Header `X-API-CREDITS-REMAINING` at ≥50% usage.
- Concurrency: Free 5 … Ultimate 25 simultaneous calls per org/app; sub-concurrency 10 for convert/query/heavy write.
- `TOO_MANY_REQUESTS` → `{ error }` with backoff. Do not burn credits in a retry storm.
- HIPAA-sensitive fields may return null when API restrict is on.
- Customer owns the Zoho org, DC residency, and consent. Never log OAuth tokens or record bodies.
- Official-API-first: `www.zohoapis.{dc}/crm/v8` only. No auth-token, no UI scrape.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Zoho SDK.
- Header: `Authorization: Zoho-oauthtoken {accessToken}`, `Accept: application/json`.
- Refresh: `POST {accounts}/oauth/v2/token` (`grant_type=refresh_token`). Use `api_domain` from the token response when present (overrides DC table if they disagree).
- Empty refresh/access → `{ error }` no CRM HTTP.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- 401 → refresh once, retry once. 429 / TOO_MANY_REQUESTS → `{ error, retry_after_seconds: 30 }` (or `Retry-After`). 5xx/timeout → 30. Do not tight-loop.
- Logs: operation, module, HTTP status, record counts, credit header — never tokens or field values.
- **DO NOT BUILD** in this task.
