# Crossref

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/crossref/`
- **Researched:** 2026-08-20
- **Official API:** Crossref REST API (current docs + Swagger; default route = latest)
- **Docs:** https://www.crossref.org/documentation/retrieve-metadata/rest-api/ — try-it: https://api.crossref.org/ — tips: https://www.crossref.org/documentation/retrieve-metadata/rest-api/tips-for-using-the-crossref-rest-api/
- **Auth:** none for public pool (polite `User-Agent` + `mailto`). Optional Plus token — secret binding: `token`

## Scope

One node for Crossref **metadata** (works, funders). GitHub `CrossRef/rest-api-doc` is **deprecated** (Solr-era) — do not implement from it. Current search is Elasticsearch-backed; Swagger at `https://api.crossref.org/` is authoritative.

This is metadata, not full-text. Do not scrape publisher sites.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `works_search` | GET | `/works` | Search / filter works. |
| `work_get` | GET | `/works/{doi}` | One work by DOI. |
| `funders` | GET | `/funders` | Funder search. Optional `/funders/{id}` when `funder_id` set. |

Prefer versioned base `https://api.crossref.org/v1` per Crossref tips page (examples omit host and use `/v1`). Default unversioned `https://api.crossref.org` redirects to latest — pin `v1` in `base_url` unless Plus docs say otherwise.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `works_search` | no | Chooses the row above |
| `mailto` | string | yes | empty | no | Polite-pool contact. Sent in `User-Agent` and/or `mailto` query. Blank → `{ error }` no HTTP. |
| `token` | password | no | empty | yes | Crossref **Plus** token if the operator has a Plus licence. Header per current Plus docs (⚠️ verify header name at build: historically `Crossref-Plus-API-Token`). |
| `timeout_seconds` | number | no | `25` | no | `<= 0` → 25. |
| `base_url` | string | no | `https://api.crossref.org/v1` | no | Pin major version. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Query / DOI |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Metadata only |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = works_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Wire `query`. |
| `filter` | string | no | Crossref `filter=` clauses. |
| `select` | string | no | Comma fields to return. |
| `rows` | number | no | Page size (cap 1000; default 20). |
| `offset` | number | no | Offset. Prefer `cursor` when present. |
| `cursor` | string | no | Deep paging cursor if documented. |
| `sort` | string | no | Sort field. |
| `order` | string | no | `asc` \| `desc`. |

### `operation = work_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `doi` | string | yes | DOI (`10.xxxx/…`). Strip `https://doi.org/` prefix. Encode path. |

### `operation = funders`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Funder search. |
| `funder_id` | string | no | If set, GET `/funders/{id}` instead of list. |
| `rows` | number | no | List page size. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `message_type` | string | success | Provider `message-type`. |
| `total_results` | number | list | `message.total-results`. |
| `items` | array | list | `message.items` (works or funders). |
| `work` | object | `work_get` | `message` work. |
| `error` | string | failure | Actionable. |

## Legal

- Crossref REST exposes **member-deposited metadata**. Full-text licence is **per work** (`license` URLs on the record) — not granted by this API.
- Public REST is not a substitute for **Plus** if the operator needs a commercial SLA / higher volume. Plus has a separate licence.
- Identify mailto; abusive clients get blocked (Crossref tips page).
- Official-API-first: `api.crossref.org` only.

## Implementation notes

- JDK `HttpClient` only. No Crossref SDK.
- `User-Agent: StudioQuack/1.0 (mailto:{mailto}; https://studioquack.com)`.
- HTTPS only. Body cap ~8 MiB.
- Failures on OUTPUT `{ error }`. Never throw past dispatch.
