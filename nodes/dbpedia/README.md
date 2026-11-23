# DBpedia

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/dbpedia` |
| `templateId` | `4c51a024-5424-5b11-8ef9-668ac97189af` |
| Env override | `SQ_TEMPLATE_ID_DBPEDIA` |
| Category | Data & Analytics |
| Official docs | https://www.dbpedia.org/ |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `sparql_query` | http GET | `dbpedia.org/sparql` |
| `lookup` | http GET | `lookup.dbpedia.org/api/search` |
| `spotlight_annotate` | http GET | `api.dbpedia-spotlight.org/en/annotate` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Public DBpedia endpoints. Courtesy limits. Spotlight annotates third-party text. Distinct hosts; paths are host+path.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
