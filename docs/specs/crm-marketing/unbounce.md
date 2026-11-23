# Unbounce

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/unbounce/`
- **Researched:** 2026-08-20
- **Official API:** Unbounce REST API **v0.4** (`Accept: application/vnd.unbounce.api.v0.4+json`)
- **Docs:** https://developer.unbounce.com/getting_started/ — reference https://developer.unbounce.com/api_reference/
- **Auth:** api_key (HTTP Basic, key as username, empty password) — secret binding: `apiKey`. OAuth 2.0 exists but is **not self-serve** (register via Unbounce form); default this node to API key.

## Scope

One node for Unbounce landing-page **pages, form fields, and leads**. Origin **`https://api.unbounce.com`**. v0.4 is the current version (v0.3 removed May 2018). API access is **request-gated** (support enables “API Access”; then keys are created in Manage Account). API keys act as **account administrators**. Page leads are **PII**. **Customer owns the Unbounce account.** Do not scrape the Smart Builder. Do not ship page HTML/CSS/JS sub-resources.

`GET /pages/{page_id}/leads` is the lead collection (sub-account-wide lead list was **removed** in v0.4). There is **no documented REST “create webhook”** endpoint in v0.4; Unbounce page webhooks are configured in the product UI. `trigger.listen` therefore **polls** new leads (`from`/`to`) for the page. Operators may *also* paste the StudioQuack message-event URL into Unbounce’s UI webhook settings (not registered by this node).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `page.list` | GET | `/pages` | All pages the credential can see |
| `page.get` | GET | `/pages/{page_id}` | One page |
| `form.list` | GET | `/pages/{page_id}/form_fields` | Form fields on a page |
| `lead.list` | GET | `/pages/{page_id}/leads` | Leads for a page (PII) |
| `lead.get` | GET | `/pages/{page_id}/leads/{lead_id}` | One page-scoped lead. Fallback `GET /leads/{lead_id}` if page id unknown |
| `trigger.listen` | GET | `/pages/{page_id}/leads` | Poll leads since `from` (same list endpoint) |

Lead **deletion** (`/lead_deletion_request`) is GDPR-erasure, not a catalog verb — omit unless a later catalog row asks for it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `page.list` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | HTTP Basic username; password empty |
| `subdomain` | string | no | | no | Informational (Unbounce site subdomain); **not** the API host |
| `pageId` | string | no | | no | Default page UUID |
| `timeout_seconds` | number | no | `25` | no | Connect + read. Floor 1 s. |

`subdomain` is **not** interpolated into `api.unbounce.com`. Catalog listed it for operator context only.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `apiKey` or lead `form_data` in logs |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = page.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Page size (provider default 50; max 1000) |
| `offset` | number | no | Skip |
| `sort_order` | string | no | `asc` \| `desc` |
| `with_stats` | boolean | no | Include A/B stats |
| `from` / `to` | string | no | Date window if listing by activity |

### `operation = page.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes* | Else config `pageId` |

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes* | Else config |
| `include_sub_pages` | boolean | no | Include variant/sub-page fields |

### `operation = lead.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes* | Else config |
| `limit` / `offset` | number | no | Pagination |
| `sort_order` | string | no | `asc` \| `desc` |
| `from` / `to` | string | no | Created window |

### `operation = lead.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lead_id` | string | yes | Lead id |
| `page_id` | string | no | Prefer page-scoped path when known |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_id` | string | yes* | Else config |
| `from` | string | yes | Inclusive lower bound (ISO-8601). Operator/workflow stores watermark |
| `to` | string | no | Upper bound |
| `limit` | number | no | Cap this poll (default 50, max 1000) |

Not a long-running listener. One HTTP list per dispatch. BPMN timer + watermark for “new leads”.

## Output fields (by operation)

### `page.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `pages` | object[] | success | `id`, `name`, `state`, `url`, related `leads`/`form_fields` hrefs |
| `metadata` | object | success | Pagination / location |
| `error` | string | failure | |

### `page.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `page` | object | success | Page record |
| `error` | string | failure | |

### `form.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `form_fields` | object[] | success | Field id, name, type |
| `error` | string | failure | |

### `lead.list` | `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `leads` | object[] | success | `id`, `page_id`, `created_at`, `form_data` (**PII**) |
| `count` | number | success | Page count / metadata count |
| `error` | string | failure | |

### `lead.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `lead` | object | success | Full lead including `form_data` (**PII**) |
| `error` | string | failure | |

406 if `Accept` header omitted/wrong.

## Legal

- Getting started / auth: https://developer.unbounce.com/getting_started/
- API reference: https://developer.unbounce.com/api_reference/
- Rate limit: **500 requests per minute per user account and IP**. HTTP **429** when exceeded.
- Access is not anonymous: request API Access, then create keys. Keys are admin-equivalent — treat as high-value secrets.
- Page leads are PII. Customer owns the Unbounce account and consent (landing-page forms). Never log `apiKey` or `form_data`.
- UI webhooks (if used) are **unsigned** per operator reports — prefer polling this API; if UI webhooks are used, treat ingress as unauthenticated-from-Unbounce and rely on SQ env API key + allowlist.
- Official-API-first: `api.unbounce.com` + v0.4 Accept only.

## Implementation notes

- JDK `HttpClient` only. Host `api.unbounce.com`. TLS verified. `Redirect.NEVER`.
- Headers: `Accept: application/vnd.unbounce.api.v0.4+json`, `Authorization: Basic base64(apiKey + ":")`.
- Empty key → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds: 60 }`. 5xx/timeout → 30 s. Do not tight-loop.
- Logs: operation, page_id, status, counts — never form field values or the API key.
- **DO NOT BUILD** in this task.
