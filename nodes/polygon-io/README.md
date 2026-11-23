# Polygon.io

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/polygon-io` |
| `templateId` | `d46f2ce9-0eaf-5b20-9117-a6929ecfe893` |
| Env override | `SQ_TEMPLATE_ID_POLYGON_IO` |
| Category | Data & Analytics |
| Official docs | https://polygon.io/docs/rest/stocks/aggregates/custom-bars |
| Auth | `bearer` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `aggregates` | http GET | `/v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}` — `from`/`to` **YYYY-MM-DD** (validated before HTTP). |
| `ticker_details` | http GET | `/v3/reference/tickers/{ticker}` |
| `snapshot` | http GET | `/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}` |
| `news` | http GET | `/v2/reference/news` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Polygon/Massive terms. Host https://api.polygon.io. Bearer. Paid plans for realtime. Display/redistribution limits. Docs: https://polygon.io/docs/rest/stocks/aggregates/custom-bars.

**Plan tier:** `aggregates` and `snapshot` may return HTTP 403 on free/low tiers; `ticker_details` and `news` are typically reachable.

**Tickers:** lookups are case-sensitive (`AAPL` vs `aapl`).

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
