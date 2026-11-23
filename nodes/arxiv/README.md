# arXiv Search

## Overview

In-process StudioQuack builtin (`templateId` `f809e1c3-1ca4-4888-89a0-53155478ab70`, override `SQ_TEMPLATE_ID_ARXIV`). Handler: `ArxivNode`. It searches and fetches arXiv preprints through the official [arXiv Atom API](https://info.arxiv.org/help/api/user-manual.html) at `http://export.arxiv.org/api/query` (**HTTP**, not HTTPS — that is the documented export endpoint).

**No authentication.** There is no API key or configuration secret. Responses are Atom XML (`application/atom+xml`) parsed in-process (namespace-aware, XXE-hardened: doctype disallowed, external entities off). Paper objects expose `abstract` (Atom `<summary>` with whitespace collapsed), not `summary`.

Provider docs: [API basics](https://info.arxiv.org/help/api/basics.html), [user manual](https://info.arxiv.org/help/api/user-manual.html), [terms of use](https://info.arxiv.org/help/api/tou.html) (attribution + rate limits), [category taxonomy](https://arxiv.org/category_taxonomy).

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | `inputVariables.input` (list-of-one unwraps). Fields: `action`, `query` (alias `q`), `arxiv_id`, `arxiv_ids`, `category`, `max_results`, `sort_by`, `sort_order`, `start`. Unknown keys are ignored. |
| `output` | OUTPUT | object | yes | Success search: `{status, action, query, category, total_results, papers_returned, start, papers}`. Success get_paper: `{status, action, papers_returned, papers}`. Failure: `{error}` only. No error OUTPUT port. |

### Input fields

Incoming data selects `search` vs `get_paper`. On search, `query` is the corpus expression; optional `category` wraps the wire `search_query` as `cat:{category} AND ({query})` while `output.query` stays the original string. On get_paper, `arxiv_id` overwrites `arxiv_ids` to a one-element list.

| Field | Type | Required | Default | Accepted values | How it is used |
|---|---|---|---|---|---|
| `action` | string | no | `search` | `search` \| `get_paper` (case-sensitive) | `search` → `search_query` + sort + paging. `get_paper` → `id_list`. Any other string fails with no HTTP call. |
| `query` | string | for `search` | — | arXiv field prefixes `ti:`, `au:`, `abs:`, `cat:`, `all:`, `co:`, `jr:`; Boolean `AND`/`OR`/`ANDNOT`; quotes | Alias `q` when `query` is blank. Missing → `query is required for search action.` Ignored for `get_paper`. |
| `category` | string | no | omitted (`output.category` then `null`) | taxonomy codes such as `cs.AI`, `cs.CL` | Search only. When non-empty, wire `search_query` is `cat:{category} AND ({query})`. Invalid codes yield zero hits, not a node error. |
| `arxiv_id` | string | one of for `get_paper` | — | new-style (`2301.07041`, `2301.07041v2`) or old-style (`hep-ex/0307015`); no `https://arxiv.org/abs/` prefix | When non-blank, **overwrites** `arxiv_ids` to a one-element list. |
| `arxiv_ids` | string or array | one of for `get_paper` | — | CSV string or JSON array of strings | Used when `arxiv_id` is blank. Tokens trimmed; empties dropped. Wire `id_list` is comma-joined (no spaces); `max_results=len(ids)`. |
| `max_results` | number | no | `50` | positive int; **hard-capped at 100** | Search page size (`max_results` query param). Ignored for `get_paper` (that path sends `max_results=len(ids)`). |
| `sort_by` | string | no | `relevance` | `relevance` \| `lastUpdatedDate` \| `submittedDate` | Wire name `sortBy`. Search only. Unknown values are forwarded. |
| `sort_order` | string | no | `descending` | `descending` \| `ascending` | Wire name `sortOrder`. Search only. |
| `start` | number | no | `0` | integer `>= 0` | 0-based offset (not a 1-based page). Search only. Page *k* uses `start=k*max_results`. |

### Output fields

| Field | Type | When present | Meaning |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `action` | string | success | Echo: `search` or `get_paper`. |
| `query` | string | search success | Original input query (not the `cat:` wrapper). |
| `category` | string or null | search success | Echo of input `category`, or `null` when omitted. |
| `total_results` | number | search success | OpenSearch `totalResults` (corpus size, not this page). |
| `start` | number | search success | Echo of the requested offset. |
| `papers_returned` | number | success | `papers.length` after dropping entries with an empty title. |
| `papers` | array | success | Parsed Atom `<entry>` objects. |
| `error` | string | failure | See error mapping. Never contains `helperToken`. |

Each `papers[]` item:

| Field | Type | Meaning |
|---|---|---|
| `arxiv_id` | string | Suffix after `/abs/` in Atom `<id>`, else the raw id. |
| `title` | string | Atom `<title>` with whitespace collapsed. Empty titles are skipped. |
| `authors` | array of string | Ordered Atom `author/name` values. |
| `abstract` | string | Atom `<summary>` with whitespace collapsed. There is **no** `summary` field. |
| `published` | string | Atom `<published>` (typically ISO-8601 UTC). |
| `updated` | string | Atom `<updated>`. |
| `doi` | string | `arxiv:doi`, or `""`. |
| `journal_ref` | string | `arxiv:journal_ref`, or `""`. |
| `primary_category` | string | `arxiv:primary_category/@term`. |
| `categories` | array of string | Every Atom `<category @term>`, including primary. |
| `comment` | string | `arxiv:comment`, or `""`. |
| `url` | string | `https://arxiv.org/abs/{arxiv_id}`. |
| `pdf_url` | string | Atom link `title="pdf"` href, else `https://arxiv.org/pdf/{arxiv_id}`. |

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `timeout_seconds` | number | no | `30` | positive int; missing / not positive → 30 | Connect+read timeout for the single GET. On expiry: `arXiv API Error: timed out after {n}s` (or the transport timeout text). This is **not** an inter-request delay. |

No API key parameter exists.

## How it works

1. The catalog registers `ArxivNode` under `f809e1c3-1ca4-4888-89a0-53155478ab70` (or `SQ_TEMPLATE_ID_ARXIV`).
2. `handle` defaults blank `action` to `search`, then dispatches `search` or `get_paper`.
3. `GET http://export.arxiv.org/api/query` through the shared JDK `HttpClient` (`DataNodeSupport.get`: HTTP/1.1, redirects, request timeout, 8 MiB body cap).
4. Headers: **`User-Agent: StudioQuack/1.0` on every call**. arXiv [asks clients to identify themselves](https://info.arxiv.org/help/api/user-manual.html); unidentified clients are often **HTTP 503**. All five data nodes send this product UA; arXiv is the one that requires it to stay available.
5. Atom XML is parsed with a namespace-aware `DocumentBuilder` (secure processing, doctype disallowed, external entities off). If that parser rejects the document, a regex fallback recovers `<entry>` blocks when present.
6. Pagination is `start` + `max_results` (cap 100). The node does **not** sleep between calls. arXiv asks for about **3 seconds between requests** — space invocations in the workflow. HTTP 503 appends ` | arXiv is rate-limiting. Wait 3+ seconds between requests.`
7. Failures stay on `output.error`; `handle` never throws:
   - `query is required for search action.`
   - `arxiv_id or arxiv_ids is required for get_paper action.`
   - `Unknown action: {action}. Use 'search' or 'get_paper'.`
   - `arXiv API Error: HTTP {status} for url: {url}` (plus the 503 hint when status is 503)
   - `arXiv API Error: timed out after {n}s` / transport text
   - `arXiv XML Parse Error: {message}`
   - `Error: {simpleName}: {message}`
8. Helper logs record `arxiv start` / `done` plus `action` and `papers_returned` — never query text, never abstracts, never `helperToken`.

## Security & legal

- No customer secret is involved. Query text and abstracts are not written to helper logs.
- [arXiv API Terms of Use](https://info.arxiv.org/help/api/tou.html) require **attribution** when you display metadata or full text (credit arXiv and the authors; keep links to `arxiv.org`). Do not present this node’s output as an arXiv-hosted service.
- Rate limit: roughly **one request every three seconds**. Bursting produces HTTP 503. This node does not insert delays — the workflow must space calls. Always send a descriptive User-Agent (this handler already sends `StudioQuack/1.0`).
- XML parsing is XXE-hardened. The export endpoint is HTTP by specification; do not “upgrade” the URL to HTTPS in config (there is no base-URL parameter).
- The customer is responsible for how preprint metadata is shown downstream (attribution, licenses on individual papers).

## Examples

### Fielded search in a category

Input:

```json
{
  "action": "search",
  "query": "ti:\"attention is all you need\"",
  "category": "cs.CL",
  "max_results": 5,
  "sort_by": "relevance",
  "sort_order": "descending",
  "start": 0
}
```

The HTTP `search_query` is `cat:cs.CL AND (ti:"attention is all you need")`. `output.query` remains `ti:"attention is all you need"`.

Output (shape): `{ "status": "ok", "action": "search", "query": "ti:\"attention is all you need\"", "category": "cs.CL", "total_results": 1, "papers_returned": 1, "start": 0, "papers": [ { "arxiv_id": "1706.03762v7", "title": "Attention Is All You Need", "authors": ["Ashish Vaswani", "Noam Shazeer"], "abstract": "…", "url": "https://arxiv.org/abs/1706.03762v7", "pdf_url": "https://arxiv.org/pdf/1706.03762v7" } ] }`.

### Lookup by id list

Input:

```json
{
  "action": "get_paper",
  "arxiv_ids": "1706.03762,2301.07041"
}
```

Equivalent single-id form: `{"action":"get_paper","arxiv_id":"1706.03762"}` (this overwrites any `arxiv_ids`). Success omits `query` / `category` / `total_results` / `start` and returns `papers_returned` + `papers`.
