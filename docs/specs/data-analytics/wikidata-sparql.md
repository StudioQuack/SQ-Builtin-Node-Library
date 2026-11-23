# Wikidata SPARQL

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/wikidata-sparql/`
- **Researched:** 2026-08-20
- **Official API:** Wikidata Query Service (WDQS) SPARQL 1.1
- **Docs:** https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual
- **Auth:** none — identifying `user_agent` required (WMF UA policy; WDQS UA exception ends **2026-02-18**, so always send a compliant UA)

## Scope

Runs **read-only SPARQL** against `https://query.wikidata.org/sparql`. Distinct from Wikipedia / Wikidata entity GET (`wbgetentities`).

Hard **60 s** query deadline on the public endpoint. One client (UA+IP): 60 s processing per 60 s; 30 error queries/min; ~5 parallel queries per IP. HTTP 429 + `Retry-After` when throttled. Heavy queries are killed — inject `LIMIT` if the query has none.

Scholarly graph is split: `https://query-scholarly.wikidata.org/sparql` (optional `endpoint` config). Do not federate to random SPARQL hosts from this node (SSRF). Allow-list: `query.wikidata.org` and `query-scholarly.wikidata.org` only.

Reject SPARQL that is not `SELECT` / `ASK` / `CONSTRUCT` / `DESCRIBE` (no updates).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `sparql_query` | GET or POST | `https://query.wikidata.org/sparql` | Run SPARQL; prefer POST for long queries |

`Accept: application/sparql-results+json`. POST body `application/x-www-form-urlencoded` `query=`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `sparql_query` | no | |
| `user_agent` | string | yes | empty | no | WMF-compliant UA with contact. Empty → no HTTP. |
| `endpoint` | select | no | `main` | no | `main` → query.wikidata.org; `scholarly` → query-scholarly.wikidata.org |
| `timeout_seconds` | number | no | `55` | no | Client timeout **below** the 60 s server deadline |
| `default_limit` | number | no | `100` | no | Appended when SELECT/CONSTRUCT has no LIMIT |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `{ query }` | |
| `output` | OUTPUT | `{ status, bindings }` or `{ error }` | Cap rows |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = sparql_query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | SPARQL text |
| `timeout_seconds` | number | no | Override (max 55) |

If SELECT/CONSTRUCT lacks a top-level `LIMIT`, append `LIMIT {default_limit}` before send.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `head` | object | SELECT | Variable names |
| `bindings` | array | SELECT | Row objects (values as `{ type, value }`) |
| `boolean` | boolean | ASK | |
| `truncated` | boolean | row cap | True if dropped extra rows |
| `error` | string | failure | Timeout / 429 / parse |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / timeout | Honor header; timeout → 30 |

## Legal

- https://www.mediawiki.org/wiki/Wikidata_Query_Service/User_Manual
- https://www.wikidata.org/wiki/Wikidata:Data_access
- UA: https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
- Data: CC0. Still rate-limit; do not DDoS WDQS.
- Graph split: scholarly items may be absent from the main endpoint.

## Implementation notes

- POST preferred. Header `User-Agent` + `Accept: application/sparql-results+json` + `Accept-Encoding: gzip`.
- Body cap 2 MiB. Row cap 500 in output even if SPARQL LIMIT higher.
- Client timeout 55 s. Server 60 s → treat as timeout error, not partial success.
- No SPARQL UPDATE. No custom endpoint URLs (SSRF).
- Logs: query length, elapsed, row count — never the full query if it might contain PII (truncate to 200 chars).
