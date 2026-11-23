# Numverify

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/numverify` |
| `templateId` | `caed9196-4d72-535c-a1fb-42e0a06c32f9` |
| Env override | `SQ_TEMPLATE_ID_NUMVERIFY` |
| Category | Data & Analytics |
| Official docs | https://numverify.com/documentation |
| Auth | `query` — config `access_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `validate` | http GET | `/validate` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

APILayer Numverify terms. HTTPS base https://apilayer.net/api. access_key query. Phone numbers are PII — do not log.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
