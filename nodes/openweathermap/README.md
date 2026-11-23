# OpenWeatherMap

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/openweathermap` |
| `templateId` | `14a71c69-0341-5b0e-8a49-fd4847ec6536` |
| Env override | `SQ_TEMPLATE_ID_OPENWEATHERMAP` |
| Category | Data & Analytics |
| Official docs | https://openweathermap.org/current |
| Auth | `query` — config `appid` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `weather_current` | http GET | `/data/2.5/weather` |
| `weather_forecast5` | http GET | `/data/2.5/forecast` |
| `onecall` | http GET | `/data/3.0/onecall` |
| `geo_direct` | http GET | `/geo/1.0/direct` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

OpenWeather terms. One Call 3.0 is a separate SKU. Do not use retired /data/2.5/onecall. Attribution required when displaying.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
