# Finnhub

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/finnhub` |
| `templateId` | `a7b18e0c-6495-5574-ab40-d9eb46fbf05b` |
| Env override | `SQ_TEMPLATE_ID_FINNHUB` |
| Category | Data & Analytics |
| Official docs | https://finnhub.io/docs/api |
| Auth | `query` — config `token` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `quote` | http GET | `/quote` |
| `candle` | http GET | `/stock/candle` |
| `company_profile` | http GET | `/stock/profile2` |
| `news` | http GET | `/company-news` |
| `forex_rate` | http GET | `/forex/rates` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Finnhub terms. Exchange licences on some datasets. Token query.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
