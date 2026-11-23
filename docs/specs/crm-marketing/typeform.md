# Typeform

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/typeform/`
- **Researched:** 2026-08-20
- **Official API:** Typeform **Create + Responses + Webhooks** REST (no numeric version in the path; base `https://api.typeform.com`)
- **Docs:** https://developers.typeform.com/developers/get-started/ — Create https://developers.typeform.com/developers/create/ — Responses https://developers.typeform.com/developers/responses/ — Webhooks https://developers.typeform.com/developers/webhooks/ — retrieve responses https://developers.typeform.com/developers/responses/reference/retrieve-responses/
- **Auth:** oauth2 / apiToken — secret bindings: `token`, `clientSecret`, `webhookSecret`

## Scope

One node for Typeform. Lists/gets/creates forms (Create API), pulls submissions (Responses API), and registers HTTPS webhooks that POST into StudioQuack message-event ingress (`webhook.register` / `trigger.listen`). Default origin **`https://api.typeform.com`**. EU-resident accounts use **`https://api.eu.typeform.com`** or **`https://api.typeform.eu`** (operator picks `dataCenter`). Responses are respondent PII. **Customer owns the Typeform account.** Do not scrape the builder. Embed SDK is out of scope.

Recent Responses API results can lag (~30 minutes); use webhooks for near-real-time.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.list` | GET | `/forms` | List forms (`page`, `page_size` max 200) |
| `form.get` | GET | `/forms/{form_id}` | Full form JSON |
| `form.create` | POST | `/forms` | Create a form (201 + `Location`) |
| `response.list` | GET | `/forms/{form_id}/responses` | Page responses (`before`/`after` cursors) |
| `response.get` | GET | `/forms/{form_id}/responses` | One/few via `included_response_ids` |
| `webhook.register` | PUT | `/forms/{form_id}/webhooks/{tag}` | Create/update webhook (idempotent tag) |
| `trigger.listen` | PUT | `/forms/{form_id}/webhooks/{tag}` | Same PUT; `url` must be SQ ingress; enable + HMAC `secret` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | Chooses the row above |
| `auth` | select | yes | `apiToken` | no | `apiToken` \| `oauth2` |
| `token` | password | yes | | yes | Personal access token or OAuth access token. Header `Authorization: Bearer` |
| `clientId` | string | no | | no | OAuth app (refresh / multi-account) |
| `clientSecret` | password | no | | yes | OAuth client secret |
| `dataCenter` | select | no | `US` | no | `US` → `api.typeform.com`; `EU` → `api.eu.typeform.com` |
| `formId` | string | no | | no | Default form id |
| `timeout_seconds` | number | no | `25` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | File answers: type `file` (ref-id), never inline bytes |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `token` / `webhookSecret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Title contains |
| `page` | number | no | Default 1 |
| `page_size` | number | no | Default 10; max **200** |
| `workspace_id` | string | no | Workspace filter |
| `sort_by` | string | no | `created_at` \| `last_updated_at` |
| `order_by` | string | no | `asc` \| `desc` |
| `is_public` | boolean | no | Filter `settings.is_public` |

### `operation = form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes* | Else config `formId` |

### `operation = form.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Form title |
| `type` | string | no | e.g. `form`, `quiz` |
| `fields` | object[] | no | Create-API field objects (images must already exist in the account) |
| `workspace` | object | no | `{ href }` workspace URL |
| `theme` | object | no | `{ href }` theme URL |
| `hidden` | string[] | no | Hidden field keys |
| `settings` | object | no | Create-API settings |

PUT overwrite of an entire form is **not** a catalog verb — omit.

### `operation = response.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes* | Else config |
| `page_size` | number | no | Max 1000; if more exist, page with `before`/`after` |
| `since` / `until` | string | no | ISO-8601 or unix seconds |
| `before` / `after` | string | no | Response `token` cursor |
| `response_type` | string | no | Comma list: `completed` (default), `partial`, `started` |
| `query` | string | no | Phrase search in answers/hidden/variables |
| `sort` | string | no | e.g. `submitted_at,desc` |

### `operation = response.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes* | Else config |
| `response_id` | string | yes | Token / `response_id`; sent as `included_response_ids` |

### `operation = webhook.register` | `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes* | Else config |
| `tag` | string | yes | Webhook name in the path |
| `url` | string | yes | HTTPS callback. `trigger.listen`: SQ message-event URL only |
| `enabled` | boolean | no | Default `true` |
| `secret` | password | no | HMAC-SHA256 signing secret (`webhookSecret`) |
| `verify_ssl` | boolean | no | Default `true`; self-signed not supported |
| `form_response_partial` | boolean | no | Also deliver partials |

New webhook URLs must be `https`.

## Output fields (by operation)

### `form.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `forms` | object[] | success | `id`, `title`, `last_updated_at`, `_links` |
| `total_items` | number | success | Total |
| `page_count` | number | success | Pages |
| `error` | string | failure | |

### `form.get` | `form.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx / 201 | `ok` |
| `form_id` | string | success | Id |
| `form` | object | success | Form JSON (no secrets) |
| `error` | string | failure | Typeform `code` / `description` |

### `response.list` | `response.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `total_items` | number | success | Count |
| `items` | object[] | success | Responses: `response_id`/`token`, `submitted_at`, `answers` (**PII**) |
| `files` | file[] | file_upload answers | Platform `file` refs from `file_url` when requested |
| `error` | string | failure | |

### `webhook.register` | `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `tag` | string | success | Webhook tag |
| `url` | string | success | Callback URL |
| `enabled` | boolean | success | |
| `error` | string | failure | Never return `secret` |

## Legal

- Developer platform: https://www.typeform.com/developers/ — get started https://developers.typeform.com/developers/get-started/
- Create + Responses: **2 requests per second per Typeform account**. Webhook *delivery* is not that quota; failed deliveries retry up to **1 day**.
- Respondent answers are PII (and may include payment/hidden fields). Customer owns the Typeform workspace, consent, and GDPR/CCPA basis. Minimize. Never log tokens, answers, or HMAC secrets.
- EU data-center accounts must use the EU API host (residency).
- Official-API-first: `api.typeform.com` / `api.eu.typeform.com` only. Do not scrape typeform.com UI.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Header: `Authorization: Bearer {token}`, `Accept: application/json`.
- Rate: token-bucket ~2 rps per account; on 429 wait `Retry-After` or 1 s. Do not tight-loop.
- Body cap **1_000_000** bytes. Timeout default 25 s. File download: stream to `file` ref, per-file cap.
- Inbound webhook: verify HMAC; map to BPMN message events. Do not persist raw bodies beyond the run.
- 4xx/5xx → `{ error, http_status }` on OUTPUT. Truncate provider body (240 chars); redact Bearer.
- Logs: operation, form_id, status, counts/sizes — never answers or tokens.
- **DO NOT BUILD** in this task.
