# US Census Bureau

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/us-census-bureau` |
| `templateId` | `076e7939-ea47-595a-8442-068a1788f77b` |
| Env override | `SQ_TEMPLATE_ID_US_CENSUS_BUREAU` |
| Category | Data & Analytics |
| Official docs | https://www.census.gov/data/developers/guidance/api-user-guide.html |
| Auth | `query` — config `key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `census_get` | http GET | `/{vintage}/{dataset}` |
| `geography_list` | http GET | `/{vintage}/{dataset}/geography.json` |

**Required INPUT**

| operation | Fields |
|---|---|
| `census_get` | `vintage`, `dataset`, `get`, `for` (optional `in`) |
| `geography_list` | `vintage`, `dataset` |

`dataset` is always **two path segments** (`program/dataset`), e.g. `dec/sf1`, `acs/acs5`. A single segment such as `dec` hits the collection catalog, not tabular data. `vintage` is the four-digit dataset year (e.g. `2010` for many `dec/*` sets).

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Census Data API. A free API key is required for all queries as of 2026 (https://www.census.gov/library/video/2026/adrm/requesting-a-census-data-api-key.html). US Government work is generally public domain plus embargo notes.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
