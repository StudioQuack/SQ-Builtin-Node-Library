# Twelve Data

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/twelve-data` |
| `templateId` | `b6bfc10a-c384-5377-9485-6a4297185a1e` |
| Env override | `SQ_TEMPLATE_ID_TWELVE_DATA` |
| Category | Data & Analytics |
| Official docs | https://twelvedata.com/docs |
| Auth | `query` — config `apikey` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `quote` | http GET | `/quote` |
| `time_series` | http GET | `/time_series` |
| `eod` | http GET | `/eod` |
| `fx_rate` | http GET | `/exchange_rate` |
| `crypto_quote` | http GET | `/quote` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Twelve Data REST terms. apikey query. Credit meter. Delayed/realtime per plan.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
