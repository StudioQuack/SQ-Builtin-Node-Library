# Currents News API

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/currents/`
- **Researched:** 2026-08-20
- **Official API:** Currents API v1 (`https://api.currentsapi.services/v1`) — **currentsapi.services (news), not currents.dev**
- **Docs:** https://currentsapi.services/en/docs/ — OpenAPI: https://currentsapi.services/json/swagger.json — search: https://currentsapi.services/en/docs/search — latest: https://currentsapi.services/en/docs/latest-news
- **Auth:** api_key — secret binding: `api_key` (header `Authorization: Bearer <api_key>`)

## Scope

One node for the official Currents News API. Catalog verbs `search`, `latest`, and `available` map to swagger paths `/search`, `/latest-news`, and `/available/{languages|regions|categories}`. Swagger **does not** define `/available/sources` — do not invent one.

Self-service plans are for processing, previews, attribution, and links back to publishers — not a paywall bypass and not a licence to republish full articles.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/search` | Keyword / topic / date-range discovery (`keywords`, `start_date`, `end_date`, …). |
| `latest` | GET | `/latest-news` | Fresh feed. Swagger lists `language`; product pages also describe country/category filters. |
| `available` | GET | `/available/{kind}` | Catalogue helpers. `kind`: `languages` \| `regions` \| `categories`. |

Base origin: `https://api.currentsapi.services/v1`. Host in swagger: `api.currentsapi.services`, `basePath` `/v1`, scheme `https`.

Retired / missing: no `/available/sources`. Do not add it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Currents API key. Header `Authorization: Bearer {api_key}` only. Never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. `<= 0` → 25. Floor 1 s. |
| `default_language` | string | no | `en` | no | Default `language` when input is blank. |
| `base_url` | string | no | `https://api.currentsapi.services/v1` | no | Documented proxy. No key in this URL. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

Swagger query params:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keywords` | string | no | Search keywords. Alias `query`. Prefer non-blank `keywords` for production. |
| `language` | string | no | Language filter. Else config `default_language`. |
| `country` | string | no | Country / region filter (codes from `available` `regions`). |
| `category` | string | no | Category (codes from `available` `categories`: `regional`, `technology`, `lifestyle`, `business`, `general`, `programming`, `science`, `entertainment`, `world`, `sports`, `finance`, `academia`, `politics`, `health`, `opinion`, `food`, `game`, …). |
| `start_date` | string | no | `YYYY-MM-DDTHH:MM:SS+00:00` |
| `end_date` | string | no | `YYYY-MM-DDTHH:MM:SS+00:00` |

Product curl also shows `page_size` on latest-news. **Not in swagger 2026-08-20** for `/search`. Do not send undocumented search params.

### `operation = latest`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `language` | string | no | Swagger-documented filter. Else `default_language`. |
| `country` | string | no | Product pages describe country filter on Latest News. Send when non-blank. |
| `category` | string | no | Product pages describe category filter. Send when non-blank. |
| `page_size` | number | no | Shown on official product curl (`page_size=5`). Not in swagger 2026-08-20. Send when a positive int; if HTTP 400, surface `{ error }` (do not retry with other names). |

### `operation = available`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `kind` | string | yes | `languages` → `/available/languages`; `regions` → `/available/regions`; `categories` → `/available/categories`. Other values → `{ error }` no HTTP. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | Provider `status` (`ok`) or `ok` if missing. |
| `news` | array | `search` / `latest` | Provider `news[]`. Map each item: `id`, `title`, `description`, `url` (example field; swagger News also lists `urls`), `author`, `image`, `language`, `category` (array), `published`. Missing → `""` / `[]`. |
| `languages` | object | `available` + `kind=languages` | Provider `languages` map (name → code), e.g. `"English": "en"`. |
| `regions` | object | `available` + `kind=regions` | Provider `regions` map. |
| `categories` | array | `available` + `kind=categories` | Provider `categories` string list. |
| `description` | string | `available` | Provider `description` when present. |
| `error` | string | failure | Actionable; never the bearer token. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` or `X-RateLimit-*` wait, else 60. Honor **once**. |

Swagger errors: 401 Unauthorized; 429 Token limit reached.

## Legal

- Terms: https://currentsapi.services/terms (Last updated: December 27, 2025). Swagger `termsOfService` same URL.
- Licence: download for as long as the use case requires — **not a transfer of title**. Do not reverse-engineer, strip proprietary notices, mirror data as-is with no value-add, register more than one API key at a time, or circumvent plan limits.
- **Publisher copyright:** IP in retrieved data belongs to the originating website. Unless law or the owner permits: no permanent copies / scrape / databases of the data; no sell/lease/distribute/sublicense/translate/modify as a substitute news product; no misleading ownership; no stripping copyright/author labels.
- Product usage (https://currentsapi.services/en/free-news-api, pricing): self-service is for **API processing, previews, attribution, and links back to publishers**. Customer-facing AI summaries that replace articles, long-term archives, resale, OEM, or republication need separate terms. **No paywall bypass.**
- Attribution: unless authorized otherwise, display attribution such as a hyperlink to https://currentsapi.services/ with text **Powered by Currents News API**. Keep publisher URLs on each item.
- Quota (live product pages 2026-08-20): **free ~250 requests/day**; partial access, max ~20 results/request, recent history up to ~30 days. Paid Builder/Professional raise volume. HTTP 429 when token/quota exhausted. Track `X-RateLimit-Remaining` / `X-RateLimit-Limit` if present.
- Prohibit uses include collecting personal data of others and spider/crawl/scrape. PII: do not put personal data in `keywords`. Logs: operation, status, sizes — never the bearer token.
- Official-API-first: JDK HTTP to `api.currentsapi.services` only. No HTML scrape.

## Implementation notes

- JDK `HttpClient` only. No vendor SDK. TLS verified. `Redirect.NEVER`.
- Header: `Authorization: Bearer {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0 (currents; support@studioquack.com)`.
- Body cap **~8 MiB**. Timeout default 25 s.
- Retry: none on 4xx except a **single** 429 honoring `Retry-After` once. No unbounded retry.
- Empty `api_key` → `{ error: "Missing Currents API key. Bind api_key in node parameters." }` — no HTTP.
- HTTP 401 → `Invalid Currents API key.` + `http_status: 401`.
- HTTP 429 → `Currents quota exceeded.` + `retry_after_seconds`.
- Failures go on OUTPUT `{ error }`. Never throw past dispatch. Redact bearer material to `***`.
