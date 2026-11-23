# Alpha Vantage

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/alpha-vantage` |
| `templateId` | `c6a5dcfd-5418-5c03-affc-46b40d243cb6` |
| Env override | `SQ_TEMPLATE_ID_ALPHA_VANTAGE` |
| Category | Data & Analytics |
| Official docs | https://www.alphavantage.co/documentation/ |
| Auth | `query` — config `apikey` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `quote` | http GET | `/query?function=GLOBAL_QUOTE` |
| `time_series_intraday` | http GET | `/query?function=TIME_SERIES_INTRADAY` |
| `time_series_daily` | http GET | `/query?function=TIME_SERIES_DAILY` |
| `time_series_weekly` | http GET | `/query?function=TIME_SERIES_WEEKLY` |
| `search` | http GET | `/query?function=SYMBOL_SEARCH` |
| `fx_rate` | http GET | `/query?function=CURRENCY_EXCHANGE_RATE` |
| `crypto_daily` | http GET | `/query?function=DIGITAL_CURRENCY_DAILY` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }`.

## Legal

Alpha Vantage terms. Free key: 25 requests/day. TIME_SERIES_INTRADAY is Premium. TIME_SERIES_DAILY outputsize=full is premium-only. Realtime and 15-minute delayed US market data are premium. Not for redistribution as a competing feed.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
