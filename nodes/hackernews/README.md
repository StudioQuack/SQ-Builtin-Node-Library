# Hacker News Search

## Overview

In-process StudioQuack builtin (`templateId` `ea8652a8-a1f5-4101-aa7d-65a2f9c097d4`, override `SQ_TEMPLATE_ID_HACKERNEWS`). Handler: `HackernewsNode`. It searches Hacker News stories and comments through the public, sanctioned [HN Search API](https://hn.algolia.com/api) (Algolia) at `http://hn.algolia.com/api/v1` (**HTTP**, not HTTPS — that is the documented endpoint).

No API key. Use this when you already know a search phrase; it is not a full HN scrape and it does not call `news.ycombinator.com` HTML.

Provider docs: [HN Algolia API](https://hn.algolia.com/api), [HN Search](https://hn.algolia.com/).

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | `inputVariables.input`. **Required:** `query` (or alias `q`). Optional: `action`, `tags`, `numeric_filters`, `hits_per_page`, `page`. Empty query fails before any HTTP call. |
| `output` | OUTPUT | object | yes | Success: `{status, total_hits, total_pages, stories_returned, stories}`. Failure: `{error}` only. No error OUTPUT port. |

### Input fields

Incoming `query` is sent as Algolia `query`. `action` selects relevance vs recency. `tags` and `numeric_filters` narrow the index; `page` / `hits_per_page` walk the first ~1,000 hits.

| Field | Type | Required | Default | Accepted values | How it is used |
|---|---|---|---|---|---|
| `query` | string | **yes** | — | search phrase (titles, URLs, story text, comments depending on tags) | Sent as Algolia `query`. Alias `q` when `query` is blank. Both empty → `query is required.` |
| `action` | string | no | `search` | `search` \| `search_by_date` (case-sensitive) | `search` → `GET /search` (relevance). `search_by_date` → `GET /search_by_date` (newest first). Any other string still hits `/search` (not an error). |
| `tags` | string | no | `story` | `story`, `comment`, `show_hn`, `ask_hn`, `poll`, `front_page`; `author_USERNAME`; `story_ID`; comma = AND; `(a,b)` = OR | Sent as Algolia `tags`. Example: `story,author_pg`. Example comments on a thread: `comment,story_123`. |
| `numeric_filters` | string | no | omitted | Algolia `numericFilters` on `points`, `num_comments`, `created_at_i` (Unix seconds); operators `<` `<=` `>` `>=` `=`; commas AND | Omitted when blank. Use `created_at_i` windows to stay under the ~1,000-hit ceiling. |
| `hits_per_page` | number | no | config `default_hits_per_page` (30) | integer; **hard-capped at 50** | Wire name `hitsPerPage`. Values above 50 are clamped, not rejected. |
| `page` | number | no | `0` | integer `>= 0` | **0-indexed** (page `0` is the first page). Do not send `1` expecting the first page. Keep `page * hits_per_page < 1000`. |

### Output fields

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `total_hits` | number | success | Algolia `nbHits`. May exceed 1,000 even though later pages are empty. |
| `total_pages` | number | success | Algolia `nbPages` for the current `hitsPerPage`. Indexes run `0 .. total_pages-1`. |
| `stories_returned` | number | success | `stories.length` on this page (0 is a valid ok response). |
| `stories` | array | success | Mapped hits in API order. |
| `error` | string | failure | See error mapping. |

Each `stories[]` item:

| Field | Type | Meaning |
|---|---|---|
| `object_id` | string | Algolia `objectID` (HN item id). |
| `title` | string | `title` if non-empty, else `story_title`, else `""`. |
| `url` | string | External article URL, or `""` for Ask/Show text posts. Not the HN discussion URL. |
| `points` | number | Score; `0` when omitted. |
| `num_comments` | number | Comment count; `0` when omitted. |
| `author` | string | HN username. |
| `created_at` | string | Algolia timestamp (typically ISO-8601 UTC). For filters use `created_at_i`, not this field. |
| `story_text` | string | Self-post body, truncated to 500 characters (no ellipsis). |
| `comment_text` | string | Comment text, truncated to 500 characters. |
| `hn_url` | string | `https://news.ycombinator.com/item?id={objectID}`. |

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `timeout_seconds` | number | no | `25` | positive int; `<= 0` → 25 | Connect+read timeout for the Algolia GET. |
| `default_hits_per_page` | number | no | `30` | 1–50 (still clamped to 50) | Used when input `hits_per_page` is omitted. |

No API key parameter exists.

## How it works

1. The catalog registers `HackernewsNode` under `ea8652a8-a1f5-4101-aa7d-65a2f9c097d4` (or `SQ_TEMPLATE_ID_HACKERNEWS`).
2. `handle` requires `query`/`q`, defaults `action` to `search` and `tags` to `story`, clamps `hits_per_page` to 50, and treats `page` as 0-based (so `0` is not replaced by a default).
3. `GET http://hn.algolia.com/api/v1/search` or `…/search_by_date` through the shared JDK `HttpClient` (`DataNodeSupport.get`: HTTP/1.1, redirects, request timeout, 8 MiB body cap). Query params: `query`, `tags`, `hitsPerPage`, `page`, and optional `numericFilters`.
4. Headers: `User-Agent: StudioQuack/1.0` (same product UA as the other data nodes). No `Authorization`.
5. Pagination is 0-based `page` + `hitsPerPage`. Algolia generally exposes only the first **1,000 hits** per query — narrow with `numeric_filters` on `created_at_i`.
6. Failures stay on `output.error`; `handle` never throws:
   - `query is required.`
   - `HackerNews Algolia API Error: HTTP {status} for url: {url}`
   - `HackerNews Algolia API Error: {detail}` (timeout / transport; timeout detail is `timed out after {n}s`)
   - `Error: {simpleName}: {message}`
7. Helper logs record `hackernews start` / `done` plus `action` and `stories_returned` — never the query text, never `helperToken`.

## Security & legal

- No customer secret is involved. Do not log query text in custom wrappers; this handler does not.
- Use the [official HN Search API](https://hn.algolia.com/api) (this node). Do not scrape `news.ycombinator.com`. [HN Guidelines](https://news.ycombinator.com/newsguidelines.html) and YC’s site terms still apply to how you reuse content.
- There is no published API key quota; keep `hits_per_page` modest and avoid tight polling loops. The 1,000-hit ceiling is an Algolia/HN Search limit, not a StudioQuack cap.
- Traffic is identifiable via `User-Agent: StudioQuack/1.0`.

## Examples

### Recency search with filters

Input:

```json
{
  "query": "workflow rate limit",
  "action": "search_by_date",
  "tags": "story",
  "numeric_filters": "points>10,created_at_i>1700000000",
  "hits_per_page": 30,
  "page": 0
}
```

Output (shape):

```json
{
  "status": "ok",
  "total_hits": 12,
  "total_pages": 1,
  "stories_returned": 12,
  "stories": [
    {
      "object_id": "39926122",
      "title": "…",
      "url": "https://…",
      "points": 42,
      "num_comments": 7,
      "author": "pg",
      "created_at": "2026-01-01T00:00:00.000Z",
      "story_text": "",
      "comment_text": "",
      "hn_url": "https://news.ycombinator.com/item?id=39926122"
    }
  ]
}
```

### Relevance search, first page

Input: `{"query":"workflow automation","action":"search","tags":"story","hits_per_page":10,"page":0}` → same envelope, ranked by relevance instead of recency.
