# Formstack

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/formstack/`
- **Researched:** 2026-08-20
- **Official API:** Formstack Forms API **V2025** (OpenAPI title `Formstack API v2025.0`, spec `version` `0.0.4`; docs `updatedAt` 2026-06-03)
- **Docs:** https://developers.formstack.com/reference — overview https://developers.formstack.com/reference/api-overview — index https://developers.formstack.com/llms.txt
- **Auth:** Personal Access Token (`Authorization: Bearer`) — secret binding: `personalAccessToken`. V2 OAuth remains available but **this node targets V2025**.

## Scope

One node for Formstack Forms. Production base **`https://www.formstack.com/api/v2025`**. Lists forms, reads submissions (respondent PII), and registers per-form webhooks that POST into StudioQuack message-event ingress (`trigger.listen`). V2 (`/api/v2`, OAuth) is not going away per Formstack’s 2025 announcement, but V2025 is the current documented surface (PATs, JSON-only, OpenAPI). File fields: download via submission upload endpoint using type `file` (ref-id), never inline bytes. **Customer owns the Formstack account.** HIPAA/GDPR/SOC 2/PCI claims are Formstack’s; operators still minimize PII on edges and logs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.list` | GET | `/forms` | Paginated forms (`pageNumber`, `pageSize`, `search`, `folder`) |
| `form.get` | GET | `/forms/{formId}` | Form metadata + field layout |
| `submission.list` | GET | `/forms/{formId}/submissions` | Paginated submissions for one form |
| `submission.get` | GET | `/submissions/{submissionId}` | One submission’s answers |
| `trigger.listen` | POST | `/forms/{formId}/webhooks` | Push new submissions to SQ ingress |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | Chooses the row above |
| `personalAccessToken` | password | yes | | yes | PAT; header `Authorization: Bearer fs_pat_…` |
| `formId` | number | no | | no | Default form id for list/get/listen |

Do not put tokens on INPUT.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | File answers use type `file` |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include the PAT |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pageNumber` | number | no | Page ≥ 1 |
| `pageSize` | number | no | 10–500; default 50 |
| `search` | string | no | Filter by form name |
| `orderBy` | string | no | Sort column |
| `order` | string | no | `ASC` or `DESC` |
| `folder` | number | no | Folder id |

### `operation = form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | number | yes | Form id |

### `operation = submission.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | number | yes | Form id |
| `pageNumber` | number | no | Page ≥ 1 |
| `pageSize` | number | no | Provider page size |

Pass through only query names present on `GET /forms/{formId}/submissions` in the live V2025 OpenAPI at build (docs intro: pagination, date ranges, field search — do not invent param names).

### `operation = submission.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `submissionId` | number | yes | Submission id |
| `downloadFiles` | boolean | no | If true, fetch upload parts as `file` refs (cap count/size) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `formId` | number | yes | Form to watch |
| `name` | string | yes | Webhook name |
| `url` | string | yes | Public HTTPS SQ message-event URL |
| `contentType` | string | no | `json` (prefer) or `urlencoded` |
| `fileTransferType` | string | no | `downloadLink` (default), `signedUrl`, or `base64encode` — **do not use `base64encode`** (unbounded) |
| `hmacSecret` | password | no | HMAC key; store as secret, never log |
| `sharedSecret` | password | no | Shared secret query/body check |
| `postDataFieldKeys` | string | no | `field_names` (default) or `field_ids` / `api_friendly_field_names` |

## Output fields (by operation)

### `form.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `forms` | object[] | success | Previews: `id`, `name`, `url`, `submissionsCount`, `active`, `encrypted` |
| `page` | object | success | `{ pageNumber, pageSize, totalElements, totalPages }` |
| `error` | object | failure | `{ status, code, message }` |

### `form.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `formId` | number | success | Form id |
| `form` | object | success | Form details (no secrets) |
| `error` | object | failure | `{ status, code, message }` |

### `submission.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `submissions` | object[] | success | Submission summaries (PII — do not log) |
| `page` | object | success | Pagination |
| `error` | object | failure | `{ status, code, message }` |

### `submission.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `submissionId` | number | success | Id |
| `data` | object | success | Field answers (PII) |
| `files` | file[] | when downloads requested | Platform `file` refs |
| `error` | object | failure | `{ status, code, message }` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP 201 |
| `webhookId` | number | success | Webhook id |
| `url` | string | success | Callback URL |
| `error` | object | failure | `{ status, code, message }` — never return `hmacSecret` |

## Legal

- Formstack legal/terms: https://www.formstack.com/legal (confirm live Terms of Service URL at build)
- V2025 daily rate limits are **plan-specific**; `429 Too Many Requests` when the token quota is exceeded (api-overview).
- Submissions are respondent PII (and may be PHI on HIPAA accounts). Minimize. Customer owns the Formstack org and consent records.
- Encrypted forms: API may withhold field values; surface `{ error }` rather than guessing.
- Official-API-first: `www.formstack.com/api/v2025` only. Do not scrape the builder.

## Implementation notes

- JDK `HttpClient` only. Host: `www.formstack.com`.
- Header: `Authorization: Bearer <personalAccessToken>` (token already includes `fs_pat_` prefix per docs).
- Timeouts: connect 10s, read 30s (60s if downloading files). Body cap 2 MiB; file downloads streamed to `file` refs with a per-file size cap.
- Retry: `429` wait until next UTC day or honor `Retry-After` if present; `5xx` backoff + jitter, max 3.
- Inbound webhook: verify HMAC/`sharedSecret`; map to BPMN message events. Do not persist raw bodies beyond the run.
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
