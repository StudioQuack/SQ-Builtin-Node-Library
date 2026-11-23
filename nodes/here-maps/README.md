# HERE Maps

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/here-maps` |
| `templateId` | `4ffbc15c-dd67-5f74-8867-15dd7163e64f` |
| Env override | `SQ_TEMPLATE_ID_HERE_MAPS` |
| Category | Data & Analytics |
| Official docs | https://docs.here.com/geocoding-and-search/docs/request-constructing |
| Auth | `query` — config `apiKey` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `geocode` | http GET | `geocode.search.hereapi.com/v1/geocode` |
| `reverse` | http GET | `geocode.search.hereapi.com/v1/revgeocode` |
| `discover` | http GET | `discover.search.hereapi.com/v1/discover` |
| `route` | http GET | `router.hereapi.com/v8/routes` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

HERE developer terms. API key (apiKey query). Attribution required. Geocoding & Search v7 hosts *.search.hereapi.com/v1; Routing v8 is router.hereapi.com/v8/routes. Hosts differ per SKU; paths are host+path because OfficialApiNode has one baseUrl.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
