# CoinMarketCap

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/coinmarketcap` |
| `templateId` | `a3e65a93-f44d-5751-b91f-6e3d220988c3` |
| Env override | `SQ_TEMPLATE_ID_COINMARKETCAP` |
| Category | Data & Analytics |
| Official docs | https://coinmarketcap.com/api/documentation/guides/quick-start |
| Auth | `header` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `listings_latest` | http GET | `/v1/cryptocurrency/listings/latest` |
| `quotes_latest` | http GET | `/v2/cryptocurrency/quotes/latest` |
| `info` | http GET | `/v2/cryptocurrency/info` |
| `global_metrics` | http GET | `/v1/global-metrics/quotes/latest` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

CMC Professional API terms. Header X-CMC_PRO_API_KEY. Logo/data attribution required. Quick-start still documents GET /v1/cryptocurrency/listings/latest; the cryptocurrency reference also lists v3 listings/quotes.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
