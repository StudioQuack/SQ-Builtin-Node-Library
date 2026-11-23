# Wikidata

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/wikidata/`
- **Researched:** 2026-08-20
- **Official API:** Wikidata Query Service (SPARQL) + Wikibase Action API
- **Docs:** Data access https://www.wikidata.org/wiki/Wikidata:Data_access — SPARQL https://query.wikidata.org — Wikibase API https://www.mediawiki.org/wiki/Wikibase/API — endpoint https://www.wikidata.org/w/api.php
- **Auth:** none (public read)

## Scope

One node for official Wikidata read surfaces: SPARQL on WDQS, `wbgetentities`, and `wbsearchentities`. Default SPARQL graph is **main** `https://query.wikidata.org/sparql`. Scholarly articles live on a **split graph** `https://query-scholarly.wikidata.org/sparql` — optional `endpoint` input, not the default.

Required User-Agent with contact. Do not scrape wikidata.org HTML. Heavy SPARQL is killed — keep queries narrow.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `sparql` | GET or POST | `https://query.wikidata.org/sparql` | SPARQL `query=`. Prefer POST for long queries. |
| `entity_get` | GET | `/w/api.php?action=wbgetentities` | Entity JSON by Q/P id. |
| `search` | GET | `/w/api.php?action=wbsearchentities` | Search labels. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `user_agent` | string | yes | empty | no | App + contact. Blank → `{ error }` no HTTP. |
| `timeout_seconds` | number | no | `25` | no | SPARQL should default **20** (WDQS kills long queries). `<= 0` → 25 / 20. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Query / ids |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Cap SPARQL rows |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = sparql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | SPARQL string. Empty → `{ error }` no HTTP. Cap length (e.g. 32 KiB). |
| `endpoint` | string | no | `main` (default) \| `scholarly`. Maps to query.wikidata.org vs query-scholarly.wikidata.org. Reject other hosts (SSRF). |

Send `Accept: application/sparql-results+json`. POST `application/x-www-form-urlencoded` with `query=`.

### `operation = entity_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | string | yes | Pipe-separated Q/P ids (`Q42` or `Q42\|Q5`). |
| `languages` | string | no | Wire `languages` (e.g. `en`). |
| `props` | string | no | Wire `props` default `labels\|descriptions\|claims\|sitelinks`. |

`format=json`.

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Wire `search`. |
| `language` | string | no | Wire `language` default `en`. |
| `type` | string | no | `item` \| `property` \| `lexeme` (if documented). Default `item`. |
| `limit` | number | no | 1–50. Default 10. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `bindings` | array | `sparql` | SPARQL JSON `results.bindings` (cap e.g. 500 rows). |
| `entities` | object | `entity_get` | `entities` map. |
| `search` | array | `search` | Search hits (`id`,`label`,`description`,`url`). |
| `license` | string | success | Wikidata data **CC0**. |
| `error` | string | failure | Actionable. |

## Legal

- Wikidata **structured data is CC0**. Wikipedia sitelink **text** you might follow is still CC BY-SA — this node returns Wikidata, not article prose.
- UA + robot policy: gzip, low concurrency, honor 429 / `Retry-After`. https://www.wikidata.org/wiki/Wikidata:Data_access
- WDQS: set a sensible timeout; do not use SPARQL as a bulk dump (use dumps).
- Official-API-first: WDQS + Action API only.

## Implementation notes

- JDK `HttpClient` only. TLS verified.
- SPARQL GET only when the encoded query is short; else POST.
- Body cap ~8 MiB. Fail closed on huge SPARQL XML/JSON.
- Failures on OUTPUT `{ error }`. Never throw past dispatch.
