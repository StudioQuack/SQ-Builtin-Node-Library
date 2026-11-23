# Mapbox

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/mapbox/`
- **Researched:** 2026-08-20
- **Official API:** Mapbox Geocoding **v6** + Directions + Isochrone + Tilequery
- **Docs:** https://docs.mapbox.com/api/search/geocoding/ · https://docs.mapbox.com/api/navigation/directions/ · https://docs.mapbox.com/api/navigation/isochrone/ · https://docs.mapbox.com/api/maps/tilequery/
- **Auth:** access token — secret binding: `access_token` (query `access_token`). Prefer a **secret** token for server-side; public tokens are URL-visible.

## Scope

Current Geocoding is **v6**:

- Forward: `GET https://api.mapbox.com/search/geocode/v6/forward?q=`
- Reverse: `GET https://api.mapbox.com/search/geocode/v6/reverse?longitude=&latitude=`

Do **not** implement Geocoding v5. v6 has **no POI** — POI/search-box is a different SKU (`Search Box API`); omit in v1 unless catalog expands. Temporary geocoding is default (`permanent` param). Attribution required when displaying map results. Do not scrape mapbox.com.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `geocode` | GET | `/search/geocode/v6/forward` | Address/place → coords |
| `reverse` | GET | `/search/geocode/v6/reverse` | Coords → address |
| `directions` | GET | `/directions/v5/mapbox/{profile}/{coords}` | Routing (confirm v5 Directions still current in Mapbox docs) |
| `isochrone` | GET | `/isochrone/v1/mapbox/{profile}/{lon},{lat}` | Travel-time polygons |
| `tilequery` | GET | `/v4/{tileset}/{lon},{lat}.json` | Vector tile features at a point |

Confirm Directions path version on https://docs.mapbox.com/api/navigation/directions/ at implement time.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `geocode` | no | |
| `access_token` | password | yes | empty | yes | Mapbox token. Never on INPUT. |
| `permanent` | boolean | no | `false` | no | Geocoding storage mode (`permanent=true` is billed/account-gated) |
| `timeout_seconds` | number | no | `20` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = geocode`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q` |
| `limit` | number | no | |
| `country` | string | no | |
| `proximity` | string | no | `lon,lat` or `ip` |
| `types` | string | no | Feature types |
| `language` | string | no | |

### `operation = reverse`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `longitude` | number | yes | |
| `latitude` | number | yes | |
| `types` | string | no | |
| `language` | string | no | |

### `operation = directions`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `coordinates` | string | yes | `lon,lat;lon,lat;…` |
| `profile` | string | no | `driving` \| `walking` \| `cycling` \| `driving-traffic` |

### `operation = isochrone`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `longitude` | number | yes | |
| `latitude` | number | yes | |
| `profile` | string | no | |
| `contours_minutes` | string | yes | e.g. `5,10,15` |

### `operation = tilequery`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tileset` | string | yes | Mapbox tileset id |
| `longitude` | number | yes | |
| `latitude` | number | yes | |
| `radius` | number | no | Meters |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `features` | array | geocode/reverse/tilequery/isochrone | GeoJSON features (cap) |
| `routes` | array | directions | Distance, duration, geometry |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 (~1000 rpm default) | |

Do not persist geocodes unless `permanent=true` and the operator’s Mapbox contract allows it.

## Legal

- https://docs.mapbox.com/api/search/geocoding/
- https://www.mapbox.com/legal/tos
- Attribution: https://docs.mapbox.com/help/getting-started/attribution/
- Temporary vs permanent geocoding: default temporary — **do not cache** coords unless `permanent=true`.
- Rate limit typically 1000 req/min; HTTP 429.

## Implementation notes

- Always pass `access_token` as query. Prefer secret token. Never log it (query strings appear in URLs — redact in error messages).
- JDK HTTP. TLS verified. Body cap 1 MiB. Timeout 20 s.
- Empty token → error, no HTTP.
- No vendor SDK.
