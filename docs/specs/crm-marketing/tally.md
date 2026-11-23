# Tally

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/tally/`
- **Researched:** 2026-08-20
- **Official API:** Tally REST (date-versioned; pin **`tally-version: 2025-02-01`** or the key’s default) + Webhooks API. Base `https://api.tally.so`.
- **Docs:** https://tally.so/help/api · https://developers.tally.so/api-reference/introduction · https://developers.tally.so/api-reference/versioning · https://developers.tally.so/api-reference/endpoint/forms/list · https://developers.tally.so/api-reference/endpoint/forms/submissions/list · https://developers.tally.so/api-reference/endpoint/forms/submissions/get · https://developers.tally.so/api-reference/endpoint/webhooks/post · https://tally.so/help/webhooks · https://developers.tally.so/llms.txt
- **Auth:** Bearer API key — secret binding: `api_key`

## Scope

One node for Tally forms, submissions, webhook registration, and listen. API is **free on all plans** including Free. File-upload answers must use type `file` when the handler materializes attachments. Rate limit **100 req/min** — prefer webhooks over polling.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.list` | GET | `/forms` | Paginated forms (`items`, `hasMore`) |
| `form.get` | GET | `/forms/{formId}` | Full blocks + settings |
| `submission.list` | GET | `/forms/{formId}/submissions` | Paginated submissions |
| `submission.get` | GET | `/forms/{formId}/submissions/{submissionId}` | One submission |
| `webhook.create` | POST | `/webhooks` | Register endpoint |
| `trigger.listen` | POST | `/webhooks` | Same as create; event `FORM_RESPONSE` toward ingress |

Do not ship form create/PATCH in v1 unless catalog expands — those require sending the **entire** `blocks` array (partial PATCH deletes omitted blocks).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | |
| `api_key` | password | yes | empty | yes | `Authorization: Bearer`. Create at tally.so settings API keys |
| `tally_version` | string | no | `2025-02-01` | no | Header `tally-version`. Keys are pinned at creation; this header overrides per request |
| `timeout_seconds` | number | no | `30` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Submission file answers → OUTPUT `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Bearer or `signingSecret` |

FUNCTION ports: none.

## Input fields (by operation)

### `form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default 1 |
| `limit` | number | no | Default 50, max **500** |
| `workspace_ids` | array | no | Query `workspaceIds` |

### `form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Path `formId` |

### `submission.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | |
| `page` | number | no | |
| `limit` | number | no | Default 50, max **500** |
| `filter` | string | no | `all` \| `completed` \| `partial` |
| `start_date` | string | no | ISO 8601 `startDate` |
| `end_date` | string | no | `endDate` |
| `after_id` | string | no | `afterId` |

### `submission.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | |
| `submission_id` | string | yes | |

### `webhook.create` / `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Body `formId` |
| `url` | string | yes | HTTPS ingress (`trigger.listen` should default to StudioQuack message-event URL when the designer wires listen) |
| `event_types` | array | yes | Official: include `FORM_RESPONSE` |
| `signing_secret` | password | no | Body `signingSecret`; HMAC verification of `Tally-Signature` |
| `http_headers` | array | no | `{ name, value }[]` — do not log values |

Webhook receiver must return **2xx within 10 seconds** (official). Retries on failure.

## Output fields (by operation)

### `form.list` / `form.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | array | list | Forms (`id`, `name`, `status`, `numberOfSubmissions`, `workspaceId`) |
| `form` | object | get | Blocks + settings (may contain PII labels) |
| `page` / `limit` / `total` / `hasMore` | number/boolean | list | Official pagination |
| `error` | string | 4xx/5xx | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | |

### `submission.list` / `submission.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `questions` | array | list | Question metadata |
| `submissions` | array | list | `id`, `isCompleted`, `submittedAt`, `responses` (PII) |
| `submission` | object | get | Full responses |
| `preview_url` | string | when returned | Signed URL — treat as secret-equivalent; do not log |
| `pdf_url` | string | when returned | Signed URL; do not log |
| `files` | array of `file` | file-upload answers | Platform ref-ids after download; cap per file |
| `hasMore` | boolean | list | |
| `error` | string | failure | |

### `webhook.create` / `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 201/200 | `ok` |
| `webhook` | object | created | Id + url; **omit** `signingSecret` from OUTPUT even if API echoes it |
| `error` | string | failure | |

## Legal

- Help + developer: https://tally.so/help/api · https://developers.tally.so/api-reference/introduction
- Webhooks: https://tally.so/help/webhooks
- Operator owns the Tally workspace. Submissions are **PII**. Minimize fields consumed downstream. GDPR/CCPA on the customer.
- Signed `previewUrl` / `pdfUrl` contain access tokens with **no expiry** (official list response). Do not log; do not persist on edges longer than the run.
- Rate limit **100/minute**. Webhook deliveries do not count against that (help docs).
- Never log API keys, signing secrets, `Tally-Signature`, answers, or file bytes.

## Implementation notes

- JDK HTTP. `Authorization: Bearer {api_key}`. Always send `tally-version: {tally_version}`.
- Empty key → `{ error }`, no HTTP.
- Timeout **30 s**. List `limit` cap 500. Response body cap **5_000_000** bytes.
- Retry 429/5xx twice. Honor `Retry-After`.
- File uploads: if `answer` is a Tally file URL, GET it with the same Bearer **only if** official docs say the URL is authenticated; otherwise treat as signed URL. Store as `file` ref-id. Cap **20_000_000** bytes per file.
- `trigger.listen`: POST webhook with `url` set to the StudioQuack engine's message-event ingress. Verify `Tally-Signature` HMAC-SHA256 (help: SHA256 of payload) when `signing_secret` is set. Respond 2xx quickly; correlate payload onto BPMN message.
- Version pin: `2025-01-15` returns a bare array for `/forms`; `2025-02-01` wraps `{ items, page, limit, total, hasMore }`. This node **requires** the wrapped shape — send `2025-02-01` or later.
