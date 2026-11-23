# Webflow

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/webflow/`
- **Researched:** 2026-08-20
- **Official API:** Webflow **Data API v2** (`https://api.webflow.com/v2`)
- **Docs:** https://developers.webflow.com/data/reference/rest-introduction — CMS https://developers.webflow.com/data/reference/cms — auth https://developers.webflow.com/data/reference/authentication — webhooks https://developers.webflow.com/data/docs/working-with-webhooks — forms list-submissions https://developers.webflow.com/data/reference/forms/form-submissions/list-submissions.mdx
- **Auth:** oauth2 / siteToken — secret bindings: `siteToken`, `clientSecret`, `accessToken`

## Scope

One node for Webflow **CMS collection items** and **site forms**. All calls use **`https://api.webflow.com/v2/...`**. Site token (one site) or OAuth / workspace token (multi-site). Designer APIs are out of scope. Form submissions are respondent PII. **Customer owns the Webflow site/workspace.** Do not scrape the Designer.

Item ops target **staged** CMS items (`/collections/{collection_id}/items`). Publishing is not a catalog verb; omit unless a later row adds it.

`trigger.form` and `trigger.listen` both `POST /v2/sites/{site_id}/webhooks`. `trigger.form` forces `triggerType=form_submission`. `trigger.listen` allows the documented trigger types (default `form_submission`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `site.list` | GET | `/v2/sites` | Sites the token can see |
| `item.list` | GET | `/v2/collections/{collection_id}/items` | Staged items (`limit` max 100) |
| `item.get` | GET | `/v2/collections/{collection_id}/items/{item_id}` | One item |
| `item.create` | POST | `/v2/collections/{collection_id}/items` | Create staged item(s) |
| `item.update` | PATCH | `/v2/collections/{collection_id}/items/{item_id}` | Update staged item |
| `item.delete` | DELETE | `/v2/collections/{collection_id}/items/{item_id}` | Delete staged item |
| `form.list` | GET | `/v2/sites/{site_id}/forms` | Forms on a site |
| `form.submission.list` | GET | `/v2/sites/{site_id}/forms/{form_id}/submissions` | Submissions for a form. Site-wide: `GET /v2/sites/{site_id}/form_submissions` |
| `trigger.form` | POST | `/v2/sites/{site_id}/webhooks` | `form_submission` → SQ ingress |
| `trigger.listen` | POST | `/v2/sites/{site_id}/webhooks` | Any documented `triggerType` → SQ ingress |

v1 (`api.webflow.com` without `/v2`) is retired for this node.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `site.list` | no | Chooses the row above |
| `auth` | select | yes | `siteToken` | no | `siteToken` \| `oauth2` |
| `siteToken` | password | no | | yes | Site token (`Authorization: Bearer`). Required if `auth=siteToken` |
| `accessToken` | password | no | | yes | OAuth access token if `auth=oauth2` |
| `clientId` | string | no | | no | OAuth app |
| `clientSecret` | password | no | | yes | OAuth secret |
| `siteId` | string | no | | no | Default site id |
| `collectionId` | string | no | | no | Default CMS collection id |
| `timeout_seconds` | number | no | `25` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | CMS images/files: type `file` (ref-id) |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens; do not log `formResponse` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = site.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Token-scoped |

### `operation = item.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection_id` | string | yes* | Else config `collectionId` |
| `cmsLocaleId` | string | no | Locale; comma-separated for several |
| `limit` | number | no | Max **100** |
| `offset` | number | no | Pagination |

### `operation = item.get` | `item.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection_id` | string | yes* | Else config |
| `item_id` | string | yes | Item id |

### `operation = item.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection_id` | string | yes* | Else config |
| `fieldData` | object | yes | Collection field slug → value (schema from Get Collection) |
| `cmsLocaleId` | string | no | Locale |
| `isArchived` / `isDraft` | boolean | no | Staged flags as documented |

### `operation = item.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `collection_id` | string | yes* | Else config |
| `item_id` | string | yes | Item id |
| `fieldData` | object | yes | Partial field map |
| `cmsLocaleId` | string | no | Locale |

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `site_id` | string | yes* | Else config `siteId` |
| `limit` / `offset` | number | no | Pagination (max 100) |

### `operation = form.submission.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `site_id` | string | yes* | Else config |
| `form_id` | string | no | Form id. If omitted, use site-wide `/form_submissions` |
| `elementId` | string | no | `formElementId` filter on site-wide list (component instances share it) |
| `limit` / `offset` | number | no | Max 100 |

### `operation = trigger.form`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `site_id` | string | yes* | Else config |
| `url` | string | yes | HTTPS SQ message-event URL |
| `filter_name` | string | no | Only this form’s display name |

`triggerType` is always `form_submission`. Scope `forms:read`.

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `site_id` | string | yes* | Else config |
| `url` | string | yes | HTTPS SQ message-event URL |
| `triggerType` | string | no | Default `form_submission`. Allowed: `form_submission`, `site_publish`, `page_created`, `page_metadata_updated`, `page_deleted`, `ecomm_new_order`, `ecomm_order_changed`, `ecomm_inventory_changed`, `collection_item_created`, `collection_item_changed`, `collection_item_deleted`, `collection_item_published`, `collection_item_unpublished`, `comment_created` |
| `filter_name` | string | no | Only for `form_submission` |

## Output fields (by operation)

### `site.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `sites` | object[] | success | `id`, `displayName`, `shortName` |
| `error` | string | failure | |

### `item.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | object[] | success | Staged items + `fieldData` |
| `pagination` | object | success | `limit`, `offset`, `total` |
| `error` | string | failure | |

### `item.get` | `item.create` | `item.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `item` | object | success | Item including `id`, `fieldData` |
| `error` | string | failure | |

### `item.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `item_id` | string | success | Deleted id |
| `error` | string | failure | |

### `form.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `forms` | object[] | success | `id`, `displayName`, `formElementId` |
| `error` | string | failure | |

### `form.submission.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `formSubmissions` | object[] | success | `id`, `dateSubmitted`, `formResponse` (**PII**) |
| `pagination` | object | success | `limit`, `offset`, `total` |
| `error` | string | failure | |

### `trigger.form` | `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `webhookId` | string | success | Webhook id |
| `triggerType` | string | success | Registered type |
| `url` | string | success | Callback URL |
| `error` | string | failure | |

## Legal

- Data API intro: https://developers.webflow.com/data/reference/rest-introduction
- Rate limits (per API key, per minute): Starter/Basic **60**; CMS/eCommerce/Business **120**; Enterprise custom. HTTP **429** + `Retry-After` (typically 60 s). Headers `X-RateLimit-Limit` / `X-RateLimit-Remaining`.
- Form submissions and CMS records may contain PII. Customer owns the Webflow site and consent. Never log tokens or `formResponse` / field values.
- Webhook payloads for `form_submission` include field values — treat ingress like PII.
- Official-API-first: `api.webflow.com/v2` only. Do not scrape the Designer.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Webflow SDK.
- Header: `Authorization: Bearer {siteToken|accessToken}`, `Accept: application/json`.
- Empty token → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }` from `Retry-After` else 60. 5xx/timeout → 30. Do not tight-loop. Prefer webhooks over polling.
- CMS:validate `fieldData` size; file fields are platform `file` refs resolved just-in-time.
- Logs: operation, site_id, collection_id, status, counts — never tokens or submission bodies.
- **DO NOT BUILD** in this task.
