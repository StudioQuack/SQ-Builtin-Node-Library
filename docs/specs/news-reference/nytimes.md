# NYTimes

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/nytimes/`
- **Researched:** 2026-08-20
- **Official API:** NYT Developer APIs (one node, multiple products, one `api_key`) — Article Search v2, Top Stories v2, Most Popular, Archive, Books v3
- **Docs:** Article Search https://developer.nytimes.com/docs/articlesearch-product/1/overview — Top Stories https://developer.nytimes.com/docs/top-stories-product/1/overview — Most Popular https://developer.nytimes.com/docs/most-popular-product/1/overview — Archive https://developer.nytimes.com/docs/archive-product/1/overview — Books https://developer.nytimes.com/docs/books-product/1/overview — APIs index https://developer.nytimes.com/apis — FAQ https://developer.nytimes.com/faq
- **Auth:** api_key — secret binding: `api_key` (query `api-key` only; NYT style)

## Scope

One node wrapping five official NYT JSON products that share a developer key. These APIs return **metadata** (headlines, abstracts, URLs, bylines, multimedia URLs) — **not full article body text**. Do not scrape nytimes.com HTML or bypass the paywall.

The developer portal pages are recaptcha-gated (WebFetch 2026-08-20 returned the portal chrome, not the spec body). Paths and query names below are from the live product URLs, the FAQ snippet, and NYT’s published `public_api_specs` (Top Stories, Archive, Books, Article Search). Re-check the portal when implementing.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `article_search` | GET | `/svc/search/v2/articlesearch.json` | Keyword / filter search (1851–present metadata). |
| `top_stories` | GET | `/svc/topstories/v2/{section}.json` | Current section-front / home stories. |
| `most_popular` | GET | `/svc/mostpopular/v2/{share_type}/{period}.json` | Most viewed / emailed / shared over 1, 7, or 30 days. |
| `archive` | GET | `/svc/archive/v1/{year}/{month}.json` | **Whole-month** metadata. Responses can be **~20 MB**. Size-cap; do not dump into engine vars. |
| `books_list` | GET | `/svc/books/v3/lists/{date}/{list}.json` | Best Sellers list for a date (use `current` for latest). |

Base origin: `https://api.nytimes.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `article_search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | NYT developer key. Query `api-key` only. Never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Archive may need a higher instance value; still cap the body. `<= 0` → 25. Floor 1 s. |
| `default_section` | string | no | `home` | no | Default Top Stories `section`. |
| `base_url` | string | no | `https://api.nytimes.com` | no | Documented proxy. No key in this URL. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key`. Archive must not place a full month blob on this port if over cap. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = article_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `q` (body/headline/byline). Alias `q`. |
| `fq` | string | no | Lucene filter query (`source:"The New York Times"`, `news_desk:(...)`, …). |
| `begin_date` | string | no | `YYYYMMDD`. |
| `end_date` | string | no | `YYYYMMDD`. |
| `sort` | string | no | `newest` \| `oldest` \| `relevance`. |
| `page` | number | no | 0-based. Max 100 (1,000 hits). |
| `fl` | string | no | Comma-separated field list. |
| `hl` | boolean | no | Highlight query terms when documented (`hl=true`). |
| `facet` | string | no | `true` / `false` to include facet counts. |
| `facet_fields` | string | no | `day_of_week`, `document_type`, `news_desk`, `pub_month`, `pub_year`, `section_name`, `source`, `subsection_name`, `type_of_material`, … |
| `facet_filter` | string | no | `true` / `false` — apply filters to facet counts. |

### `operation = top_stories`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `section` | string | no | Path `{section}`. Else config `default_section`. Allowed: `home`, `arts`, `automobiles`, `books`, `business`, `fashion`, `food`, `health`, `insider`, `magazine`, `movies`, `national`, `nyregion`, `obituaries`, `opinion`, `politics`, `realestate`, `science`, `sports`, `sundayreview`, `technology`, `theater`, `tmagazine`, `travel`, `upshot`, `world`. Reject anything else before HTTP. |

### `operation = most_popular`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `share_type` | string | yes | Path segment: `viewed` → `/viewed/{period}.json`; `emailed` → `/emailed/{period}.json`; `shared` → `/shared/{period}.json`. |
| `period` | number | yes | `1` \| `7` \| `30` (days). |
| `share_channel` | string | no | For `shared` only: extra path `/{period}/{share_channel}.json` when documented (`facebook`, …). Omit if blank. |

### `operation = archive`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `year` | number | yes | Four-digit year (1851+). |
| `month` | number | yes | 1–12 (example path `2016/11.json`). |

**Warn in the node description:** a month can be ~20 MB and thousands of docs. The handler **must** apply the ~8 MiB body cap and return `{ error }` if the response is truncated/capped — do **not** stream the whole month into engine variables. Prefer `article_search` with `begin_date`/`end_date` for bounded pulls.

### `operation = books_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list` | string | yes | List name, lowercase hyphenated (`hardcover-fiction`, `e-book-fiction`, …). Discover names via GET `/svc/books/v3/lists/names.json` (not a catalog verb — optional follow-up). |
| `date` | string | no | Path `{date}`: `YYYY-MM-DD` or `current` (default `current`). |
| `offset` | number | no | Pagination where the list endpoint documents it (history uses multiples of 20). |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | Provider `status` (`OK` / `ok`). |
| `copyright` | string | success | Provider `copyright` string — **pass through**; required for attribution. |
| `num_results` | number | top stories / most popular / books | Provider `num_results` when present. |
| `last_updated` | string | top stories | Provider `last_updated`. |
| `hits` | number | `article_search` | `response.meta.hits`. |
| `offset` | number | `article_search` | `response.meta.offset`. |
| `docs` | array | `article_search` | `response.docs` metadata only: `web_url`, `snippet`, `lead_paragraph`, `abstract`, `headline`, `byline`, `pub_date`, `section_name`, `news_desk`, `document_type`, `_id`, `word_count`, `multimedia` (URLs, not bytes). **No full article body.** |
| `results` | array | `top_stories` / `most_popular` | Provider `results[]`: `title`, `abstract`, `url`, `byline`, `section`, `published_date` / `updated_date`, `multimedia`. |
| `books` | array | `books_list` | From `results.books` (or equivalent): `title`, `author`, `rank`, `publisher`, buy links as returned, `isbns`. |
| `list_name` | string | `books_list` | `results.list_name` when present. |
| `bestsellers_date` | string | `books_list` | When present. |
| `archive_truncated` | boolean | `archive` | `true` if the body hit the size cap. |
| `error` | string | failure | Actionable; never the API key. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` if parseable, else **12** (FAQ: 5/min → sleep 12 s between calls). Honor **once**. |

## Legal

- Terms: https://developer.nytimes.com/terms — **URL live; page body recaptcha-blocked on 2026-08-20.** Operator must accept the live Terms of Use in the NYT developer portal.
- Branding / attribution: https://developer.nytimes.com/branding-attribution (also linked as https://developer.nytimes.com/branding and https://developer.nytimes.com/attribution). **URL live; body recaptcha-blocked on 2026-08-20.** **Must credit The New York Times** per branding guidelines when displaying data. Pass through the JSON `copyright` field. Do not imply NYT endorsement.
- FAQ https://developer.nytimes.com/faq (**cited 2026-08-20**):
  - Public developer APIs are for **non-commercial use only**. Commercial use: https://nytlicensing.com/contact/. Text & data mining: https://nytlicensing.com/data-solutions/.
  - Rate limits: **500 requests per day and 5 requests per minute** per API. Sleep ~12 seconds between calls to avoid 429. Higher limits: `code@nytimes.com`.
- APIs return **METADATA, not full article text**. **No full-article republish.** No paywall bypass. Do not present snippets as a substitute NYT product. Returned `url` points at NYT pages that may be paywalled — this node must not fetch those pages.
- Archive: official spec warns responses can be **~20 megabytes**. Size-cap in the handler. **Do not dump whole months into engine vars** (REF-only / bounded payloads).
- PII: bylines and person facets are personal data — minimize. Logs: operation, product path, status, sizes — never `api-key`, never full `docs`.
- Official-API-first: JDK HTTP to `api.nytimes.com` only. No scrape of nytimes.com.

## Implementation notes

- JDK `HttpClient` only. No NYT SDK. TLS verified. `Redirect.NEVER`.
- Auth: query `api-key={api_key}` only. Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0 (nytimes; support@studioquack.com)`.
- Body cap **~8 MiB** for all ops. **Archive will often exceed this** — fail with `{ error: "NYT Archive response exceeded size cap (~8 MiB). Use article_search with begin_date/end_date instead of dumping a month." }` and `archive_truncated: true` if a partial parse is impossible. Do not raise the cap to 20 MiB to “make archive work”.
- Timeout default 25 s; archive may timeout before the cap — still `{ error }`, no retry storm.
- Retry: none on 4xx except a **single** 429 honoring `Retry-After` once. No unbounded retry. Do not loop `page` 0–100 inside the handler.
- Empty `api_key` → `{ error: "Missing NYTimes API key. Bind api_key in node parameters." }` — no HTTP.
- HTTP 401 → `Invalid NYTimes API key.`
- HTTP 429 → `NYTimes rate limit (typically 5/min or 500/day).` + `retry_after_seconds`.
- Failures go on OUTPUT `{ error }`. Never throw past dispatch. Redact the key to `***`.
