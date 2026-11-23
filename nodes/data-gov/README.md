# data.gov

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/data-gov` |
| `templateId` | `988b172f-a753-53b8-8047-9e217c50037d` |
| Env override | `SQ_TEMPLATE_ID_DATA_GOV` |
| Category | Data & Analytics |
| Official docs | https://resources.data.gov/catalog-api/ |
| Auth | header `X-Api-Key` from configuration `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | GET | `/search` |
| `keywords` | GET | `/keywords` |
| `organizations` | GET | `/organizations` |
| `locations_search` | GET | `/locations/search` |
| `location_get` | GET | `/location/{location_id}` |
| `harvest_record` | GET | `/harvest_record/{record_id}` |
| `harvest_record_raw` | GET | `/harvest_record/{record_id}/raw` |
| `harvest_record_transformed` | GET | `/harvest_record/{record_id}/transformed` |

Base URL `https://api.gsa.gov/technology/datagov/v4`. Default operation is `search` (no required query field). `location_get` requires `location_id`. The three harvest operations require `record_id`.

`search` query fields: `q`, `sort` (`relevance`, `popularity`, `distance`, `last_harvested_date`), `per_page`, `org_slug`, `org_type`, `keyword` (one exact keyword), `spatial_filter` (`geospatial` or `non-geospatial`), `spatial_geometry`, `spatial_within` (`true` or `false`), `after` (cursor from the previous response).

`keywords` query fields: `size`, `min_count`. `locations_search` query fields: `q`, `size`.

## Auth

`api_key` is a configuration parameter, never an INPUT field. A blank key fails before HTTP with `api_key is required (StudioQuack secret binding)`. The provider publishes `DEMO_KEY` for trials; it is rate-limited. Bind a StudioQuack secret for real use.

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error, http_status? }`.

## Legal

Catalog API v4. The CKAN host `catalog.data.gov/api/3` was retired and is deliberately unused. US catalog metadata is generally public-domain; honor each dataset license.

Operator accepts the provider terms. Official HTTP only. Secrets are StudioQuack bindings; never logged.
