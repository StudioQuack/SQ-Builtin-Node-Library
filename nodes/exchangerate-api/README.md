# ExchangeRate-API

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/exchangerate-api` |
| `templateId` | `2d315c62-2f31-56ed-a034-0b2072d89792` |
| Env override | `SQ_TEMPLATE_ID_EXCHANGERATE_API` |
| Category | Data & Analytics |
| Official docs | https://www.exchangerate-api.com/docs/standard-requests |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `latest` | http GET | `/v6/{api_key}/latest/{base_code}` |
| `historical` | http GET | `/v6/{api_key}/history/{base_code}/{year}/{month}/{day}` |
| `convert` | http GET | `/v6/{api_key}/pair/{base_code}/{target_code}/{amount}` |
| `quota` | http GET | `/v6/{api_key}/quota` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

ExchangeRate-API v6 terms. Key in path. HTTPS for all keyed plans including Free (https://www.exchangerate-api.com/docs/standard-requests).

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
