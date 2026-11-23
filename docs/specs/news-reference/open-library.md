# Open Library

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/open-library/`
- **Researched:** 2026-08-20
- **Official API:** Open Library JSON APIs (Internet Archive)
- **Docs:** https://openlibrary.org/developers/api — Search https://openlibrary.org/dev/docs/api/search — Works/editions/authors via `{path}.json`
- **Auth:** none (identify with User-Agent + email). Optional access_key **not** required on current public JSON endpoints (2026-08-20).

## Scope

One node for official Open Library JSON: search, work GET, ISBN/edition GET, author GET. Covers are a separate Covers API (`https://covers.openlibrary.org`) — return cover URLs, do not bulk-harvest.

APIs are for **human-facing, low-volume discovery**, not a bulk backend. Use monthly dumps for harvest. Do not scrape HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/search.json` | Book/author search. |
| `work_get` | GET | `/works/{olid}.json` | Work record (`OL…W`). |
| `isbn_get` | GET | `/isbn/{isbn}.json` | Edition by ISBN. |
| `author_get` | GET | `/authors/{olid}.json` | Author (`OL…A`). |

Legacy Partner/Read JSON APIs are deprecated — do not call them.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `user_agent` | string | yes | empty | no | `AppName (contact@domain)`. Identified clients get 3 rps vs 1 rps. Blank → `{ error }` no HTTP. |
| `email` | string | no | empty | no | Contact; may be folded into UA. |
| `timeout_seconds` | number | no | `25` | no | `<= 0` → 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Query / ids |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Cap arrays |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Wire `q`. |
| `limit` | number | no | Wire `limit` (max 100). Default 20. |
| `offset` | number | no | Offset. |
| `fields` | string | no | Comma field list if documented. |

### `operation = work_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `work_id` | string | yes | `OL…W` or full `/works/OL…W`. |

### `operation = isbn_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `isbn` | string | yes | ISBN-10 or ISBN-13 (hyphens stripped). |

### `operation = author_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `author_id` | string | yes | `OL…A`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `num_found` | number | `search` | Provider `num_found`. |
| `docs` | array | `search` | Search docs (key, title, author_name, isbn, cover_i). |
| `work` / `edition` / `author` | object | get ops | Provider JSON (trimmed). |
| `cover_url` | string | when `cover_i` or ISBN known | `https://covers.openlibrary.org/b/id/{cover_i}-M.jpg` (hotlink covers; respect Covers API). |
| `error` | string | failure | Actionable. |

## Legal

- Usage guidelines on https://openlibrary.org/developers/api (edited May 2026): identify UA+email; cache; no HTML scrape; no 5+ IP rotation; no bulk harvest via API; no high-traffic backend use. Violations → aggressive rate limit/block.
- Rate: **1 rps** anonymous, **3 rps** identified.
- Book **covers and texts have their own licences** — not all public domain. Check edition/work `identifiers` and IA borrow/read flags. Do not assume CC0.
- Internet Archive / Open Library terms apply. Contact openlibrary@archive.org for bulk.
- Official-API-first: JSON APIs + Covers URLs only.

## Implementation notes

- JDK `HttpClient` only. Host `openlibrary.org` (and `covers.openlibrary.org` only as URL strings, not extra hops unless fetching a single cover is explicitly in-scope — **v1 returns URLs only**).
- Serialize calls (~1–3 rps). Body cap ~8 MiB.
- Failures on OUTPUT `{ error }`. Never throw past dispatch.
