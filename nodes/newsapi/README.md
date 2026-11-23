# NewsAPI Search

## Overview

In-process StudioQuack builtin (`templateId` `6c601741-a3ce-4a08-86c8-a684cfda7b58`, override `SQ_TEMPLATE_ID_NEWSAPI`). Handler: `NewsapiNode`. It searches news articles through the official [NewsAPI.org](https://newsapi.org) REST API (`https://newsapi.org/v2`).

Use it for market / competitor monitoring: full-text search across NewsAPI’s source index, or breaking headlines by category and country. Authentication is a **required** instance password `api_key`, sent only as `X-Api-Key`. Failures raise a BPMN error.

Provider docs: [NewsAPI documentation](https://newsapi.org/docs), [everything](https://newsapi.org/docs/endpoints/everything), [top-headlines](https://newsapi.org/docs/endpoints/top-headlines), [terms](https://newsapi.org/terms) (attribution + developer-plan limits), [register](https://newsapi.org/register).

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | `inputVariables.input` (list-of-one unwraps). Fields below. **At least one** of `query` (or `q`), `q_in_title`, `sources`, or `domains` is required at runtime even if the designer marks `query` required. Do not put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `{status, total_results, articles_returned, articles}`. Failures raise a BPMN error. |

### Input fields

Incoming data chooses the path (`endpoint`) and the search window. `from` / `to` / `q_in_title` / `domains` / `sort_by` apply only to `everything`. `category` / `country` apply only to `top-headlines`.

| Field | Type | Required | Default | Accepted values | How it is used |
|---|---|---|---|---|---|
| `query` | string | at-least-one-of | — | NewsAPI `q` syntax (`AND`/`OR`/`NOT`, quoted phrases) | Sent as `q` when non-blank. Alias `q` when `query` is blank. |
| `q_in_title` | string | at-least-one-of | — | same operators | Sent as `qInTitle` on **everything** only. Title-only; does not search body. |
| `sources` | string | at-least-one-of | — | comma-separated NewsAPI source IDs (max 20) | Sent as `sources` on both paths. Cannot combine with `country` or `category` on top-headlines (NewsAPI HTTP 400). Use the `/top-headlines/sources` catalogue — arbitrary publisher names are rejected. |
| `domains` | string | at-least-one-of | — | comma-separated hosts, no scheme | Sent as `domains` on **everything** only. |
| `endpoint` | string | no | `everything` | `everything` \| `top-headlines` (case-sensitive) | Only the exact token `top-headlines` switches path; any other string is `everything`. |
| `from` | string | no | omitted | ISO 8601 date or datetime | NewsAPI `from` on everything only. Free tier typically allows about one month of history. |
| `to` | string | no | omitted | same formats | NewsAPI `to` on everything only. `to` before `from` → HTTP 4xx. |
| `language` | string | no | config `default_language` (`en`) | `ar` \| `de` \| `en` \| `es` \| `fr` \| `he` \| `it` \| `nl` \| `no` \| `pt` \| `ru` \| `sv` \| `zh` | Always sent on everything (including the default). On top-headlines sent when non-blank after defaulting. Unknown codes typically 400. |
| `sort_by` | string | no | config `default_sort_by` (`relevancy`) | `relevancy` \| `publishedAt` \| `popularity` | Wire name `sortBy`. Everything only. |
| `page_size` | number | no | config `default_page_size` (20) | 1–100 (NewsAPI max 100) | Wire name `pageSize`. Both endpoints. Values above 100 typically 400. |
| `page` | number | no | `1` | 1-based integer | Both endpoints. Out-of-range pages typically 400. No cursor. |
| `category` | string | no | omitted | `business` \| `entertainment` \| `general` \| `health` \| `science` \| `sports` \| `technology` | Top-headlines only. Cannot combine with `sources`. |
| `country` | string | no | omitted | ISO 3166-1 alpha-2 lowercase (`us`, `gb`, …) | Top-headlines edition country (not language). Cannot combine with `sources`. |

**At-least-one-of rule:** if `query`/`q`, `q_in_title`, `sources`, and `domains` are all blank, the handler raises `INVALID_INPUT` `At least one of query, q_in_title, sources, or domains is required.` and does not call NewsAPI. A sources-only or domains-only request is valid at runtime.

### Output fields

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | string | success | Exact `ok`. Absent on failure. |
| `total_results` | number | success | NewsAPI `totalResults` (index size, may exceed this page). |
| `articles_returned` | number | success | `articles.length` on this page. |
| `articles` | array | success | Mapped hits. Each object has trimmed strings: `title`, `description`, `url`, `source` (from `source.name`), `author`, `published_at` (from `publishedAt`), `content`. Missing JSON → `""`. Free-tier `content` is often truncated. |

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `api_key` | password | **yes** | `""` | NewsAPI key | Sent only as header `X-Api-Key`. Blank → `INVALID_INPUT` `Missing NewsAPI API key. Provide it in node parameters (api_key).` (no outbound call). Never a query param. |
| `base_url` | string | no | `https://newsapi.org/v2` | origin including `/v2`; trailing slashes stripped | Change only for a documented proxy that speaks the same REST shape. Do not put the key in this URL. |
| `default_language` | string | no | `en` | same ISO 639-1 tokens as `language` | Used when input `language` is blank. |
| `default_page_size` | number | no | `20` | 1–100 | Used when input `page_size` is omitted or not positive. |
| `default_sort_by` | select | no | `relevancy` | `relevancy` \| `publishedAt` \| `popularity` | Used when input `sort_by` is blank. Ignored for top-headlines. |
| `timeout_seconds` | number | no | `25` | positive int; `<= 0` → 25 | Connect+read timeout. On expiry: `NewsAPI request timed out after {n}s.` |

## How it works

1. The catalog registers `NewsapiNode` under `6c601741-a3ce-4a08-86c8-a684cfda7b58` (or `SQ_TEMPLATE_ID_NEWSAPI`).
2. `handle` reads config, then refuses a blank `api_key`, then applies the at-least-one-of rule.
3. Exact `endpoint=top-headlines` → `GET {base_url}/top-headlines`; otherwise `GET {base_url}/everything`.
4. Shared JDK `HttpClient` via `DataNodeSupport.get` (HTTP/1.1, redirects, request timeout, 8 MiB body cap).
5. Headers: `X-Api-Key: <key>`, `User-Agent: StudioQuack/1.0`. The key is never a query parameter.
6. Pagination is 1-based `page` + `pageSize` (NewsAPI max 100). There is no cursor.
7. Error mapping (BPMN codes):
   - missing key / missing query → `INVALID_INPUT`
   - HTTP 401 → `AUTH_FAILED` (`Invalid NewsAPI API key. HTTP 401.`)
   - HTTP 429 → `RATE_LIMITED` (`NewsAPI free tier daily limit exceeded. HTTP 429. Resume tomorrow.`)
   - other 4xx/5xx → `HTTP_<status>` `NewsAPI HTTP Error: HTTP {status} for url: {url}`
   - timeout → `REQUEST_FAILED` `NewsAPI request timed out after {n}s.`
   - other transport → `REQUEST_FAILED` `NewsAPI HTTP Error: {message}` (key redacted to `***` if present)
   - unexpected → `REQUEST_FAILED` `Error: {simpleName}: {message}` (key redacted)

## Security & legal

- The API key is read only from `configurationParameters.api_key`. It is never logged, never returned on `output`, never placed in the URL, and is stripped from error text if a transport message contains it.
- [NewsAPI Terms](https://newsapi.org/terms) require **attribution** when you display articles (credit NewsAPI / the publisher) and restrict commercial redistribution. The [Developer plan](https://newsapi.org/pricing) is **100 requests per day**, **24-hour article delay**, about one month of archive, truncated `content`, and **development/testing only** (not production). Official rate-limit status is HTTP **429**. Paid plans raise those caps; this node does not change behaviour by plan except for the HTTP statuses NewsAPI returns.
- The customer owns the NewsAPI account and is responsible for plan limits and attribution in downstream UI.
- This node uses the official REST API, not HTML scraping.

## Examples

### Everything (full-text)

Config: `api_key` set; other config at defaults.

Input:

```json
{
  "query": "\"workflow automation\" OR \"low-code\"",
  "endpoint": "everything",
  "language": "en",
  "sort_by": "publishedAt",
  "page_size": 20,
  "from": "2026-08-01"
}
```

Output (shape):

```json
{
  "status": "ok",
  "total_results": 1280,
  "articles_returned": 20,
  "articles": [
    {
      "title": "…",
      "description": "…",
      "url": "https://…",
      "source": "TechCrunch",
      "author": "…",
      "published_at": "2026-08-20T12:00:00Z",
      "content": "…"
    }
  ]
}
```

### Top headlines

Sources only (do not also set `country` or `category`):

```json
{
  "endpoint": "top-headlines",
  "sources": "techcrunch,the-verge",
  "page_size": 10
}
```

Query + category + country (valid because `query` satisfies the at-least-one-of rule):

```json
{
  "query": "AI",
  "endpoint": "top-headlines",
  "category": "technology",
  "country": "us",
  "page_size": 10
}
```
