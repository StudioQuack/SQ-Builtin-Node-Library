# Okendo

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/okendo/`
- **Researched:** 2026-08-20
- **Official API:** Okendo **Merchant (Enterprise) REST API** — version header `okendo-api-version` (documented example `2025-02-01`)
- **Docs:** https://docs.okendo.io/merchant-rest-api (JS-gated site; Markdown `.md` URLs returned a generic index on 2026-08-20). Auth notes: https://docs.okendo.io/merchant-rest-api/quick-start. Surveys: https://docs.okendo.io/merchant-rest-api/endpoints/surveys
- **Auth:** HTTP Basic — username = Okendo User ID (`username`), password = 32-char API key — secret binding: `api_key`

## Scope

One node for Okendo reviews/surveys on Shopify. Call the **Merchant** API (`https://api.okendo.io/enterprise`) from the daemon — never the public Storefront REST API (unauthenticated, client-side). The customer owns the Okendo merchant account.

**`review_create` is unverified** on the Merchant surface as of 2026-08-20: public descriptions list list/get/update/reply, not shopper-create. Do **not** invent `POST /reviews`. If the live OpenAPI at docs.okendo.io documents a create verb, implement it; otherwise return `{ error }` for `review_create` (shoppers submit via Okendo widgets).

UGC: moderate before republish. Never log reviewer email or review body.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `review_list` | GET | `/reviews` | Cursor-paginated reviews (`lastEvaluated`, `limit` 1–100). Pass `status` (`approved` / `pending` / `rejected`) — default is approved-only |
| `review_create` | — | **unverified** | Do not call unless OpenAPI documents a create path |
| `survey_get` | GET | `/surveys/{surveyId}/responses` | List responses for a survey UUID (not a public “list surveys” parent) |

Webhooks exist (docs.okendo.io webhooks) but are **not** in this catalog row’s starting ops.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `review_list` | no | Chooses the row above |
| `username` | string | yes | empty | no | Okendo User ID (Basic username) |
| `api_key` | password | yes | empty | yes | 32-char Merchant API key (Basic password) |
| `api_version` | string | no | `2025-02-01` | no | Header `okendo-api-version` (date). Confirm live allowed values in docs |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = review_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `status` | string | no | Moderation status filter |
| `limit` | number | no | 1–100, default 25 |
| `last_evaluated` | string | no | Cursor (`lastEvaluated`) |
| `order_by` | string | no | Sort, e.g. `date desc` if documented |

### `operation = review_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `review` | object | yes | Only used if OpenAPI documents create. Else ignore and error |

### `operation = survey_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `survey_id` | string | yes | Survey UUID |
| `limit` | number | no | 1–100 |
| `last_evaluated` | string | no | Cursor |
| `order_by` | string | no | Default `date desc` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `reviews` | array | `review_list` | Review ids + moderation status; omit reviewer PII from logs |
| `last_evaluated` | string | paginated | Next cursor |
| `survey_responses` | array | `survey_get` | Answers; NPS category when type `nps` |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- Merchant API is **not** for on-site/browser use (Okendo warning: exposing Basic credentials puts shop data at risk).
- Shopify + Okendo merchant terms. Respondent/reviewer PII — GDPR/CCPA on the merchant.
- Docs site was JS-gated on fetch; treat endpoint details as **verify against live OpenAPI** at implement time.
- Official-API-first: no scrape of Okendo admin or widgets.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- `Authorization: Basic base64(username + ":" + api_key)`. Header `okendo-api-version: {api_version}` on **every** request.
- Empty key/username → `{ error }` — no HTTP.
- `review_create` without a documented path → `{ error: "Okendo Merchant API does not document review create. Use widgets for shopper submissions." }` — no HTTP.
- Body cap **1_000_000** bytes. Timeout default 30 s.
- Logs: operation, HTTP status, counts — never API key, reviewer email, or review/survey answer text.
