# Mediastack

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/mediastack` |
| `templateId` | `5894ba96-8c2f-568d-b24c-f55718759f89` |
| Env override | `SQ_TEMPLATE_ID_MEDIASTACK` |
| Category | News & Reference |
| Official docs | https://mediastack.com/documentation |
| Auth | `query` — config `access_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `live` | http GET | `/news` |
| `historical` | http GET | `/news` |
| `sources` | http GET | `/sources` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{status,http_status,data}`.

## Legal

Mediastack (APILayer) terms. access_key query. Display/source attribution. Distinct from NewsAPI.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
