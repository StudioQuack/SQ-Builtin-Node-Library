# Tomorrow.io

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/tomorrow-io` |
| `templateId` | `4a9f3fa1-0bef-5f81-850a-ca081f8cdb33` |
| Env override | `SQ_TEMPLATE_ID_TOMORROW_IO` |
| Category | Data & Analytics |
| Official docs | https://docs.tomorrow.io/reference |
| Auth | `query` — config `apikey` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `realtime` | http GET | `/weather/realtime` |
| `forecast` | http GET | `/weather/forecast` |
| `historical` | http GET | `/weather/history/recent` |
| `timelines` | http GET | `/timelines` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Tomorrow.io REST v4 (https://api.tomorrow.io/v4). Location as lat/lng or locationId. timesteps=best on /timelines is deprecated and returns 1h.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
