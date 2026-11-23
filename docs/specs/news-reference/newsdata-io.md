# NewsData.io

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/newsdata-io/`
- **Researched:** 2026-08-20
- **Official API:** NewsData.io News API (`https://newsdata.io/api/1`)
- **Docs:** https://newsdata.io/documentation
- **Auth:** api_key — secret binding: `api_key` (header `X-ACCESS-KEY` preferred; vendor also documents query `apikey`)

## Scope

One node for the official NewsData.io REST API. Current documented article endpoints: `/latest` (last ~48 hours), `/crypto`, `/market`, `/archive` (paid), plus `/sources`. Auth may be header `X-ACCESS-KEY` **or** query `apikey` — **use the header only**.

`/news` appears in older “Latest/News” wording in the response-object notes; **do not ship a `news` operation** — current resource URLs are `/latest`. `/count` and WebSocket (coming soon) are out of catalog scope. Catalog verbs: `latest`, `crypto`, `sources`, `archive`. **`market` is included** because it is documented as current on 2026-08-20.

Do not scrape publisher HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `latest` | GET | `/latest` | Breaking / last 48 hours. |
| `crypto` | GET | `/crypto` | Crypto / blockchain news (`coin`, …). |
| `sources` | GET | `/sources` | Convenience catalogue (up to ~100 domains for the filters). |
| `archive` | GET | `/archive` | Historical (paid). **5 API credits per request.** |
| `market` | GET | `/market` | Financial / market news (`market_id` = `TICKER-COUNTRY-EXCHANGE`). |

Base origin: `https://newsdata.io/api/1`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `latest` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | NewsData.io key. Header `X-ACCESS-KEY` only. Never query `apikey`, never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. `<= 0` → 25. Floor 1 s. |
| `default_language` | string | no | empty | no | Default `language` when input omitted. |
| `default_size` | number | no | empty | no | Default `size` (free 1–10, paid 1–50). If blank, omit `size` (provider default 10 free / 50 paid). |
| `base_url` | string | no | `https://newsdata.io/api/1` | no | Documented proxy. No key in this URL. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

Mutual exclusion: only one of `q`, `q_in_title` (`qInTitle`), `q_in_meta` (`qInMeta`). Only one of `country` / `excludecountry`, `language` / `excludelanguage`, `category` / `excludecategory`. Max character length for `q*` is plan-capped (free/basic 100, professional 256, corporate 512; docs also say 512 max — send as given, surface HTTP 400).

### `operation = latest`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | no | Comma-separated `article_id`s (up to 50). |
| `query` | string | no | Wire `q`. Alias `q`. URL-encode. |
| `q_in_title` | string | no | Wire `qInTitle`. |
| `q_in_meta` | string | no | Wire `qInMeta`. |
| `timeframe` | string | no | Hours `1`–`48` or minutes `1m`–`2880m` (paid feature on many plans). |
| `country` | string | no | Comma-separated country codes (free/basic ≤5, pro/corp ≤10). |
| `excludecountry` | string | no | Exclude countries; not with `country`. |
| `category` | string | no | e.g. `sports,top`. |
| `excludecategory` | string | no | Not with `category`. |
| `language` | string | no | Else config `default_language`. |
| `excludelanguage` | string | no | Not with `language`. |
| `sort` | string | no | `pubdateasc` \| `relevancy` \| `source` \| `fetched_at`. Default newest `pubDate`. |
| `datatype` | string | no | `news`,`blog`,`multimedia`,`forum`,`pressrelease`,`review`,`research`,`opinion`,`analysis`,`podcast`. |
| `creator` | string | no | Author names, comma-separated. |
| `url` | string | no | Exact article URL. |
| `domain` | string | no | Source ids (`nytimes,bbc`). |
| `domainurl` | string | no | Hosts (`nytimes.com`). |
| `excludedomain` | string | no | Exclude hosts. |
| `excludefield` | string | no | Drop response fields (`pubdate,source_icon`). Cannot exclude article id. |
| `prioritydomain` | string | no | `top` \| `medium` \| `low`. |
| `timezone` | string | no | IANA tz (`America/New_York`). |
| `full_content` | string | no | `1` / `0`. Full content is a **paid** plan feature. |
| `image` | string | no | `1` / `0` featured image. |
| `video` | string | no | `1` / `0`. |
| `removeduplicate` | string | no | `1` to drop duplicates (provider algorithm). |
| `size` | number | no | 1–50 (free max 10). Else `default_size`. |
| `page` | string | no | **Cursor**: `nextPage` token from the previous response, not a 1-based index. |
| `tag` | string | no | AI tags (Professional/Corporate). |
| `sentiment` | string | no | `positive` \| `negative` \| `neutral` (Pro/Corporate). |
| `sentiment_score` | number | no | With `sentiment` (Pro/Corporate). |
| `organization` | string | no | Corporate. |
| `region` | string | no | Corporate geo. |

### `operation = crypto`

Same filters as `latest` that the crypto table documents (`q`, `qInTitle`, `qInMeta`, `from_date`, `to_date`, `language`, `domain`/`domainurl`, `size`, `page`, `id`, `url`, …), plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `coin` | string | no | Crypto symbols (`btc`, `eth,usdt,bnb`). Free/basic ≤5, pro/corp ≤10. |
| `from_date` | string | no | `YYYY-MM-DD` or `YYYY-MM-DD HH:MM:SS`. |
| `to_date` | string | no | Same formats. |

Do not send `timeframe` unless the live crypto table still lists it (latest uses timeframe; crypto table uses `from_date`/`to_date`).

### `operation = archive`

Paid only. 5 credits per request.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `q`. |
| `q_in_title` | string | no | Wire `qInTitle`. |
| `q_in_meta` | string | no | Wire `qInMeta`. |
| `from_date` | string | no | Start. If omitted, plan default history window. |
| `to_date` | string | no | End; default today. |
| `language` | string | no | Same caps as latest. |
| `country` | string | no | Same caps. |
| `category` | string | no | Same caps. |
| `id` | string | no | Article ids. |
| `url` | string | no | Exact URL. |
| `domain` / `domainurl` / `excludedomain` | string | no | Same as latest. |
| `size` | number | no | 1–50. |
| `page` | string | no | `nextPage` cursor. |
| `full_content` | string | no | `1` / `0` (paid). |

History depth is plan-gated (docs table: Basic ~6 months, Professional ~2 years, Corporate ~10 years).

### `operation = sources`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `language` | string | no | Filter sources. |
| `country` | string | no | Filter sources. |
| `category` | string | no | Filter sources. |
| `domainurl` | string | no | Specific hosts. |
| `prioritydomain` | string | no | `top` \| `medium` \| `low`. |

### `operation = market`

Same article filters as documented on `/market` (`q`, dates, country, language, domain*, size, page, datatype, sentiment*, …), plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `market_id` | string | no | `TICKER-COUNTRY-EXCHANGE` (`AAPL-US-NASDAQ`). Up to 5 (10 on Pro/Corp). Prefer this over ticker-only. |
| `organization` | string | no | Corporate. |
| `from_date` / `to_date` | string | no | Date window. |

Docs still show example URLs with `symbol=`; the 2026 “What’s New” replaces `symbol` with `market_id`. **Send `market_id`, not `symbol`.**

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | Provider `status` (`success`). |
| `total_results` | number | article ops | `totalResults`. |
| `next_page` | string | article ops | `nextPage` cursor for the next `page` input. Empty if last page. |
| `results` | array | article ops | Mapped `results[]`: `article_id`, `title`, `link`, `source_id`, `source_name`, `source_url`, `source_icon`, `source_priority`, `keywords`, `creator`, `image_url`, `video_url`, `description`, `pub_date` (`pubDate`), `pub_date_tz` (`pubDateTZ`), `content`, `country`, `category`, `datatype`, `fetched_at`, `language`, `duplicate`, `coin` (crypto), plus plan fields `ai_tag`, `sentiment`, `sentiment_stats`, `ai_region`, `ai_org`, `ai_summary`, `symbol` when present. Keep `link` / `source_url`. |
| `sources` | array | `sources` | `id`, `name`, `url`, `icon`, `priority`, `description`, `category`, `language`, `country`. |
| `error` | string | failure | Actionable; never the API key. Prefer provider `results.message` / `code` when `status=error`. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` if parseable, else 900 (15-minute window). Honor **once**. |

## Legal

- Terms: https://newsdata.io/terms (also linked historically as terms-and-conditions). NewsData.io is an aggregator; **publisher copyright remains with the originating sites**. NewsData.io does not grant a copyright licence to republish third-party text/media.
- Commercial use of the **API service** is plan-based (Free plan is usable commercially per product FAQ, with lower credits and no full content / archive). **Publishing full news content, images, or video as your own is a copyright violation.** Safe display fields per product FAQ: title, short description, publisher name, author, date/time — keep publisher URLs. Do not post full `content` or full-size images/videos as original reporting.
- Attribution: credit the original publisher (and NewsData.io where the dashboard/terms require). Always keep `link` / `source_url`.
- Quota (docs 2026-08-20): Free **200 credits/day**, 10 articles/credit, no Archive. Basic 20,000/month; Professional 50,000/month; Corporate 1,000,000/month; 50 articles/credit on paid. Archive consumes **5 credits** per request. Rate window: Free **30 credits / 15 min**; paid **1800 credits / 15 min**. HTTP 429 when exceeded.
- HTTP codes: 400 missing/invalid param; 401 unauthorized (docs also mention 403 for missing/invalid key or CORS/IP); 409 duplicate param; 415; 422; 429; 500.
- PII: creators/authors may be personal data. Logs: operation, status, `totalResults`, sizes — never `api_key`, never article bodies.
- Official-API-first: JDK HTTP to `newsdata.io/api/1` only. No HTML scrape.

## Implementation notes

- JDK `HttpClient` only. No NewsData SDK. TLS verified. `Redirect.NEVER`.
- Header: `X-ACCESS-KEY: {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0 (newsdata-io; support@studioquack.com)`. **Do not** also send query `apikey`.
- Body cap **~8 MiB**. Timeout default 25 s.
- Retry: none on 4xx except a **single** 429 honoring `Retry-After` once. No unbounded retry. Do not auto-walk `nextPage` inside the handler (one page per dispatch).
- Empty `api_key` → `{ error: "Missing NewsData.io API key. Bind api_key in node parameters." }` — no HTTP.
- If more than one of `q` / `qInTitle` / `qInMeta` is set → `{ error }` no HTTP.
- HTTP 401/403 → `Invalid NewsData.io API key.`
- HTTP 429 → `NewsData.io rate limit exceeded.` + `retry_after_seconds`.
- Failures go on OUTPUT `{ error }`. Never throw past dispatch. Redact the key to `***`.
