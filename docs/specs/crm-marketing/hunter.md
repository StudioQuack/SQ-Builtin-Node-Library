# Hunter

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/hunter/`
- **Researched:** 2026-08-20
- **Official API:** Hunter REST API **v2** (`api.hunter.io`)
- **Docs:** https://hunter.io/api-documentation/v2
- **Auth:** api_key — secret binding: `api_key`. Send `X-API-KEY` (preferred) or `Authorization: Bearer`. Do **not** put the key on the query string (leaks in logs/proxies).

## Scope

Email discovery and verification against Hunter’s official v2 HTTP API: domain search, email finder, email verifier. Catalog does **not** include Discover, Enrichment, or Sequences — omit those verbs.

Hunter returns professional emails and sources scraped from public web pages. **GDPR:** for personal data obtained via the API, the workflow operator is an independent controller and must have a lawful basis (typically legitimate interest for B2B prospecting, plus suppression/opt-out). StudioQuack does not supply that basis.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `domain.search` | GET | `/v2/domain-search` | Emails found for a domain or company name |
| `email.finder` | GET | `/v2/email-finder` | Most likely email for a person at a domain |
| `email.verifier` | GET | `/v2/email-verifier` | Deliverability check for one address |

Retired / unofficial: v1 paths, HTML scrape of hunter.io UI.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `domain.search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Hunter API key. Header only. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |
| `default_limit` | number | no | `10` | no | Domain Search page size (Hunter max **100**) |

Test key `test-api-key` is documented by Hunter (dummy payloads, no credits). Allow it; do not special-case logging.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Domain, name, or email. PII. |
| `output` | OUTPUT | `{ status, data, meta }` or `{ error }` | Strip nothing Hunter returned except never echo `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = domain.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | one of `domain`/`company` | e.g. `stripe.com` |
| `company` | string | one of `domain`/`company` | Company name if domain unknown |
| `limit` | number | no | 1–100 (Hunter max) |
| `offset` | number | no | Pagination offset |
| `type` | string | no | `personal` \| `generic` |
| `seniority` | string | no | Comma-separated: `junior`, `senior`, `executive` |
| `department` | string | no | Comma-separated Hunter department slugs |
| `required_field` | string | no | Comma-separated fields that must be present |

### `operation = email.finder`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | yes unless `company` | Company domain |
| `company` | string | yes unless `domain` | Company name |
| `first_name` | string | yes unless `full_name` or `linkedin_handle` | Given name |
| `last_name` | string | yes unless `full_name` or `linkedin_handle` | Family name |
| `full_name` | string | no | Alternative to first+last |
| `linkedin_handle` | string | no | LinkedIn public identifier (operator must comply with LinkedIn ToS for how they obtained it) |

### `operation = email.verifier`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Address to verify. Empty → `{ error }`, no HTTP. |

## Output fields (by operation)

### `operation = domain.search`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object | 2xx | Hunter `data`: `domain`, `organization`, `pattern`, `emails[]` (`value`, `type`, `confidence`, `first_name`, `last_name`, `position`, `department`, `linkedin`, `verification`) |
| `meta` | object | 2xx | `results`, `limit`, `offset` |
| `error` | string | failure | Missing domain/company, 401, 429 |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Prefer `Retry-After` if present else 60 |

### `operation = email.finder`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object | 2xx | `email`, `score`, `first_name`, `last_name`, `position`, `twitter`, `linkedin_url`, `sources[]`, `verification` |
| `meta` | object | 2xx | Hunter `meta.params` echo (no key) |
| `error` | string | failure | Missing name+domain |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

### `operation = email.verifier`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object | 2xx | `status` (`valid`/`invalid`/`accept_all`/`webmail`/`disposable`/`unknown`), `score`, `email`, `regexp`, `gibberish`, `disposable`, `webmail`, `mx_records`, `smtp_server`, `smtp_check`, `block`, `sources[]` |
| `meta` | object | 2xx | |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

## Legal

- API v2: https://hunter.io/api-documentation/v2
- Overview: https://hunter.io/api
- Terms (API section §4): https://hunter.io/terms-of-service
- Privacy: https://hunter.io/privacy-policy
- GDPR help: https://help.hunter.io/en/articles/1890029-gdpr-compliance
- DPA: https://hunter.io/data-processing-agreement
- Rate limits (published): Domain Search / Email Finder **15 rps / 500 rpm**; Email Verifier **10 rps / 300 rpm**. 403 = rate; 429 = usage/credits.
- Operator remains independent controller for names, emails, titles, phone numbers obtained from Hunter. Legitimate-interest assessments, B2B vs consumer, and CAN-SPAM/CASL for any subsequent send are **caller duties**. Do not use this node to build a redistributed email database.

## Implementation notes

- JDK HTTP GET to `https://api.hunter.io`. Header `X-API-KEY`. Query params for filters only.
- Timeout 25 s. Response cap 1 MiB. TLS verified. `Redirect.NEVER`.
- Clamp `limit` to 1–100. Missing required combo → `{ error }` without HTTP.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry. Never log `api_key`, emails, or `sources` URIs.
