# Google Maps / Places

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/google-maps-places/`
- **Researched:** 2026-08-20
- **Official API:** Geocoding API + Places API **(New)** + Routes API v2
- **Docs:** https://developers.google.com/maps/documentation/geocoding/overview · https://developers.google.com/maps/documentation/places/web-service/op-overview · https://developers.google.com/maps/documentation/routes/compute-route-over
- **Auth:** API key (query or header `X-Goog-Api-Key` / `X-Goog-FieldMask` for new APIs) — secret binding: `api_key`

## Scope

One node for Maps Platform **current** products. **Do not scrape Google Maps HTML.**

- **Places API (New)** — Text Search, Nearby Search, Place Details. Do not implement legacy Places Nearby/Find Place unless New is unavailable (it is GA).
- **Routes API** — `computeRoutes` and `computeRouteMatrix`. Directions API and Distance Matrix API were moved to **legacy** (new Cloud projects cannot enable them as of 2025-03-01). Do **not** implement legacy Directions/Distance Matrix.
- **Geocoding API** — forward/reverse (still current).
- **Elevation API** and **Time Zone API** — still official; include if still listed in Maps Platform docs at implement time.

Billing is per SKU. Operator must enable each API in Cloud Console.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `geocode` | GET | `https://maps.googleapis.com/maps/api/geocode/json` | Address → lat/lng |
| `reverse_geocode` | GET | same `latlng=` | Lat/lng → address |
| `places_search_text` | POST | `https://places.googleapis.com/v1/places:searchText` | Places API (New) text search |
| `places_search_nearby` | POST | `https://places.googleapis.com/v1/places:searchNearby` | Nearby (New) |
| `places_details` | GET | `https://places.googleapis.com/v1/places/{placeId}` | Details (New) |
| `compute_routes` | POST | `https://routes.googleapis.com/directions/v2:computeRoutes` | Directions replacement |
| `compute_route_matrix` | POST | `https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix` | Distance Matrix replacement |
| `elevation` | GET | `https://maps.googleapis.com/maps/api/elevation/json` | Elevation |
| `timezone` | GET | `https://maps.googleapis.com/maps/api/timezone/json` | Time zone for a point |

Confirm exact Places/Routes URLs in Google docs at implement time (field masks are mandatory on New APIs).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `geocode` | no | |
| `api_key` | password | yes | empty | yes | Cloud API key. Header on New APIs; `key=` on Geocoding/Elevation/Timezone. |
| `default_field_mask` | string | no | `places.id,places.displayName,places.formattedAddress,places.location` | no | `X-Goog-FieldMask` default for Places search |
| `timeout_seconds` | number | no | `25` | no | |

OAuth client id/secret from the catalog row is **not** required for these server-to-server Maps SKUs; omit unless an operation documents otherwise.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No API key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = geocode`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `address` | string | yes | Address or plus-code |
| `region` | string | no | ccTLD bias |
| `language` | string | no | |

### `operation = reverse_geocode`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | |
| `lng` | number | yes | |
| `language` | string | no | |

### `operation = places_search_text`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text_query` | string | yes | `textQuery` |
| `language_code` | string | no | |
| `max_result_count` | number | no | Cap ≤ 20 |
| `field_mask` | string | no | Override |

### `operation = places_search_nearby`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | Circle center |
| `lng` | number | yes | |
| `radius_meters` | number | yes | |
| `included_types` | array | no | Place types |
| `field_mask` | string | no | |

### `operation = places_details`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `place_id` | string | yes | `places/{id}` resource or raw Place ID |
| `field_mask` | string | no | Required by Google; default `id,displayName,formattedAddress,location,googleMapsUri` |

### `operation = compute_routes`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `origin` | object | yes | `{ lat, lng }` or address |
| `destination` | object | yes | Same |
| `travel_mode` | string | no | `DRIVE` default |
| `field_mask` | string | no | Mandatory header; default `routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline` |

### `operation = compute_route_matrix`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `origins` | array | yes | Waypoints |
| `destinations` | array | yes | |
| `travel_mode` | string | no | |
| `field_mask` | string | no | Mandatory |

Cap origins×destinations (Google documented max).

### `operation = elevation`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `locations` | string | yes | `lat,lng` or path as documented |

### `operation = timezone`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lat` | number | yes | |
| `lng` | number | yes | |
| `timestamp` | number | yes | Unix seconds |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` (also map Geocoding `status=OK`) |
| `results` | array | geocode/places | Normalized `{ formatted_address, lat, lng, place_id }` |
| `routes` | array | compute_routes | duration, distance, polyline |
| `matrix` | array | matrix | cells |
| `elevation` | array | elevation | |
| `time_zone_id` | string | timezone | |
| `error` | string | failure | REQUEST_DENIED → enable API in Console |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

Place Photos: return photo **resource names** only in v1; fetching bytes is a later `file` operation (quota-heavy).

## Legal

- Google Maps Platform Terms: https://cloud.google.com/maps-platform/terms
- Places (New): attribution / no caching beyond ToS; reviews and photos have extra rules.
- Routes/Geocoding: no storing results except as ToS allows; display on a Google Map when required.
- Billing per request SKU. Field masks change price on Places/Routes.
- **No scraping** maps.google.com.

## Implementation notes

- New APIs: headers `X-Goog-Api-Key`, `X-Goog-FieldMask`, `Content-Type: application/json`. Missing field mask → 400; fail closed with a clear error before retry.
- Legacy `key=` query for Geocoding/Elevation/Timezone.
- JDK HTTP. TLS verified. Body cap 1 MiB. Timeout 25 s.
- Never log `api_key`. No inner retry.
- Do not register Directions/Distance Matrix paths.
