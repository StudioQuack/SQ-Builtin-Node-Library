# Nasdaq Data Link

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/nasdaq-data-link` |
| `templateId` | `5281d205-2f21-5e4d-8590-f319fabef8d5` |
| Env override | `SQ_TEMPLATE_ID_NASDAQ_DATA_LINK` |
| Category | Data & Analytics |
| Official docs | https://docs.data.nasdaq.com/docs/getting-started |
| Auth | `query` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `dataset_get` | http GET | `/datasets/{database_code}/{dataset_code}.json` |
| `datatable_get` | http GET | `/datatables/{publisher}/{table}.json` |
| `metadata_get` | http GET | `/datasets/{database_code}/{dataset_code}/metadata.json` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

Nasdaq Data Link REST (formerly Quandl). Many tables are licensed premium — do not cache/resell. docs.data.nasdaq.com retires 2026-08-31; bookmark successor Data Access Tools.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
