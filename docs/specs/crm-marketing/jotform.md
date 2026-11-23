# Jotform

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/jotform/`
- **Researched:** 2026-08-20
- **Official API:** Jotform REST API **v1** (`api.jotform.com` / `eu-api.jotform.com` / `hipaa-api.jotform.com`)
- **Docs:** https://api.jotform.com/docs/
- **Auth:** api_key — secret binding: `api_key`. Send header `APIKEY`. Do **not** put `apiKey` on the query string.

## Scope

Read and delete form definitions and submissions on the operator’s Jotform account. Register a form webhook for `trigger.listen`. Submissions are respondent PII (answers, files, IP metadata).

EU-hosted accounts **must** use `host=eu-api.jotform.com`. US default `host=api.jotform.com`. HIPAA plans: `host=hipaa-api.jotform.com`. Do not mix hosts; keys are region-bound. `baseUrl` is `https://{host}`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `form.list` | GET | `/user/forms` | Paginated forms for the key owner |
| `form.get` | GET | `/form/{form_id}` | One form definition |
| `submission.list` | GET | `/form/{form_id}/submissions` | Submissions for a form |
| `submission.get` | GET | `/submission/{submission_id}` | One submission |
| `submission.delete` | DELETE | `/submission/{submission_id}` | Delete a submission |
| `report.list` | GET | `/form/{form_id}/reports` | Reports for a form |
| `trigger.listen` | POST | `/form/{form_id}/webhooks` | Register SQ ingress URL as form webhook |

Jotform’s own FAQ still says “v1 is mostly read only”; write paths that exist (delete submission, create webhook) are official and in scope. Do not scrape the builder UI.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `form.list` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Jotform API key (`APIKEY` header) |
| `host` | select | yes | `api.jotform.com` | no | Official hostname → `https://{host}` |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Form/submission ids; webhook URL for listen |
| `output` | OUTPUT | `{ status, responseCode, content }` or `{ error }` | Jotform wraps payload in `content` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = form.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `offset` | number | no | Start index |
| `limit` | number | no | Page size (clamp 1–1000) |
| `filter` | string | no | Jotform filter expression if provided |
| `orderby` | string | no | e.g. `created_at` |

### `operation = form.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Form id |

### `operation = submission.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Form id |
| `offset` | number | no | |
| `limit` | number | no | Clamp 1–1000 |
| `filter` | string | no | e.g. `{"created_at:gt":"2026-01-01 00:00:00"}` |
| `orderby` | string | no | |

### `operation = submission.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `submission_id` | string | yes | Submission id |

### `operation = submission.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `submission_id` | string | yes | Submission to delete |

### `operation = report.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Form id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_id` | string | yes | Form to attach |
| `webhook_url` | string | yes | StudioQuack message-event ingress HTTPS URL. Must be HTTPS. |

Inbound POSTs from Jotform are delivered through the StudioQuack engine's message-event ingress and correlate onto a BPMN message catch event. This operation **registers** the URL; it does not run as a long poll.

## Output fields (by operation)

Jotform success envelope: `{ responseCode, message, content, limit-left, … }`. Map as follows.

### `form.list` / `form.get` / `report.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `response_code` | number | 2xx | Jotform `responseCode` |
| `content` | object \| array | 2xx | Forms or form object or reports |
| `limit_left` | number | when present | Remaining daily calls |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

### `submission.list` / `submission.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `content` | object \| array | 2xx | Submission(s): `id`, `form_id`, `created_at`, `answers` (question keyed). **PII.** |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |

### `submission.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `content` | object | 2xx | Jotform delete confirmation |
| `error` | string | failure | |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `content` | object | 2xx | Webhook list/id returned by Jotform |
| `error` | string | failure | Non-HTTPS URL rejected locally |

## Legal

- Docs: https://api.jotform.com/docs/
- Terms: https://www.jotform.com/terms/
- Privacy: https://www.jotform.com/privacy/
- Daily API call limits (FAQ, 2026-08-20): Starter 1 000; Bronze 10 000; Silver 50 000; Gold 100 000; Enterprise unlimited. Confirm live article “Daily API Call Limits”.
- EU host `eu-api.jotform.com` is the residency control for EU plans. HIPAA host is a separate BAA surface — only use `hipaa-api.jotform.com` when the operator’s account is HIPAA.
- Respondent answers are personal data. Minimize fields on OUTPUT edges. Never log `api_key` or answer values. Caller owns lawful basis for processing submissions.

## Implementation notes

- JDK HTTP. `baseUrl` `https://{host}` with official hostnames `api.jotform.com` \| `eu-api.jotform.com` \| `hipaa-api.jotform.com`. Header `APIKEY`.
- Timeout 25 s. Response cap 2 MiB. TLS verified. `Redirect.NEVER`.
- `trigger.listen`: POST form field `webhookURL` (Jotform’s documented name). Reject `http://` locally.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry. Surface `limit-left` when Jotform sends it.
