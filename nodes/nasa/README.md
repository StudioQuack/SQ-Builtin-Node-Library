# NASA

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/nasa` |
| `templateId` | `d6d434ee-0bf8-5c69-9fd3-5ed453d08bb8` |
| Env override | `SQ_TEMPLATE_ID_NASA` |
| Category | News & Reference |
| Official docs | https://api.nasa.gov/ |
| Auth | `query` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `apod` | http GET | `/planetary/apod` |
| `neo` / `neo_feed` | http GET | `/neo/rest/v1/feed` |
| `neo_lookup` | http GET | `/neo/rest/v1/neo/{asteroid_id}` |
| `neo_browse` | http GET | `/neo/rest/v1/neo/browse` |
| `donki` / `donki_notifications` | http GET | `/DONKI/notifications` |
| `donki_cme` | http GET | `/DONKI/CME` |
| `donki_flares` | http GET | `/DONKI/FLR` |
| `earth_imagery` / `epic` | http GET | `/EPIC/api/natural` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

NASA Open APIs. Query `api_key`. DEMO_KEY is shared/rate-limited. `earth_imagery` is EPIC (Earth API archived). `mars_rover` omitted (archived). NASA media guidelines.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
