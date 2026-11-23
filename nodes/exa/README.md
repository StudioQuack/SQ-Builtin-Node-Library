# Exa

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/exa` |
| `templateId` | `94da0e66-3ea8-57ca-9e9a-1988a770d756` |
| Env override | `SQ_TEMPLATE_ID_EXA` |
| Category | Data & Analytics |
| Official docs | https://docs.exa.ai/reference/search |
| Auth | `header` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | http POST | `/search` |
| `find_similar` | http POST | `/findSimilar` |
| `contents` | http POST | `/contents` |
| `answer` | http POST | `/answer` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Exa API terms. Bearer or x-api-key. Retrieved page text is third-party content.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
