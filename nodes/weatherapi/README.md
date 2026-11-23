# WeatherAPI

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/weatherapi` |
| `templateId` | `1edaaa3b-4b3a-5ea3-96b0-c84e8d143b8b` |
| Env override | `SQ_TEMPLATE_ID_WEATHERAPI` |
| Category | Data & Analytics |
| Official docs | https://www.weatherapi.com/docs/ |
| Auth | `query` — config `key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `current` | http GET | `/current.json` |
| `forecast` | http GET | `/forecast.json` |
| `history` | http GET | `/history.json` |
| `astronomy` | http GET | `/astronomy.json` |
| `timezone` | http GET | `/timezone.json` |
| `sports` | http GET | `/sports.json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

WeatherAPI.com terms. Query param key. Commercial redistribution licensed.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
