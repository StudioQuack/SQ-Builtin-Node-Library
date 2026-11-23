# SurveyMonkey

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/surveymonkey/`
- **Researched:** 2026-08-20
- **Official API:** SurveyMonkey REST **v3** (`https://api.surveymonkey.com/v3`)
- **Docs:** https://api.surveymonkey.com/v3/docs · https://developer.surveymonkey.com/api/v3/ · https://www.surveymonkey.com/developer/build-a-private-app/ · https://developer.surveymonkey.com/tou/ · GitHub source for the same v3 docs: https://github.com/SurveyMonkey/public_api_docs
- **Auth:** OAuth 2.0 Bearer — secret bindings: `client_secret`, `access_token`

## Scope

One node for surveys, collectors, responses, and webhooks. Typical StudioQuack install is a **Private app** (no App Directory review) with OAuth scopes set in https://developer.surveymonkey.com/apps/. Response **answer text** requires `responses_read_detail` (paid). `trigger.listen` registers `POST /v3/webhooks` toward StudioQuack message-event ingress. Responses are **PII**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `survey.list` | GET | `/v3/surveys` | Page of surveys |
| `survey.get` | GET | `/v3/surveys/{id}` or `/v3/surveys/{id}/details` | Metadata vs full design+ids |
| `response.list` | GET | `/v3/surveys/{id}/responses` or `/responses/bulk` | Metadata vs answers |
| `response.get` | GET | `/v3/surveys/{survey_id}/responses/{id}` | One response |
| `collector.list` | GET | `/v3/surveys/{id}/collectors` | Collectors |
| `trigger.listen` | POST | `/v3/webhooks` | Subscribe to events |

Use `/details` when the operator needs question/choice ids to decode bulk answers (official bulk workflow). Prefer `/responses/bulk` for answer payloads (`per_page` ≤ 100 in their example).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `survey.list` | no | |
| `client_id` | string | yes | empty | no | App client id |
| `client_secret` | password | yes | empty | yes | |
| `access_token` | password | yes | empty | yes | Private-app long-lived token from app Settings, or OAuth access token. Header `Authorization: Bearer` |
| `timeout_seconds` | number | no | `30` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Bearer or answer PII in logs |

FUNCTION ports: none. File: none unless a future attachment type appears — then type `file`.

## Input fields (by operation)

### `survey.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Default 1 |
| `per_page` | number | no | Official pagination |

### `survey.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | |
| `include_details` | boolean | no | If true, GET `/surveys/{id}/details` (design + answer option ids) |

### `response.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | |
| `bulk` | boolean | no | Default **true** for this node so answers are included when scope allows |
| `page` | number | no | |
| `per_page` | number | no | Official example 100 |
| `start_created_at` / `end_created_at` | string | no | If documented on live list endpoint |

### `response.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | |
| `response_id` | string | yes | |

### `collector.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | |
| `page` | number | no | |
| `per_page` | number | no | |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Webhook name |
| `object_type` | string | yes | e.g. `survey` |
| `object_ids` | array | yes | Survey (or collector) ids |
| `verify_ssl` | boolean | no | Default true |
| `authorization` | string | no | Provider sends this to the subscriber — treat as secret if set |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | array | list | Surveys / responses / collectors |
| `survey` | object | get | |
| `response` | object | get | Answers = PII |
| `page` / `per_page` / `total` | number | list | |
| `links` | object | list | `next` / `self` — follow only `api.surveymonkey.com` hosts |
| `webhook` | object | listen | `id`, `href`, `name` |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | |

## Legal

- Developer ToS: https://developer.surveymonkey.com/tou/
- Private vs public apps: https://www.surveymonkey.com/developer/build-a-private-app/ — Private apps start at **500 calls/day** and **120 requests/minute** (draft/private limits in public_api_docs). Public directory apps have higher/unlimited published limits.
- **Scopes (official):** `surveys_read`, `collectors_read`, `responses_read`, `responses_read_detail` (paid, answer bodies), `webhooks_read`, `webhooks_write`. Set these on the private app before shipping.
- Respondent answers are **PII** (often special-category if surveys ask health/politics). Customer owns the SurveyMonkey account and GDPR/CCPA basis. Minimize: default `survey.list` does not pull answers; `response.*` does.
- Never log tokens, `authorization` webhook secrets, IP fields on responses, or answer arrays.

## Implementation notes

- JDK HTTP. Base `https://api.surveymonkey.com`. Header `Authorization: Bearer {access_token}`, `Accept: application/json`.
- Empty token → `{ error }`, no HTTP.
- Timeout **30 s**. Response cap **5_000_000** bytes.
- Retry 429/5xx twice. Private-app daily 500: surface `{ error }` with `http_status=429` rather than looping.
- Pagination: use `links.next` only when the URL host is `api.surveymonkey.com` (or documented `.net` alias). Prefer constructing `page=` yourself.
- Decode bulk answers using cached `/details` when the operator needs labels; do not log the cache.
- `trigger.listen` is ingress: create the provider webhook pointing at the StudioQuack engine's message-event ingress, not an open URL.
