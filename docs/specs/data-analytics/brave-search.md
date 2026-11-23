# Brave Search

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/brave-search/`
- **Researched:** 2026-08-20
- **Official API:** Brave Search API `res/v1` (`api.search.brave.com`)
- **Docs:** https://api-dashboard.search.brave.com/documentation/quickstart
- **Auth:** api_key — secret binding: `api_key` (header `X-Subscription-Token`)

## Scope

One node for Brave’s independent search index. The designer picks `operation`; the handler calls the matching `res/v1` REST path. Web / news / images / videos are Search-plan endpoints. `suggest` and `spellcheck` are query-assist endpoints. `llm_context` returns extracted page text for RAG (LLM Context API). `answer` is Brave **Answers / AI Grounding** (`/res/v1/chat/completions`, OpenAI-compatible) and is a **separate paid SKU** — skip the HTTP call with a clear error if the key’s plan does not include it.

Do not scrape search.brave.com HTML. Do not call Bing v7.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `web_search` | GET | `/res/v1/web/search` | Organic web results |
| `news_search` | GET | `/res/v1/news/search` | News articles from Brave’s index |
| `image_search` | GET | `/res/v1/images/search` | Image results |
| `video_search` | GET | `/res/v1/videos/search` | Video results |
| `suggest` | GET | `/res/v1/suggest/search` | Query autocompletion |
| `spellcheck` | GET | `/res/v1/spellcheck/search` | Spelling correction for a query |
| `llm_context` | GET or POST | `/res/v1/llm/context` | Extracted source text for an LLM (not an answer) |
| `answer` | POST | `/res/v1/chat/completions` | Grounded chat answer (Answers plan only) |

Base origin: `https://api.search.brave.com`. Prefer GET for search endpoints. For `llm_context`, use POST with JSON body when the query or goggles payload may exceed URL limits.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `web_search` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Subscription token. Header `X-Subscription-Token` only. Never on INPUT, never logged. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |
| `default_count` | number | no | `10` | no | Default result cap when input `count` omitted (web/news/images/videos: Brave max per endpoint). |
| `default_country` | string | no | empty | no | Default 2-letter country (`country`) for search/suggest/spellcheck/llm_context. |
| `default_search_lang` | string | no | empty | no | Default `search_lang` / `lang` where the endpoint supports it. |
| `safesearch` | select | no | `moderate` | no | `off` \| `moderate` \| `strict` where supported. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = web_search` | `news_search` | `image_search` | `video_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query (`q`). Trim; empty → `{ error }` no HTTP. Alias `q`. |
| `count` | number | no | Result count. Clamp to Brave’s published max for that endpoint (web typically 20). |
| `offset` | number | no | Pagination offset where the endpoint documents it. |
| `country` | string | no | 2-letter country; else config `default_country`. |
| `search_lang` | string | no | Language preference. |
| `freshness` | string | no | `pd` / `pw` / `pm` / `py` or `YYYY-MM-DDtoYYYY-MM-DD` as documented. |
| `safesearch` | string | no | Overrides instance `safesearch`. |

### `operation = suggest`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Partial query (`q`) |
| `count` | number | no | Suggestion count |
| `country` | string | no | 2-letter country |
| `rich` | boolean | no | `true` → richer suggestion metadata |

### `operation = spellcheck`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Query to correct (`q`) |
| `country` | string | no | 2-letter country |

### `operation = llm_context`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Query (`q`), 1–400 chars / max 50 words per Brave docs |
| `count` | number | no | Max results to consider (1–50, default 20) |
| `country` | string | no | Country |
| `search_lang` | string | no | Language |
| `freshness` | string | no | Same tokens as web search |
| `context_threshold_mode` | string | no | `strict` \| `balanced` \| `lenient` \| `disabled` |
| `enable_source_metadata` | boolean | no | Extra source metadata |

### `operation = answer`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | array | yes | OpenAI-style chat messages; at least one `user` turn |
| `model` | string | no | Model id as published on the Answers SKU; omit → Brave default |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `query` | string | search ops | Echo of `q` / original query object from Brave when present |
| `results` | array | search / suggest | Normalized hits: `title`, `url`, `description` (plus type-specific fields: `thumbnail`, `age`, `source`) |
| `suggestions` | array | `suggest` | Suggestion strings / rich objects as returned |
| `corrected` | string | `spellcheck` | Corrected query when Brave returns one |
| `context` | object | `llm_context` | Provider JSON (sources + extracted text). Cap body; do not flatten into logs. |
| `answer` | string | `answer` | Assistant message content |
| `citations` | array | `answer` | Citation URLs if present |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- Dashboard / docs: https://api-dashboard.search.brave.com/documentation/quickstart
- Authentication: https://api-dashboard.search.brave.com/documentation/guides/authentication
- LLM Context: https://api-dashboard.search.brave.com/documentation/services/llm-context
- Autosuggest: https://api-dashboard.search.brave.com/app/documentation/suggest
- Spellcheck: https://api-dashboard.search.brave.com/app/documentation/spellcheck
- Operator must accept Brave Search API terms in the dashboard. Result snippets are third-party content; fetching a hit URL is a separate node (HTTP Request / Web Browser) and remains subject to that site’s terms.
- Rate limits and credits are plan-specific; confirm live in the Brave dashboard. HTTP 429 must not tight-loop inside the handler.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `X-Subscription-Token: {api_key}`, `Accept: application/json`, `Accept-Encoding: gzip`, `User-Agent: StudioQuack/1.0`.
- Empty `api_key` → `{ error: "Missing Brave API key. Bind api_key in node parameters." }` — no HTTP.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- 429 → `{ error, http_status: 429, retry_after_seconds: 60 }` (honor `Retry-After` header if present). 5xx / timeout → `retry_after_seconds: 30`. Other 4xx → `{ error }` with truncated body (240 chars), key redacted to `***`.
- Do not auto-retry inside the handler.
- Logs: operation, status, result count, sizes — never `api_key` or query PII beyond length.
