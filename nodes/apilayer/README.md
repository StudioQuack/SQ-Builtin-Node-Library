# APILayer

Official-API node driven by `OfficialApiNode` + `api_spec.json`. This node is Fixer only.

| | |
|---|---|
| Folder | `nodes/apilayer` |
| `templateId` | `6621f650-c774-5afb-8cc8-06fdbc6c4efb` |
| Env override | `SQ_TEMPLATE_ID_APILAYER` |
| Category | Data & Analytics |
| Official docs | https://fixer.io/documentation |
| Base | `https://data.fixer.io/api` |
| Auth | `query` — config `access_key` |

## Operations

| operation | HTTP | Path | Required |
|---|---|---|---|
| `fixer_latest` | GET | `/latest` | — (`base`, `symbols`, `spread` optional) |
| `fixer_convert` | GET | `/convert` | `from`, `to`, `amount` (`date` optional, `YYYY-MM-DD`) |
| `fixer_historical` | GET | `/{date}` | `date` (`YYYY-MM-DD`; `base`, `symbols`, `spread` optional) |
| `fixer_timeseries` | GET | `/timeseries` | `start_date`, `end_date` |
| `fixer_fluctuation` | GET | `/fluctuation` | `start_date`, `end_date` |
| `fixer_symbols` | GET | `/symbols` | — |

`spread` is optional. Send `1` for bid/ask quotes on latest, historical, and timeseries. Fluctuation has no `spread` parameter.

The marketplace gateway `https://api.apilayer.com` with header `apikey` is deliberately unused. Hub Fixer is `data.fixer.io` and query `access_key`. JSONP `callback` is deliberately unused: the node reads JSON.

Numverify, Mediastack, and marketstack stay separate nodes. Each product has its own key.

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }`.

## Legal

Fixer terms. Operator accepts the provider terms. Official HTTP only. Secrets are StudioQuack bindings; never logged.
