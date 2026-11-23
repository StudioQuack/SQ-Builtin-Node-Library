# Open Library

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/open-library` |
| `templateId` | `c8c75923-e2cb-5050-939d-d3920dd9a7f1` |
| Env override | `SQ_TEMPLATE_ID_OPEN_LIBRARY` |
| Category | News & Reference |
| Official docs | https://openlibrary.org/developers/api |
| Auth | `none` — contact User-Agent required by Open Library |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http GET | `/search.json` |
| `work_get` | http GET | `/works/{work_id}.json` |
| `edition_get` | http GET | `/books/{edition_id}.json` |
| `author_get` | http GET | `/authors/{author_id}.json` |
| `isbn_get` | http GET | `/isbn/{isbn}.json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Internet Archive / Open Library terms. Courtesy throttle. Identifying User-Agent.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
