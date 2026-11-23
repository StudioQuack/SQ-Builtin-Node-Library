# Mapbox

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/mapbox` |
| `templateId` | `da70b5c1-dd73-5bc8-8cf1-87a3ab7d506f` |
| Env override | `SQ_TEMPLATE_ID_MAPBOX` |
| Category | Data & Analytics |
| Official docs | https://docs.mapbox.com/api/search/geocoding/ |
| Auth | `query` — config `access_token` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `geocode` | http GET | `/search/geocode/v6/forward` |
| `reverse` | http GET | `/search/geocode/v6/reverse` |
| `directions` | http GET | `/directions/v5/mapbox/{profile}/{coordinates}` |
| `isochrone` | http GET | `/isochrone/v1/mapbox/{profile}/{coordinates}` |
| `tilequery` | http GET | `/v4/{tileset}/tilequery/{coordinates}.json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{status,http_status,data}`.

## Legal

Mapbox ToS. Geocoding v6. Directions v5. Isochrone v1. Tilequery GET /v4/{tileset_id}/tilequery/{lon},{lat}.json. Attribution required. Temporary geocodes must not be stored unless permanent=true. Token is access_token query.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
