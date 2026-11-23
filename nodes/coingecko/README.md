# CoinGecko

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/coingecko` |
| `templateId` | `6e7be034-e891-5438-b570-0cdf8634864c` |
| Env override | `SQ_TEMPLATE_ID_COINGECKO` |
| Category | Data & Analytics |
| Official docs | https://docs.coingecko.com/demo/reference/authentication |
| Auth | `header` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `coin_list` | http GET | `/coins/list` |
| `coin_get` | http GET | `/coins/{id}` |
| `coin_price` | http GET | `/simple/price` |
| `coin_markets` | http GET | `/coins/markets` |
| `coin_market_chart` | http GET | `/coins/{id}/market_chart` |
| `coin_ohlc` | http GET | `/coins/{id}/ohlc` |
| `coin_history` | http GET | `/coins/{id}/history` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

CoinGecko API terms. Demo host + x-cg-demo-api-key. Events endpoints omitted (deprecated). Not an exchange.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
