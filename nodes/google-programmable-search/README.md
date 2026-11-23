# Google Programmable Search

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/google-programmable-search` |
| `templateId` | `8dfb4025-f255-560f-8560-3717bfa9859b` |
| Env override | `SQ_TEMPLATE_ID_GOOGLE_PROGRAMMABLE_SEARCH` |
| Category | Data & Analytics |
| Official docs | https://developers.google.com/custom-search/v1/overview |
| Auth | `query` — config `key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `cse_list` | http GET | `/customsearch/v1` |
| `cse_siterestrict` | http GET | `/customsearch/v1/siterestrict` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Google APIs Terms. Closed to new CSE JSON customers; existing pricing until 2027-01-01. Requires Programmable Search Engine id (cx). Do not scrape google.com/search.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
