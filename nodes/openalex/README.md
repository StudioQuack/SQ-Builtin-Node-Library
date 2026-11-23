# OpenAlex Search

In-process StudioQuack builtin (`templateId` `58e4aa32-8830-4a65-b5df-849823282972`, override with `SQ_TEMPLATE_ID_OPENALEX`). Handler: `com.studioquack.nodelibrary.nodes.data.OpenalexNode`.

## Overview

The node searches the OpenAlex scholarly catalog, fetches one work by identifier, or searches authors. It is a read-only HTTPS client for the **official OpenAlex REST API** at `https://api.openalex.org`. There is no scraping layer.

OpenAlex is OurResearch’s open scholarly graph (works, authors, sources, institutions, topics). Official authentication (https://developers.openalex.org/api-reference/authentication, updated 2026-08-19): send a free `api_key` as `api_key=` or `Authorization: Bearer`. Casual keyless queries still work; a free key raises the daily budget 10×. The `mailto` polite pool was retired in February 2026 and OpenAlex ignores `mailto=` (https://developers.openalex.org/guides/deprecations). This handler sends `api_key=` when configured and never sends `mailto=`. The `email` config param is kept for backward compatibility but is ignored.

Provider docs:

- API overview: [developers.openalex.org](https://developers.openalex.org)
- Authentication: [developers.openalex.org/api-reference/authentication](https://developers.openalex.org/api-reference/authentication)
- Deprecations (mailto polite pool retired): [developers.openalex.org/guides/deprecations](https://developers.openalex.org/guides/deprecations)
- Filter language: [developers.openalex.org](https://developers.openalex.org)
- Free API key: [openalex.org/settings/api](https://openalex.org/settings/api)
- About / terms: [openalex.org/about](https://openalex.org/about)

Needs outbound HTTPS egress to `api.openalex.org`. `jobType` is `custom-script-executor`.

## Ports

Two ports. The daemon delivers the invoke body; `DataSupport.readInput` unwraps `inputVariables.input` (or `currentNode.inputList` with `targetPortValue=input`). The handler always replies HTTP 200 with a JSON object keyed by `output`. There is no separate error port.

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | Per-invoke action + search/id fields. Auth is **not** on this port. |
| `output` | OUTPUT | object | yes | Success payload (`status=ok`) or `{error: string}`. Never contains `api_key`. |

### `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `action` | string | no | `search_works` | `search_works` \| `get_work` \| `search_authors` (case-sensitive) | Dispatch. Blank/omit → `search_works`. Any other string → `output.error`. |
| `query` | string | for search actions | — | non-empty UTF-8 | OpenAlex `search=` for `search_works` / `search_authors`. Alias `q` when `query` is blank. |
| `q` | string | no | — | same as `query` | Alias for `query`. |
| `work_id` | string | for `get_work` | — | OpenAlex id (`W2741809807`), OpenAlex URL, DOI URL (`https://doi.org/…`), or `pmid:21994122` | Path segment of `GET /works/{id}` (URL-encoded). |
| `filter` | string | no | omitted | OpenAlex filter expression; comma-separated clauses | Sent as `filter=` on search actions. Not sent for `get_work`. |
| `sort` | string | no | `relevance_score:desc` | `field:direction`, e.g. `cited_by_count:desc`, `publication_date:desc` | Sent as `sort=` on `search_works` only. |
| `per_page` | number | no | `50` | integer; handler sends `min(value, 100)` | Page size for search actions. |
| `page` | number | no | `1` | 1-based integer | Offset page for search actions. Cursor pagination is not implemented. |

### `output` schema

| Field | Type | When present | Purpose |
| --- | --- | --- | --- |
| `status` | string | success | Always `ok`. Absent on failure. |
| `action` | string | success | Echo of the dispatched action. |
| `query` | string | `search_works`, `search_authors` | The search string that was sent. |
| `total_results` | number | search actions | OpenAlex `meta.count` (catalog total, not this page). |
| `works_returned` | number | `search_works` | `works.length` (0–100). |
| `authors_returned` | number | `search_authors` | `authors.length`. |
| `page` | number | `search_works` | Echo of the requested page. |
| `works` | array | `search_works` | Shaped work objects (not raw OpenAlex entities). |
| `work` | object | `get_work` | Raw OpenAlex work JSON. |
| `authors` | array | `search_authors` | Shaped author objects. |
| `error` | string | failure only | Validation or `OpenAlex API Error: …`. HTTP 403/429 append quota/key hints. |

Shaped `works[]` keys: `openalex_id`, `doi` (`https://doi.org/` prefix stripped), `title`, `authors[]` (display names), `institutions[]` (unique, first 10), `publication_date`, `publication_year`, `source`, `type`, `cited_by_count`, `is_oa`, `oa_url`, `topics[]` (first 5 `display_name`), `keywords[]` (first 10 `display_name`), `abstract` (reconstructed inverted index, max 2000 chars), `referenced_works_count`.

Shaped `authors[]` keys: `openalex_id`, `display_name`, `works_count`, `cited_by_count`, `h_index` (`summary_stats.h_index`), `affiliations[]` (first 5), `topics[]` (first 5).

## Configuration parameters

Instance `configurationParameters` only. Do not put secrets on `input`.

| Name | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `api_key` | password | no | `""` (blank) | OpenAlex API key from [openalex.org/settings/api](https://openalex.org/settings/api) | When non-blank after trim, sent as query `api_key=`. Never logged or returned. Recommended for any real scale. |
| `email` | string | no | `""` | contact email | **Deprecated / ignored.** OpenAlex retired the mailto polite pool in Feb 2026 and ignores `mailto=`. Kept so existing instances do not break. Prefer `api_key`. |
| `timeout_seconds` | number | no | `25` | positive integer (seconds). `0`, negative, or blank → 25 | Connect+read timeout for each OpenAlex GET. Keep at or below the sandbox request budget. |

Blank `api_key` → unauthenticated casual budget (official docs). The deprecated `email` field does not change that.

## How it works

1. The library listener receives POST `/invoke`. `NodeLibraryApp` reads `templateId` and looks up `OpenalexNode` in `NodeRegistry` (`Data2NodeHandlers.register`). Unknown ids return HTTP 200 `{error:"unknown templateId: …"}`.
2. `handle` reads config (`api_key`, `timeout_seconds`) and input (`action`, `query`/`q`, `work_id`, `filter`, `sort`, `per_page`, `page`). `email` is present on the instance for compatibility and is not read.
3. Dispatch on `action`:
   - `search_works` → `GET https://api.openalex.org/works?search=&per_page=&page=&sort=&filter=`
   - `get_work` → `GET https://api.openalex.org/works/{urlEncoded(work_id)}`
   - `search_authors` → `GET https://api.openalex.org/authors?search=&per_page=&page=&filter=` (no `sort`)
4. `authParams` sends `api_key=` when the key is non-blank; otherwise no auth query param. `mailto=` is never sent.
5. Search hits are shaped. Abstracts are rebuilt from OpenAlex `abstract_inverted_index` (word → position list): tokens sorted by position, joined with spaces, then **capped at 2000 characters**. Empty index → empty string.
6. Failures stay on `output.error` (handler never throws). HTTP 403 appends a free-key hint; HTTP 429 appends a rate-limit hint (daily budget or 100 req/s). `api_key` literals are redacted.

OpenAlex `concepts` is deprecated; filter with topics/keywords. Offset `page` is what this node sends; cursor pagination for >10k hits is not implemented.

## Security & legal

This is an **official** public API. Follow [OpenAlex about / license](https://openalex.org/about) and [authentication](https://developers.openalex.org/api-reference/authentication).

- Prefer a free API key. `mailto` is ignored by OpenAlex (polite pool retired Feb 2026).
- Daily budget is usage-based; a free key is 10× the keyless budget. HTTP 429 is a stop signal (daily budget or >100 req/s) — do not tight-loop.
- `api_key` lives only in `configurationParameters` (password). It is never logged, never copied onto `output`, and is redacted from error strings.
- The node is read-only. It does not write the catalog.
- Outbound calls leave the customer sandbox toward `api.openalex.org`. The customer owns that egress and any personal data in queries (author names).

## Examples

### `search_works`

Instance config: `api_key` set (recommended); `timeout_seconds` = 25.

Input:

```json
{
  "action": "search_works",
  "query": "large language models",
  "filter": "publication_year:2024,open_access.is_oa:true",
  "sort": "cited_by_count:desc",
  "per_page": 25,
  "page": 1
}
```

Output (shape):

```json
{
  "status": "ok",
  "action": "search_works",
  "query": "large language models",
  "total_results": 184320,
  "works_returned": 25,
  "page": 1,
  "works": [
    {
      "openalex_id": "https://openalex.org/W2741809807",
      "doi": "10.1038/nature12373",
      "title": "Example paper",
      "authors": ["Ada Lovelace"],
      "institutions": ["University of Example"],
      "publication_date": "2024-03-01",
      "publication_year": 2024,
      "source": "Nature",
      "type": "article",
      "cited_by_count": 1200,
      "is_oa": true,
      "oa_url": "https://example.edu/pdf",
      "topics": ["Natural language processing"],
      "keywords": ["transformer"],
      "abstract": "Reconstructed abstract text…",
      "referenced_works_count": 42
    }
  ]
}
```

### `get_work`

```json
{
  "action": "get_work",
  "work_id": "W2741809807"
}
```

Success: `{ "status": "ok", "action": "get_work", "work": { /* raw OpenAlex work */ } }`.

`work_id` also accepts `https://doi.org/10.1038/s41586-021-03819-2` or `pmid:21994122`.

### `search_authors`

```json
{
  "action": "search_authors",
  "query": "Geoffrey Hinton",
  "per_page": 10
}
```

Success includes `authors[]` with `display_name`, `works_count`, `cited_by_count`, `h_index`, `affiliations`, `topics`.
