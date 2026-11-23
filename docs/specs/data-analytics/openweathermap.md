# OpenWeatherMap

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/openweathermap/`
- **Researched:** 2026-08-20
- **Official API:** OpenWeather Current Weather 2.5, 5-day/3-hour Forecast 2.5, Geocoding 1.0, One Call **3.0** (separate SKU). Homepage also advertises One Call **4.0** — implement 3.0 paths that are still documented; add 4.0 only if https://openweathermap.org/api lists a stable REST path at code time.
- **Docs:** https://openweathermap.org/current · https://openweathermap.org/forecast5 · https://openweathermap.org/api/geocoding-api · https://openweathermap.org/api/one-call-3
- **Auth:** api_key — secret binding: `api_key` (query `appid`)

## Scope

Weather for a lat/lon (or city via Geocoding). One Call 2.5 is retired — do not call `/data/2.5/onecall`. Built-in city-name geocoder on Current Weather is deprecated; use `geo_direct` then `weather_current`. Do not scrape openweathermap.org HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `weather_current` | GET | `https://api.openweathermap.org/data/2.5/weather` | Current conditions (`lat`+`lon`) |
| `weather_forecast5` | GET | `https://api.openweathermap.org/data/2.5/forecast` | 5-day / 3-hour forecast |
| `onecall` | GET | `https://api.openweathermap.org/data/3.0/onecall` | Current + minutely/hourly/daily + alerts (One Call subscription) |
| `geo_direct` | GET | `https://api.openweathermap.org/geo/1.0/direct` | Place name → lat/lon |
| `geo_reverse` | GET | `https://api.openweathermap.org/geo/1.0/reverse` | lat/lon → place |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `weather_current` | no | |
| `api_key` | password | yes | empty | yes | `appid`. Never on INPUT. |
| `units` | select | no | `metric` | no | `standard` \| `metric` \| `imperial` |
| `lang` | string | no | empty | no | Response language |
| `timeout_seconds` | number | no | `20` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No `appid` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = weather_current` | `weather_forecast5` | `onecall`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | Latitude |
| `lon` | number | yes | Longitude |
| `units` | string | no | Override instance |
| `lang` | string | no | Override |
| `exclude` | string | no | One Call only: `current,minutely,hourly,daily,alerts` |

City name is **not** accepted on weather ops (deprecated built-in geocoder).

### `operation = geo_direct`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q` city[,state][,country] |
| `limit` | number | no | 1–5 |

### `operation = geo_reverse`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | |
| `lon` | number | yes | |
| `limit` | number | no | |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `coord` | object | weather | `{ lat, lon }` |
| `weather` | object | current | Normalized temp, humidity, wind, description, dt |
| `list` | array | forecast5 | 3-hour slots (cap 40) |
| `current` / `hourly` / `daily` / `alerts` | object/array | onecall | Pass-through capped |
| `locations` | array | geo | `{ name, lat, lon, country, state }` |
| `error` | string | failure | 401 One Call SKU hint |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

## Legal

- https://openweathermap.org/price · https://openweather.co.uk/storage/app/media/Terms/Openweather_terms_and_conditions_of_sale.pdf (confirm live)
- Attribution per OpenWeather terms when displaying data.
- One Call is a **separate product**; a Current Weather key may 401 on `/data/3.0/onecall`.
- Do not redistribute as a competing weather feed beyond the licence.

## Implementation notes

- Query `appid`, `lat`, `lon`, `units`, `lang`. Header `User-Agent: StudioQuack/1.0`.
- JDK HTTP. TLS verified. Body cap 1 MiB. Timeout 20 s.
- Empty key → error, no HTTP. Missing lat/lon on weather ops → error, no HTTP.
- One Call 401 → `{ error: "One Call 3.0 requires a One Call subscription on this API key." }`
- No inner retry. 429 → 60 s.
