# OpenStreetMap / Nominatim

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/openstreetmap-nominatim` |
| `templateId` | `8fe8fb61-7e86-5b06-a9bc-9c45bdcb8654` |
| Env override | `SQ_TEMPLATE_ID_OPENSTREETMAP_NOMINATIM` |
| Category | Data & Analytics |
| Official docs | https://nominatim.org/release-docs/latest/api/Overview/ |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `nominatim_search` | http GET | `{nominatim_host}/search?format=jsonv2` |
| `nominatim_reverse` | http GET | `{nominatim_host}/reverse?format=jsonv2` |
| `nominatim_lookup` | http GET | `{nominatim_host}/lookup?format=jsonv2` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

OSMF Nominatim usage policy: max ~1 req/s, identifying UA, ODbL attribution. Do not automate public nominatim.openstreetmap.org as a generic platform geocoder. Set nominatim_host to a host you are allowed to use (self-hosted or commercial). No autocomplete. Do not scrape OSM tiles/HTML.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
