# Wikidata SPARQL

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/wikidata-sparql` |
| `templateId` | `31c179eb-0047-52a6-b5d6-1c07349e61c5` |
| Env override | `SQ_TEMPLATE_ID_WIKIDATA_SPARQL` |
| Category | Data & Analytics |
| Official docs | https://query.wikidata.org/ |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `sparql_query` | http GET | `/sparql` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

WDQS official SPARQL. Wikidata CC0. WMF UA policy — identifying UA required (WDQS UA exception ended 2026-02-18). 60s deadline. Heavy queries are killed. Scholarly split is query-scholarly.wikidata.org.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
