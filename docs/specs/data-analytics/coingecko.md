# CoinGecko

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/coingecko/`
- **Researched:** 2026-08-20
- **Official API:** CoinGecko API v3 (Demo + Pro)
- **Docs:** https://docs.coingecko.com/reference/authentication · https://docs.coingecko.com/demo/reference/authentication
- **Auth:** api_key — secret binding: `api_key`

## Scope

Market data only (not an exchange). Demo host `https://api.coingecko.com/api/v3/` header `x-cg-demo-api-key`. Pro host `https://pro-api.coingecko.com/api/v3/` header `x-cg-pro-api-key`. Prefer headers; **do not** put the key on the query string in production.

**Omit `events`** — CoinGecko events endpoints are deprecated/removed. Onchain/DEX paths are out of v1 unless catalog expands.

Unkeyed Demo still exists on some public routes; require a key anyway so rate limits are attributable.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `coin_list` | GET | `/coins/list` | id/symbol/name map |
| `coin_get` | GET | `/coins/{id}` | One coin |
| `coin_price` | GET | `/simple/price` | Spot prices |
| `coin_markets` | GET | `/coins/markets` | Market table |
| `coin_market_chart` | GET | `/coins/{id}/market_chart` | History |
| `coin_ohlc` | GET | `/coins/{id}/ohlc` | OHLC |
| `coin_tickers` | GET | `/coins/{id}/tickers` | Exchange tickers |
| `coin_history` | GET | `/coins/{id}/history` | Snapshot by date |

Confirm paths on https://docs.coingecko.com at implement time.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `coin_price` | no | |
| `api_key` | password | yes | empty | yes | Demo or Pro key |
| `plan` | select | yes | `demo` | no | `demo` → demo host + `x-cg-demo-api-key`; `pro` → pro host + `x-cg-pro-api-key` |
| `timeout_seconds` | number | no | `25` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = coin_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `include_platform` | boolean | no | |

### `operation = coin_get` | `coin_market_chart` | `coin_ohlc` | `coin_tickers` | `coin_history`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | CoinGecko coin id (`bitcoin`) |
| `vs_currency` | string | chart/ohlc | e.g. `usd` |
| `days` | string | chart/ohlc | `1`, `7`, `30`, `max` as documented |
| `date` | string | history | `dd-mm-yyyy` |

### `operation = coin_price`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ids` | string | yes | Comma-separated ids |
| `vs_currencies` | string | yes | e.g. `usd,eur` |
| `include_24hr_change` | boolean | no | |

### `operation = coin_markets`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `vs_currency` | string | yes | |
| `per_page` | number | no | Cap 250 |
| `page` | number | no | |
| `order` | string | no | |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `coins` / `prices` / `market_chart` / `ohlc` / `tickers` / `coin` | mixed | 2xx | Provider JSON, size-capped |
| `error` | string | failure | 429 credit/rate |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

## Legal

- https://www.coingecko.com/en/api/terms (confirm live)
- Attribution to CoinGecko when displaying data. Not for redistribution as a competing market-data feed beyond the plan.
- Demo: ~10k calls/month class limits — confirm live dashboard.

## Implementation notes

- Header only (never query key). `User-Agent: StudioQuack/1.0`.
- Body cap 1 MiB (`coin_list` is large — allow 2 MiB for that op only).
- Timeout 25 s. TLS verified. No inner retry.
- Wrong plan/host mismatch (Pro key on Demo host) → clear `{ error }`.
