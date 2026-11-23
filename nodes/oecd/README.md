# OECD

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/oecd` |
| `templateId` | `c98fb19a-eccb-5bc8-82c4-ebeb872d03e9` |
| Env override | `SQ_TEMPLATE_ID_OECD` |
| Category | Data & Analytics |
| Official docs | https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html |
| Auth | `none` |

## Operations

Verified 2026-08-22: [OECD API explainer](https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html) · [SDMX REST 2.0 swagger](https://sdmx.oecd.org/public/swagger/index.html?urls.primaryName=v2) · [public service root](https://sdmx.oecd.org/public) (RESTful API 2.0 = `https://sdmx.oecd.org/public/rest/v2`).

| operation | HTTP | Path |
|---|---|---|
| `dataflow_list` | GET | `/structure/dataflow/*/*/*` |
| `data_get` | GET | `/data/dataflow/{agencyID}/{dataflowID}/{version}/{key}` |

Base: `https://sdmx.oecd.org/public/rest/v2`. Accept headers: structure `application/vnd.sdmx.structure+json;charset=utf-8;version=2.0.0`, data `application/vnd.sdmx.data+json;charset=utf-8;version=2.0.0`. Optional v2 query: `time_period` (sent as `c[TIME_PERIOD]`, e.g. `ge:2020-Q1+le:2024-Q4`), `dimensionAtObservation`, `attributes`, `measures`, `updatedAfter`, `firstNObservations`, `lastNObservations`, `detail`, `includeHistory`, `asOf`. SDMX REST v2 has no `startPeriod`/`endPeriod`.

SDMX REST 1.5 paths under `https://sdmx.oecd.org/public/rest` remain on the server; this node is a **hard cutover** to v2.

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

OECD SDMX REST. stats.oecd.org went offline 2024-07-01. Citation required.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
