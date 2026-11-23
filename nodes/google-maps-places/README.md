# Google Maps / Places

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/google-maps-places` |
| `templateId` | `602c698f-9bc5-56f8-8b41-bc5c532cea93` |
| Env override | `SQ_TEMPLATE_ID_GOOGLE_MAPS_PLACES` |
| Category | Data & Analytics |
| Official docs | https://developers.google.com/maps/documentation/geocoding/overview |
| Auth | `query` — config `key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `geocode` | http GET | `/maps/api/geocode/json` |
| `reverse_geocode` | http GET | `/maps/api/geocode/json` |
| `elevation` | http GET | `/maps/api/elevation/json` |
| `timezone` | http GET | `/maps/api/timezone/json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Maps Platform Terms. Billing per SKU. Places API (New) and Routes need X-Goog-FieldMask — OfficialApiNode v1 ships Geocoding/Elevation/Timezone GET. Do not scrape Google Maps. Do not call legacy Directions/Distance Matrix on new projects.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
