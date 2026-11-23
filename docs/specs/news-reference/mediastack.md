# Mediastack

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/mediastack/`
- **Researched:** 2026-08-20
- **Official API:** mediastack REST v1 (apilayer) (`https://api.mediastack.com/v1`)
- **Docs:** https://mediastack.com/documentation — also https://docs.apilayer.com/mediastack/docs/api-documentation
- **Auth:** api_key — secret binding: `api_key` (query `access_key` only; vendor documents query auth, not an API-key header)

## Scope

One node for the official mediastack JSON API (apilayer). Live and historical news share `GET /v1/news`; historical is the same path with `date=` and is **plan-gated** (Standard and above). `GET /v1/sources` lists publisher ids for the `sources` filter.

Do not scrape publisher HTML. Do not call unofficial mediastack wrappers. Vendor auth is **query `access_key` only** — map config `api_key` → query `access_key`. Do not invent `X-Api-Key`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `live` | GET | `/news` | Current headlines. **Do not send `date`.** |
| `historical` | GET | `/news` | Paid-plan archive. **Require `date`** (`YYYY-MM-DD` or `YYYY-MM-DD,YYYY-MM-DD`). |
| `sources` | GET | `/sources` | Publisher catalogue (`id`, `name`, `url`, …) for the `sources` filter. |

Base origin: `https://api.mediastack.com/v1`. Always HTTPS.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `live` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | mediastack access key. Sent as query `access_key` only. Never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. `<= 0` → 25. Floor 1 s. |
| `default_limit` | number | no | `25` | no | Default `limit` (1–100). |
| `default_languages` | string | no | empty | no | Default comma-separated ISO 639-1 `languages` when input omitted. |
| `default_countries` | string | no | empty | no | Default comma-separated ISO 3166-1 alpha-2 `countries`. |
| `base_url` | string | no | `https://api.mediastack.com/v1` | no | Documented proxy. No key in this URL. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = live`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keywords` | string | no | Free-text. Prefix `-` to exclude (`virus,-corona`). Alias `query`. |
| `sources` | string | no | Comma-separated source ids; `-` prefix excludes (`cnn,bbc,-techcrunch`). |
| `categories` | string | no | `general` \| `business` \| `entertainment` \| `health` \| `science` \| `sports` \| `technology`. `-` prefix excludes. |
| `countries` | string | no | ISO 3166-1 alpha-2 list; `-` excludes. Else config `default_countries`. |
| `languages` | string | no | ISO 639-1: `ar` `de` `en` `es` `fr` `he` `it` `nl` `no` `pt` `ru` `se` `zh`. `-` excludes. Else `default_languages`. |
| `sort` | string | no | `published_desc` (default) \| `published_asc` \| `popularity`. |
| `limit` | number | no | 1–100, default 25. Else `default_limit`. |
| `offset` | number | no | Pagination offset, default 0. |

Do not send `date` on `live`.

### `operation = historical`

Same fields as `live`, plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `date` | string | yes | Single day `YYYY-MM-DD` or range `YYYY-MM-DD,YYYY-MM-DD`. Blank → `{ error }` no HTTP. Paid plans only; free/live keys typically return `validation_error` or `function_access_restricted`. |

### `operation = sources`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | Keyword(s) over source names/ids. |
| `countries` | string | no | Include/exclude country codes. |
| `languages` | string | no | Include/exclude language codes. |
| `categories` | string | no | Include/exclude categories. |
| `limit` | number | no | 1–100, default 25. |
| `offset` | number | no | Default 0. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx without `error` object | `ok` |
| `pagination` | object | success | Provider `pagination`: `limit`, `offset`, `count`, `total`. |
| `articles` | array | `live` / `historical` | Mapped from `data[]`: `author`, `title`, `description`, `url`, `source`, `image`, `category`, `language`, `country`, `published_at`. Missing → `""`. |
| `sources` | array | `sources` | Mapped from `data[]`: `id`, `name`, `category`, `country`, `language`, `url`. |
| `error` | string | failure | Actionable; never the access key. Prefer provider `error.message` + `error.code`. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` if parseable, else 60. Honor **once**. |

Provider error JSON: `{ "error": { "code", "message", "context" } }`. Known codes: `invalid_access_key`, `missing_access_key`, `usage_limit_reached`, `rate_limit_reached`, `validation_error`, `function_access_restricted`, `internal_error`.

## Legal

- Terms: https://mediastack.com/terms (apilayer Terms of Service & License Agreement). Confirm the live page; the marketing shell is JS-heavy.
- Publisher copyright remains with the news sources. mediastack delivers parsed metadata + snippets, not a licence to republish full articles as original reporting.
- **Attribution / link-out:** when displaying a hit, keep the publisher `source` name and original article `url`. Do not strip source URLs.
- Historical news (`date=`) is **plan-gated** (FAQ: Standard / Professional and above). Free plan: limited monthly request volume (FAQ: stay below ~100/month for free); HTTPS and commercial use are plan features — confirm dashboard.
- Request counting: each call counts ≥1 toward monthly volume; some option combinations consume more (see docs). Overages may bill after 100% (75%/90%/100% email notices).
- PII: do not put personal data in `keywords`. Logs: operation, status, sizes, `pagination.total` — never `access_key`.
- Official-API-first: JDK HTTP to `api.mediastack.com` only. **No scrape of publisher HTML.**

## Implementation notes

- JDK `HttpClient` only. No apilayer / mediastack SDK. TLS verified. `Redirect.NEVER`.
- Auth: query `access_key={api_key}` only. Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0 (mediastack; support@studioquack.com)`.
- Body cap **~8 MiB**. Timeout default 25 s.
- Retry: none on 4xx except a **single** 429 honoring `Retry-After` once. No unbounded retry.
- Empty `api_key` → `{ error: "Missing mediastack API key. Bind api_key in node parameters." }` — no HTTP.
- `historical` without `date` → `{ error: "date is required for historical (YYYY-MM-DD or YYYY-MM-DD,YYYY-MM-DD)." }` — no HTTP.
- `live` must omit `date` even if the caller sends it.
- HTTP 401 / `invalid_access_key` / `missing_access_key` → `Invalid mediastack access key.`
- `usage_limit_reached` / `rate_limit_reached` → plan/quota message + `http_status`.
- Failures go on OUTPUT `{ error }`. Never throw past dispatch. Redact the key to `***` in any transport text.
