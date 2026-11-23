# Semantic Scholar Search

In-process StudioQuack builtin (`templateId` `6509231e-2d5e-4a8c-9734-cac633074330`, override with `SQ_TEMPLATE_ID_SEMANTIC_SCHOLAR`). Handler: `com.studioquack.nodelibrary.nodes.data.SemanticScholarNode`.

## Overview

The node looks up scholarly papers, citing/cited works, and authors on the **official Semantic Scholar Academic Graph API** (`https://api.semanticscholar.org/graph/v1`). One invoke runs one action: keyword search, one paper, citations, references, one author, or a batch of paper ids.

Auth is optional. A non-blank instance `api_key` is sent only as HTTP header `x-api-key`. Official limits ([product/api](https://www.semanticscholar.org/product/api)): unauthenticated traffic shares a public pool documented at 1000 req/s (further throttled under load). An API key starts at **1 request/second** across all endpoints; higher after review.

Provider docs:

- Graph API: [api.semanticscholar.org](https://api.semanticscholar.org/)
- Graph docs: [api.semanticscholar.org/api-docs/graph](https://api.semanticscholar.org/api-docs/graph)
- API product / key request: [semanticscholar.org/product/api](https://www.semanticscholar.org/product/api)
- Terms of use / API license: [semanticscholar.org/product/api/license](https://www.semanticscholar.org/product/api/license)

Use this for scholarly metadata (title, authors, year, abstract, citation counts, open-access PDF, TLDR). Do not use it as a general HTTP client.

Needs outbound HTTPS egress to `api.semanticscholar.org`. `jobType` is `custom-script-executor`.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | Action + identifiers/filters. `query` may be sent as alias `q`. `paper_ids` may be a JSON array **or** a comma-separated string. |
| `output` | OUTPUT | object | yes | Success (`status=ok` + action-specific keys) or `{error}`. Never contains `api_key`. |

`DataSupport.readInput` unwraps `inputVariables.input`. No separate error port; the handler never throws.

### `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `action` | string | no | `search` | `search` \| `paper_details` \| `citations` \| `references` \| `author` \| `batch` (case-sensitive) | Dispatch. Blank → `search`. Other strings → `output.error` listing the six names. |
| `query` | string | for `search` | — | non-empty Unicode (Boolean/quoted phrases as S2 supports) | `GET /paper/search?query=`. Alias `q` when `query` is blank. |
| `q` | string | no | — | same as `query` | Alias for `query`. |
| `paper_id` | string | for `paper_details`, `citations`, `references` | — | S2 `paperId` (hex); `DOI:…`; `ARXIV:…` / `ArXiv:…`; `PMID:…`; `CorpusId:…`; `ACL:…`; `MAG:…` | URL-encoded path segment. |
| `paper_ids` | string or list | for `batch` | — | JSON array of id strings **or** CSV of the same id formats | Body `{"ids":[…]}` on `POST /paper/batch`. Empty → error. More than **500** ids are truncated, not rejected. |
| `author_id` | string | for `author` | — | Semantic Scholar authorId (digits, no spaces) | `GET /author/{id}`. |
| `fields` | string | no | node default paper field set | comma-separated Graph field names (title, authors, year, abstract, citationCount, …) | `fields=` on search, paper_details, and batch. Ignored by citations/references/author (those use fixed field sets). |
| `limit` | number | no | **100** | integer. `search` capped at 100; citations/references capped at 1000 | Page size. Omit/blank/non-numeric → 100. Ignored by paper_details, author, batch. |
| `offset` | number | no | `0` | integer ≥ 0 | `offset=` on search/citations/references. |
| `year` | string | no | omitted | `YYYY`, `YYYY-YYYY`, `YYYY-`, or `-YYYY` | Search-only `year=` filter. |
| `venue` | string | no | omitted | venue name/abbrev, or comma-separated names | Search-only `venue=` filter. |
| `openAccessPdf` | boolean | no | false | truthy → query param `openAccessPdf=` (empty value) | Honored on search even though it is not on the designer schema. |

### `output` schema

| Field | Type | When present | Purpose |
| --- | --- | --- | --- |
| `status` | string | success | Always `ok`. |
| `action` | string | success | Echo of the dispatch value. |
| `query` | string | `search` | Search string sent. |
| `total_results` | number | `search` | API `total` (estimated matches). |
| `papers_returned` | number | `search`, `batch` | Length of `papers` (batch includes JSON `null` slots). |
| `offset` | number | `search` | API offset echoed. |
| `papers` | array | `search`, `batch` | Raw S2 paper objects (`data[]` or batch array root). |
| `paper` | object | `paper_details` | Raw paper record. |
| `paper_id` | string | `citations`, `references` | Echo of the requested id. |
| `citations_returned` | number | `citations` | Length of `citations`. |
| `citations` | array | `citations` | Unwrapped `citingPaper` objects (missing → `{}`). |
| `references_returned` | number | `references` | Length of `references`. |
| `references` | array | `references` | Unwrapped `citedPaper` objects. |
| `author` | object | `author` | Raw author profile. |
| `error` | string | failure only | Validation, `Semantic Scholar API Error: …`, HTTP 429/404 suffixes, or `Error: TypeName: …`. |

Default paper fields: `title,authors,year,abstract,citationCount,influentialCitationCount,externalIds,venue,openAccessPdf,tldr,publicationDate,journal,fieldsOfStudy,s2FieldsOfStudy,isOpenAccess,referenceCount`.

Citation/reference fields (fixed): `title,authors,year,citationCount,externalIds,venue`.

Author fields (fixed): `name,affiliations,paperCount,citationCount,hIndex,externalIds`.

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `api_key` | password | no | `""` | key from [product/api](https://www.semanticscholar.org/product/api), or empty | When non-blank, every Graph call sends `x-api-key`. Blank → no header (public limits). Never logged or copied onto `output.error`. Bind a StudioQuack secret; do not commit a production key. |
| `timeout_seconds` | number | no | `25` | positive integer. 0 / negative / blank → 25 | Connect+read timeout per Graph call. |

## How it works

1. Dispatch: POST `/invoke` with `templateId` `6509231e-2d5e-4a8c-9734-cac633074330` → `SemanticScholarNode`.
2. `handle` reads `api_key` / `timeout_seconds`, builds headers (`x-api-key` only when the key is non-blank), reads `action` (default `search`), `fields` (default paper field set), `limit` (**default 100**), `offset` (default 0).
3. Switch on `action`:
   - `search` → `GET /graph/v1/paper/search` (`query`, `fields`, `limit=min(limit,100)`, `offset`, optional `year` / `venue` / empty `openAccessPdf`)
   - `paper_details` → `GET /graph/v1/paper/{encoded id}?fields=`
   - `citations` → `GET /graph/v1/paper/{id}/citations` (`limit=min(limit,1000)`)
   - `references` → `GET /graph/v1/paper/{id}/references`
   - `author` → `GET /graph/v1/author/{id}` with `AUTHOR_FIELDS` (`input.fields` ignored)
   - `batch` → `POST /graph/v1/paper/batch?fields=` with body `{"ids":[…]}`
4. **`paper_ids`**: `DataSupport.inStringList` accepts a JSON array **or** a comma-separated string. Empty list → `paper_ids array is required for batch action. Max 500.` More than 500 ids → truncated to 500. Batch response is a JSON **array** (including `null` for unknown ids), not an object.
5. Citations/references unwrap `citingPaper` / `citedPaper` so downstream sees paper objects, not wrappers.
6. HTTP 429 appends `| Rate limited. Get a free API key at api.semanticscholar.org for higher limits.` HTTP 404 appends `| Paper not found…` or `| Author not found…` by action.
7. Typed ids are normalized before the HTTP call (`ARXIV:` → `ArXiv:`, etc.). `paper_details` / `author` return `{error}` when the JSON body has no `paperId`/`title` or `authorId`/`name` (no empty `status=ok`).
8. Sub-process duplicate-call cache that replays prior results is runtime-owned — not this handler.

## Security & legal

This is an **official** Academic Graph API. Use is subject to the [Semantic Scholar API license](https://www.semanticscholar.org/product/api/license).

| Access | Typical limit |
| --- | --- |
| Unauthenticated | Shared public pool, documented at 1000 req/s (further throttled under load) |
| API key (introductory) | 1 request / second across all endpoints |

- Request a key rather than hammering the public pool. HTTP 429 is a stop signal.
- `api_key` lives only in `configurationParameters`. It is never logged and is redacted from `output.error`.
- The node is read-only. It does not write the graph.
- Queries may contain personal names and paper identifiers; outbound calls leave the customer sandbox toward `api.semanticscholar.org`. Respect copyright on full-text / PDF links the API returns (`openAccessPdf`).

## Examples

Instance config (optional key): `{ "api_key": "", "timeout_seconds": 25 }`.

### `search`

Input:

```json
{
  "action": "search",
  "query": "attention is all you need",
  "limit": 5,
  "year": "2017-2024"
}
```

Output (shape):

```json
{
  "status": "ok",
  "action": "search",
  "query": "attention is all you need",
  "total_results": 18420,
  "papers_returned": 5,
  "offset": 0,
  "papers": [
    {
      "paperId": "204e3073870fae3d05bcbc2f6a8e263d9b72e776",
      "title": "Attention Is All You Need",
      "year": 2017,
      "citationCount": 140000
    }
  ]
}
```

### `paper_details` and `batch`

```json
{
  "action": "paper_details",
  "paper_id": "DOI:10.48550/arXiv.1706.03762"
}
```

Batch (list or CSV):

```json
{
  "action": "batch",
  "paper_ids": ["DOI:10.1038/nature14539", "ARXIV:1706.03762"]
}
```

Equivalent CSV: `"paper_ids": "DOI:10.1038/nature14539,ARXIV:1706.03762"`.

### `citations`

```json
{
  "action": "citations",
  "paper_id": "DOI:10.18653/v1/N18-3011",
  "limit": 50,
  "offset": 0
}
```

Success: `{ "status": "ok", "action": "citations", "paper_id": "DOI:…", "citations_returned": 50, "citations": [ { "title": "Follow-on work", "year": 2019 } ] }`.
