# DuckDuckGo

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/duckduckgo` |
| `templateId` | `22b23ed6-59bb-581d-a142-32b1e26aa7e8` |
| Env override | `SQ_TEMPLATE_ID_DUCKDUCKGO` |
| Category | Data & Analytics |
| Official docs | https://duckduckgo.com/api |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `instant_answer` | http GET | `/?format=json&t=studioquack` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Instant Answer API only — not a full SERP. Attribution when displayed. Do not scrape duckduckgo.com HTML.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
