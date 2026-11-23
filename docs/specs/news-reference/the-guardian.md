# The Guardian

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/the-guardian/`
- **Researched:** 2026-08-20
- **Official API:** Guardian Open Platform / Content API (`https://content.guardianapis.com`)
- **Docs:** https://open-platform.theguardian.com/documentation/ — Content: https://open-platform.theguardian.com/documentation/search — Tags: https://open-platform.theguardian.com/documentation/tag — Sections: https://open-platform.theguardian.com/documentation/section — Editions: https://open-platform.theguardian.com/documentation/edition — Single Item: https://open-platform.theguardian.com/documentation/item — Access: https://open-platform.theguardian.com/access/
- **Auth:** api_key — secret binding: `api_key` (query `api-key` only; vendor documents query auth)

## Scope

One node for the official Guardian Content API. Search the archive, fetch a single item by id, and list tags / editions / sections. The Open Platform HTML explorer is JS-rendered; wire names below are the current Content API query parameters (`api-key`, hyphenated filters).

Do not scrape theguardian.com HTML. Do not train models or redistribute beyond the operator’s key tier and Open Licence. **Developer keys are free for non-commercial use only.** Commercial use requires a commercial key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/search` | Keyword / filter search across content. |
| `item_get` | GET | `/{id}` | One content item (path id, e.g. `world/2026/aug/20/slug`). |
| `tags` | GET | `/tags` | Tag catalogue. |
| `editions` | GET | `/editions` | Edition catalogue (`uk`, `us`, `au`, `international`, …). |
| `sections` | GET | `/sections` | Section catalogue. |

Base origin: `https://content.guardianapis.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Open Platform key. Query `api-key` only. Never INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. `<= 0` → 25. Floor 1 s. |
| `default_page_size` | number | no | `10` | no | Default `page-size` (API max typically 200). |
| `default_order_by` | select | no | `newest` | no | Default `order-by` for search: `newest` \| `oldest` \| `relevance`. |
| `base_url` | string | no | `https://content.guardianapis.com` | no | Documented proxy. No key in this URL. |

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
| `query` | string | no | Wire `q`. Lucene-style keywords. Alias `q`. |
| `section` | string | no | Section id (`politics`, `technology`, …). |
| `tag` | string | no | Tag id(s), comma-separated (`type/article`, `tone/news`). |
| `from_date` | string | no | Wire `from-date` (`YYYY-MM-DD`). |
| `to_date` | string | no | Wire `to-date` (`YYYY-MM-DD`). |
| `order_by` | string | no | Wire `order-by`: `newest` \| `oldest` \| `relevance`. Else config `default_order_by`. |
| `page` | number | no | 1-based `page`. |
| `page_size` | number | no | Wire `page-size`. Clamp 1–200. Else `default_page_size`. |
| `show_fields` | string | no | Wire `show-fields` (comma-separated: `headline`, `trailText`, `byline`, `thumbnail`, `body`, `bodyText`, `standfirst`, `lastModified`, …). Do not request `body`/`bodyText` unless the operator’s licence covers reuse. |
| `show_tags` | string | no | Wire `show-tags`. |
| `show_blocks` | string | no | Wire `show-blocks`. |
| `show_elements` | string | no | Wire `show-elements`. |
| `show_references` | string | no | Wire `show-references`. |
| `lang` | string | no | Language filter when documented. |
| `query_fields` | string | no | Wire `query-fields`. |
| `star_rating` | number | no | Wire `star-rating`. |
| `production_office` | string | no | Wire `production-office`. |
| `use_date` | string | no | Wire `use-date` when documented. |

### `operation = item_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Content path id (`world/2026/aug/20/example-slug`). No leading slash. Empty / `..` / absolute URL → `{ error }` no HTTP. |
| `show_fields` | string | no | Same as search. |
| `show_tags` | string | no | Same as search. |
| `show_blocks` | string | no | Same as search. |
| `show_elements` | string | no | Same as search. |
| `show_references` | string | no | Same as search. |

GET `{base_url}/{id}` with query params. Reject ids containing `://` or `..`.

### `operation = tags`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `q`. |
| `type` | string | no | Tag type (`keyword`, `contributor`, `tone`, `series`, …). |
| `section` | string | no | Limit to a section. |
| `web_title` | string | no | Wire `web-title`. |
| `page` | number | no | 1-based. |
| `page_size` | number | no | Wire `page-size` (max 200). |
| `show_references` | string | no | Wire `show-references`. |

### `operation = editions`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `q`. |

### `operation = sections`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `q`. |

## Output fields (by operation)

Provider envelope is always `response`.

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `response.status` (`ok`). |
| `user_tier` | string | success | `response.userTier` (`developer` / commercial). |
| `total` | number | list ops | `response.total`. |
| `start_index` | number | list ops | `response.startIndex`. |
| `page_size` | number | list ops | `response.pageSize`. |
| `current_page` | number | list ops | `response.currentPage`. |
| `pages` | number | list ops | `response.pages`. |
| `order_by` | string | search | `response.orderBy`. |
| `results` | array | `search` / `tags` / `editions` / `sections` | `response.results`. Pass through ids, `type`, `sectionId`, `webTitle`, `webUrl`, `apiUrl`, `fields` (if requested). Cap array to this page only. |
| `content` | object | `item_get` | `response.content` (single item). |
| `error` | string | failure | Actionable; never the API key. |
| `http_status` | number | HTTP error | Provider status. |
| `retry_after_seconds` | number | 429 only | From `Retry-After` if parseable, else 60. Honor **once**. |

## Legal

- Access / key tiers: https://open-platform.theguardian.com/access/ (**verified 2026-08-20**).
  - **Developer (free, NON-COMMERCIAL only):** student dissertations, hackathons, nonprofit app developers. **Up to 1 call per second. Up to 500 calls per day.** Access to article text and 1,900,000+ pieces. **Free for non-commercial usage.**
  - **Commercial key required** for commercial enterprises and any commercial use of Guardian journalism, including app development, publishing on third-party sites, **training models for generative AI**, text and data mining, sentiment analysis even where content is not reproduced, and any products derived from Guardian content. Custom throttle/quota; access to text, images, audio, video, and the full store. Price depends on usage.
- Open Licence (limited free reuse, not a commercial API substitute): https://www.theguardian.com/info/2022/nov/01/open-licence-terms
  - Short quotation max **100 words** (no advertising/endorsement).
  - Students: coursework/dissertations not released for external publication.
  - Church/village/parish newsletters: articles max **500 words**.
  - Personal non-commercial blogs/sites: link back to theguardian.com **and** max **500 words**.
  - Preserve context, meaning, integrity; headlines/captions must not distort. Keep bylines, watermarks/metadata/analytics, disclaimers/corrections, external links.
  - Credit: **“Courtesy of Guardian News & Media Ltd”**. Display attribution such as **Powered by The Guardian** where the Open Platform branding requires it.
- Content reuse is **licensed, not owned**. GNM / licensors retain IP. Not all fields or images are freely commercial (images/audio/video are commercial-tier). Do not train or redistribute beyond the licence and key tier.
- PII: contributor tags and bylines are personal data — minimize. Logs: operation, status, sizes, `total` — never `api-key`.
- Official-API-first: JDK HTTP to `content.guardianapis.com` only. No scrape of theguardian.com.

## Implementation notes

- JDK `HttpClient` only. No official Guardian SDK required. TLS verified. `Redirect.NEVER`.
- Auth: query `api-key={api_key}` only. Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0 (the-guardian; support@studioquack.com)`.
- Wire names keep hyphens: `from-date`, `to-date`, `order-by`, `page-size`, `show-fields`, `show-tags`, `web-title`.
- Body cap **~8 MiB**. Timeout default 25 s. Default `page-size` 10; never unbounded loops over `pages` inside the handler.
- Retry: none on 4xx except a **single** 429 honoring `Retry-After` once (developer key is ~1 rps — callers must not tight-loop). No unbounded retry.
- Empty `api_key` → `{ error: "Missing Guardian API key. Bind api_key in node parameters." }` — no HTTP.
- `item_get` without a safe `id` → `{ error: "id is required (Guardian content path)." }` — no HTTP.
- HTTP 401/403 → invalid key or commercial-tier field not allowed on this key.
- Failures go on OUTPUT `{ error }`. Never throw past dispatch. Redact `api-key` to `***`.
