# ip-api

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/ip-api` |
| `templateId` | `a2f3fcf6-183a-5dce-894b-bfc7cb56cbb0` |
| Env override | `SQ_TEMPLATE_ID_IP_API` |
| Category | Data & Analytics |
| Official docs | https://ip-api.com/docs/api:json |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `lookup` | http GET | `/json/{ip}` |
| `batch` | http POST | `/batch` — required `ips` list (≤ 100 IPs or `{query, fields?, lang?}` objects) is the whole JSON body |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Free HTTP endpoint is non-commercial. Commercial requires SSL + key (pro.ip-api.com). IP can be PII.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
