# World Bank

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/world-bank` |
| `templateId` | `58b16f17-1564-56c5-9f83-c2076a279adb` |
| Env override | `SQ_TEMPLATE_ID_WORLD_BANK` |
| Category | Data & Analytics |
| Official docs | https://datahelpdesk.worldbank.org/knowledgebase/articles/889392-about-the-indicators-api-documentation |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `indicator_get` | http GET | `/country/{country}/indicator/{indicator}?format=json` |
| `country_list` | http GET | `/country?format=json` |
| `catalog_search` | http GET | `/indicator?format=json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

World Bank Indicators API v2. No key. Attribution to World Bank. Dataset licences still apply.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
