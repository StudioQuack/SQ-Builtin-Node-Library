# marketstack

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/marketstack` |
| `templateId` | `d7435cae-2f25-5690-97a0-a09d488fda85` |
| Env override | `SQ_TEMPLATE_ID_MARKETSTACK` |
| Category | Data & Analytics |
| Official docs | https://marketstack.com/documentation_v2 |
| Auth | `query` — config `access_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `eod_list` | http GET | `/eod` |
| `exchange_get` | http GET | `/exchanges/{mic}` |
| `ticker_get` | http GET | `/tickers/{symbol}` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{status,http_status,data}`.

## Legal

marketstack (APILayer) terms. REST v2 (https://api.marketstack.com/v2). v1 was deprecated after 2025-06-30. access_key query. EOD only on free. Exchange redistribution rules apply.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
