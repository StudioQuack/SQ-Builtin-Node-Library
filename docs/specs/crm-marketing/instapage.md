# Instapage

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/instapage/`
- **Researched:** 2026-08-20
- **Official API:** Instapage Public API **v1** (`api.instapage.com`). Changelog newest entry **2026-06-10**.
- **Docs:** https://devdocs.instapage.com/
- **Auth:** bearer personal API token — secret binding: `api_token`. Header `Authorization: Bearer`.

## Scope

Official REST for workspace pages and **form submissions (leads)**, plus UI-configured form-submit webhooks. Help Center “Public API” (token in-app) + `devdocs.instapage.com`.

**Do not scrape the page builder, published HTML, or screenshot heatmaps.** JSON import/export of page structure (`/pages/{id}/json`) is an official backup endpoint — **out of catalog scope**; do not add it to this node.

Catalog verbs: `page.list`, `lead.list`, `trigger.listen`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `page.list` | GET | `/v1/workspaces/{workspace_id}/pages` | Pages in a workspace. Query `updatedAfter` / `updatedBefore` (added 2026-06-10). |
| `lead.list` | POST | `/v1/workspaces/{workspace_id}/submissions` | Form submissions. **POST-with-body**, not GET. Max 100 page ids; 100 submissions per page token. |
| `trigger.listen` | n/a | Instapage Integrations webhook URL (Help: Integrating with Webhooks) | Form Submit webhook → SQ ingress |

No unofficial builder scrape. Webhook localhost URLs are rejected by Instapage.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `page.list` | no | |
| `api_token` | password | yes for REST | empty | yes | Personal API token (same permission as the generating user; includes all accessible workspaces) |
| `workspace_id` | number | yes for REST | empty | no | Default workspace; overridable on input |
| `timeout_seconds` | number | no | `25` | no | Instapage webhook handler SLA on **their** side is 20 s; we still cap 25 s for REST |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Workspace/page filters or webhook body |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Lead `fields` are PII |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = page.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `workspace_id` | number | no | Override config |
| `page` | number | no | Page index as documented |
| `updated_after` | string | no | Query `updatedAfter` |
| `updated_before` | string | no | Query `updatedBefore` |
| `is_deleted` | boolean | no | Include deleted if API supports `isDeleted` |
| `with_group_id` | boolean | no | Query `withGroupId` |

### `operation = lead.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `workspace_id` | number | no | Override config |
| `pages` | array | no | Up to **100** page ids. Empty = workspace default as documented. |
| `timeframe_start` | number | no | Unix seconds |
| `timeframe_end` | number | no | Unix seconds |
| `next_page_token` | string | no | `meta.nextPageToken` |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | yes when from ingress | Instapage form-submit body (`ip`, `page_id`, `page_name`, `page_url`, mapped fields) |
| `authorization` | string | no | Bearer they configured on the webhook (compare to instance secret; never log) |

Registration is in Instapage (Settings → Integrations / page webhook). Optional bearer on the webhook is operator-configured. Respond 2xx within **20 seconds** or Instapage blocks thank-you/redirect.

## Output fields (by operation)

### `page.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `pages` | array | 2xx | Page summaries (id, title, groupId, …) as returned |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | Parse `Retry-After`. Limit **200 req/min** plus plan daily cap. |

### `lead.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | array | 2xx | `{ id, pageId, variationName, variationCustomName, createdAt, fields }` |
| `meta` | object | 2xx | `nextPageToken`, `limit` |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | accepted | `ok` |
| `lead` | object | | Normalized: `page_id`, `page_name`, `page_url`, `ip`, `fields` |
| `error` | string | bad auth / empty | |

## Legal

- Dev docs: https://devdocs.instapage.com/
- Public API help: https://help.instapage.com/hc/en-us/articles/27634951739287-Public-API
- Form webhooks: https://help.instapage.com/hc/en-us/articles/360001722428-Integrating-with-Webhooks
- Rate limit: **200 requests per minute** → 429; daily cap in Subscription (account owner).
- Token accesses all workspaces the user can see, including leads. Treat as a full-account secret.
- Leads are personal data. Customer owns the Instapage account. Never log tokens, emails, or raw `fields`.
- Do not crawl `instapage.com` builder or published landing HTML as a substitute for this API.

## Implementation notes

- Base `https://api.instapage.com`. JDK HTTP. JSON. Bearer token.
- `lead.list` **must** be POST JSON `{ pages, timeframe, nextPageToken }`.
- Timeout 25 s. Response cap 2 MiB. TLS verified. `Redirect.NEVER`.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry.
