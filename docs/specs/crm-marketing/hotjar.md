# Hotjar

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/hotjar/`
- **Researched:** 2026-08-20
- **Official API:** Hotjar HTTP API **v1** (`api.hotjar.io`) + Survey/Recording **webhooks**. Identify/Events are **browser JavaScript only**.
- **Docs:** https://help.hotjar.com/hc/en-us/articles/36820005914001-Hotjar-API-Reference
- **Auth:** OAuth2 **client_credentials** against `/v1/oauth/token` using Hotjar API key pair (client id + client secret). Secret bindings: `client_id`, `client_secret`. Webhook HMAC key: `webhook_signing_key`.

## Scope

**Build:** Ask Scale REST export of surveys and survey responses; user lookup/deletion where the plan allows; `trigger.listen` for Hotjar-signed webhooks (`survey_response`, optional `recording` **metadata**).

**DO NOT BUILD (no official server API):**

| Catalog / wish | Why |
|---|---|
| `identify` | Client JS only: `hj('identify', userId, attrs)` on pages with the tracking code. A daemon cannot call this. |
| `event.track` | Client JS only: `hj('event', 'action_name')`. |
| Heatmap export | **No** official heatmap JSON/image API. |
| Recording video / DOM scrape | Webhook `recording` payloads are metadata + app URL, not replay files. Contentsquare/Hotjar ToS forbid screen/database scraping. |
| `trigger.feedback` | Official webhook events are `survey_response`, `recording`, `test_message` only. No Feedback-widget webhook. |

REST is plan-gated: **Ask Scale** = survey export; **Observe Scale** = user lookup/deletion. Keys expire after **1 year**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `survey.list` | GET | `/v1/sites/{site_id}/surveys` | List surveys (cursor page, max 100) |
| `survey.get` | GET | `/v1/sites/{site_id}/surveys/{survey_id}` | One survey including questions |
| `survey.response.list` | GET | `/v1/sites/{site_id}/surveys/{survey_id}/responses` | Bulk responses (Ask Scale). Sorted created desc; **no date filter**. |
| `user.lookup` | POST | `/v1/organizations/{organization_id}/user-lookup` | GDPR lookup/delete (Scale). Returns **202** empty body. Catalog-optional. |
| `trigger.listen` | n/a | Operator pastes SQ ingress URL in Hotjar Survey or Recording Segment webhook UI | Receive `survey_response` (Ask Scale) or `recording` metadata (Observe Scale) |

`identify` and `event.track`: if selected, return `{ error: "Hotjar Identify/Events are browser JavaScript APIs; not callable from this node" }` with **no** HTTP to Hotjar.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `survey.response.list` | no | Chooses the row above |
| `client_id` | password | yes for REST | empty | yes | Hotjar API key client id |
| `client_secret` | password | yes for REST | empty | yes | Hotjar API key client secret |
| `site_id` | string | yes for survey REST | empty | no | Hotjar site id |
| `organization_id` | string | yes for `user.lookup` | empty | no | Organization id (Sites & Organizations) |
| `webhook_signing_key` | password | yes for `trigger.listen` verify | empty | yes | “View Webhook key” in Hotjar webhook settings |
| `timeout_seconds` | number | no | `25` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Survey ids, pagination, or passthrough of verified webhook JSON |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Survey answers are PII |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = survey.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Max **100** |
| `cursor` | string | no | `next_cursor` from prior page |
| `with_questions` | boolean | no | Include question definitions |

### `operation = survey.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | e.g. `survey_…` |

### `operation = survey.response.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | |
| `limit` | number | no | Max **100** |
| `cursor` | string | no | |

### `operation = user.lookup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `data_subject_email` | string | one of email/map | Email of the data subject |
| `data_subject_site_id_to_user_id_map` | object | one of | `{ "<site_id>": "<identify user id>" }` |
| `delete_all_hits` | boolean | no | `true` = silent delete, no email. Default `false` (email report). |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event` | string | no | Expected `survey_response` \| `recording` \| `test_message` (filter after verify) |
| `payload` | object | yes when invoked from ingress | Raw JSON body from Hotjar POST |
| `signature` | string | yes | Header `com-hotjar-signature` |
| `received_at` | number | no | Unix seconds; reject if payload timestamp older than 5 minutes |

Registration is **UI-only** (Hotjar has no webhook CRUD REST). Document the SQ ingress URL for the operator.

## Output fields (by operation)

### REST list/get/responses

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | list ops | Surveys or responses |
| `next_cursor` | string \| null | list ops | Pagination |
| `survey` | object | `survey.get` | Survey + `questions` |
| `error` | string | failure | 401/403 plan or bad token |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 (3000 req/min / 50 rps) | 60 |

### `user.lookup`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 202 | `ok` |
| `http_status` | number | | `202` with empty body is success |
| `error` | string | 4xx | Need email and/or site→user map |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | HMAC + timestamp ok | `ok` |
| `event` | string | | `survey_response` / `recording` / `test_message` |
| `data` | object | | Event payload. For `recording`, **metadata only** (`id`, `hotjar_user_id`, `recording` URL in Hotjar app). Never fetch replay HTML. |
| `error` | string | bad HMAC / stale ts / unexpected | |

## Legal

- API Reference: https://help.hotjar.com/hc/en-us/articles/36820005914001-Hotjar-API-Reference
- Webhooks: https://help.hotjar.com/hc/en-us/articles/13052816995991-Webhooks-Reference
- Identify (JS): https://help.hotjar.com/hc/en-us/articles/360033640653-Identify-API-Reference
- Events (JS): https://help.hotjar.com/hc/en-us/articles/4405109971095-Events-API-Reference
- Contentsquare ToS (scraping / site crawl prohibited): https://contentsquare.com/page-legal-terms-of-service/
- Rate limit: **3000 requests/minute per source address**. Keys expire in 1 year.
- Survey answers and session metadata are personal data. Customer owns the Hotjar site. Never log tokens, answers, or `hotjar_user_id` in daemon logs.

## Implementation notes

- Token: POST `https://api.hotjar.io/v1/oauth/token` as `application/x-www-form-urlencoded` (`grant_type=client_credentials`, `client_id`, `client_secret`). Cache `access_token` until `expires_in` (typically 3600 s) **in memory**, not logs.
- REST: `Authorization: Bearer`, host `https://api.hotjar.io`. JDK HTTP only.
- Webhook verify: HMAC-**SHA3-256** over raw body with signing key; compare to `com-hotjar-signature`. Then check payload timestamp (replay window 5 min). Respond 2xx within 10 s on the ingress path.
- Timeout 25 s. Response cap 2 MiB. TLS verified. `Redirect.NEVER`.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry.
