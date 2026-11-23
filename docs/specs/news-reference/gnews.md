# GNews

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/gnews/`
- **Researched:** 2026-08-20
- **Official API:** GNews API v4 (`https://gnews.io/api/v4`)
- **Docs:** https://docs.gnews.io/ — search: https://docs.gnews.io/endpoints/search-endpoint — top headlines: https://docs.gnews.io/endpoints/top-headlines-endpoint — JSON: https://docs.gnews.io/json-response — auth: https://docs.gnews.io/authentication
- **Auth:** api_key — secret binding: `api_key` (header `X-Api-Key` only)

## Scope

One node for the official GNews REST API. The designer picks `operation`; the handler calls `GET /search` or `GET /top-headlines` on `https://gnews.io/api/v4`. Use this for keyword news search (boolean operators, date window, language/country) and Google News–ranked headlines by category.

Do not scrape publisher HTML or Google News HTML. Do not call unofficial GNews wrappers. Vendor documents both query `apikey` and header `X-Api-Key` — **use the header only**; never put the key on the query string or INPUT.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/search` | Keyword search (`q` required). Boolean operators, `from`/`to`, `lang`, `country`. |
| `top_headlines` | GET | `/top-headlines` | Trending articles by Google News ranking. Default category `general`. `q` optional. |

Base origin: `https://gnews.io/api/v4`. Trailing slashes stripped. Official docs list **only these two** endpoints — do not add `latest-headlines` or `sources`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | GNews API key. Header `X-Api-Key` only. Never INPUT, never query `apikey`, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. `<= 0` → 25. Floor 1 s. |
| `default_lang` | string | no | empty | no | Default ISO 639-1 `lang` when input `lang` is blank. |
| `default_country` | string | no | empty | no | Default ISO 3166-1 alpha-2 `country` when input `country` is blank. |
| `default_max` | number | no | `10` | no | Default `max` (1–100, plan-capped). Free plan max 10. |
| `base_url` | string | no | `https://gnews.io/api/v4` | no | Documented proxy that speaks the same REST shape. No key in this URL. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Wire `q`. Max 200 characters. URL-encode. Logical `AND`/`OR`/`NOT`, quoted phrases. Alias `q`. Empty → `{ error }` no HTTP. |
| `lang` | string | no | ISO 639-1 (`en`, `es`, `fr`, …). Else config `default_lang`. Omit when both blank. |
| `country` | string | no | ISO 3166-1 alpha-2 (`us`, `gb`, …). Else config `default_country`. Publication country, not article topic. |
| `max` | number | no | Articles per page. Min 1, max 100 (plan may be lower; free = 10). Else `default_max`. |
| `in` | string | no | Comma-separated search attributes: `title`, `description`, `content`. Default on wire if omitted: `title,description`. |
| `nullable` | string | no | Attributes allowed to be null: `description`, `content`, `image`. |
| `from` | string | no | ISO 8601 lower bound on `publishedAt` (e.g. `2026-08-18T21:32:58.500Z`). |
| `to` | string | no | ISO 8601 upper bound. |
| `sortby` | string | no | `publishedAt` (default) \| `relevance`. |
| `page` | number | no | 1-based. Page window is `max` wide. Cannot paginate past 1000 articles. |
| `truncate` | string | no | `content` to truncate `content`. Free plan truncates `content` automatically. |

### `operation = top_headlines`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `category` | string | no | `general` (default) \| `world` \| `nation` \| `business` \| `technology` \| `entertainment` \| `sports` \| `science` \| `health`. |
| `query` | string | no | Optional `q` (same syntax/200-char cap as search). Alias `q`. |
| `lang` | string | no | Same as search. Headlines language list is a superset of search (see docs). |
| `country` | string | no | Same as search. |
| `max` | number | no | Same as search. |
| `nullable` | string | no | Same as search. |
| `from` | string | no | ISO 8601 lower bound. |
| `to` | string | no | ISO 8601 upper bound. |
| `page` | number | no | Same as search (max 1000 articles). |
| `truncate` | string | no | `content`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `total_articles` | number | success | Provider `totalArticles` (index size, may exceed this page). |
| `articles_returned` | number | success | `articles.length` on this page. |
| `articles` | array | success | Mapped hits. Each object: `id`, `title`, `description`, `content`, `url`, `image`, `published_at` (from `publishedAt`), `lang`, `source_id` (`source.id`), `source` (`source.name`), `source_url` (`source.url`), `source_country` (`source.country` — search only). Missing JSON → `""`. Free-plan `content` is truncated. |
| `error` | string | failure | Actionable; never the API key. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` if parseable, else 60. Honor **once**. |

## Legal

- Terms: https://gnews.io/legal/terms-of-service (Last Updated: June 22, 2026). Operator: Gigathink Sàrl (Switzerland).
- Pricing / commercial: https://gnews.io/pricing — **Free plan is non-commercial** (development/testing only; abuse → account termination). **Commercial / production use requires a paid plan** (Essential and above).
- Rate / quota (published 2026-08-20): Free 100 requests/day, max 10 articles/request, 12-hour delay, 30 days history, localhost CORS. Essential 1,000/day, max 25, real-time, history from 2020. Hitting the daily cap returns **HTTP 403**; counter resets 00:00 UTC.
- Copyright: article text and images remain with publishers / third parties. GNews does not transfer ownership. Images may be third-party copyrighted; the operator is solely responsible for rights to reuse media.
- Do not misrepresent the source. Do not republish full articles as your own news product. Do not create a competing news-API service from bulk downloads. Reasonable response caching for application performance is permitted.
- Attribution to GNews is **appreciated but not required** unless the subscription plan says otherwise.
- PII: do not send personal data in `q`. Logs: operation, status, sizes, counts — never `api_key`, never query text beyond length.
- Official-API-first: JDK HTTP to `gnews.io/api/v4` only. No HTML scrape of publishers or Google News.

## Implementation notes

- JDK `HttpClient` only. No GNews SDK / official libraries. TLS verified. `Redirect.NEVER`.
- Headers: `X-Api-Key: {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0 (gnews; support@studioquack.com)`. **User-Agent is required** and must identify StudioQuack plus a contact address.
- Never send query `apikey`.
- Body cap **~8 MiB** (`8_388_608` bytes). Timeout default 25 s.
- Retry: none on 4xx (except a **single** 429 that honors `Retry-After` once, then `{ error }`). No unbounded retry. 5xx → `{ error }` no auto-retry.
- Empty `api_key` → `{ error: "Missing GNews API key. Bind api_key in node parameters." }` — no HTTP.
- Empty `query` on `search` → `{ error: "query is required for search." }` — no HTTP.
- HTTP 401 → `Invalid GNews API key.` + `http_status: 401`.
- HTTP 403 → `GNews request limit reached for this plan. Counter resets 00:00 UTC.` + `http_status: 403`.
- Other 4xx/5xx → truncated provider message (240 chars), key redacted to `***`.
- Timeout → `GNews request timed out after {n}s.`
- Failures go on OUTPUT `{ error }`. Never throw past dispatch.
