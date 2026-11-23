# Eurostat

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/eurostat` |
| `templateId` | `2da5e3d9-df92-5504-833f-1e99d9fc4406` |
| Env override | `SQ_TEMPLATE_ID_EUROSTAT` |
| Category | Data & Analytics |
| Official docs | https://ec.europa.eu/eurostat/web/user-guides/data-browser/api-data-access/api-detailed-guidelines/api-statistics |
| Auth | `none` — config `api_key` unused (public API) |
| Base URL | `https://ec.europa.eu/eurostat/api/dissemination` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `query` | GET | `/statistics/1.0/data/{datasetCode}?format=JSON` |
| `metadata_get` | GET | `/sdmx/2.1/dataflow/ESTAT/{datasetCode}?format=JSON` |

Default config operation is **`query`** when INPUT omits `operation`.

## Query filters

| Parameter | Applies to | Notes |
|---|---|---|
| `datasetCode` | both | Required path segment (Eurostat dataset id, e.g. `nama_10_gdp`). |
| `lang` | both | **`EN`**, **`FR`**, or **`DE`** only (case-insensitive). Default upstream is English. |
| `time` | `query` | Single **TIME_PERIOD** value: year `2022`, semester `2015-S1`, quarter `2015-Q4`, month `2015-02`, week `2020-W28`, day `2015-12-31`. Not a range — `2020-2022` and `2020--2022` are rejected locally and by Eurostat. |
| `sinceTimePeriod` / `untilTimePeriod` | `query` | Inclusive range; **must be used together** and **not** with `time` or `lastTimePeriod`. Same period code format as `time`. |
| `lastTimePeriod` | `query` | Positive integer — latest N periods (e.g. `1` = most recent). Cannot combine with `time` or since/until. |
| `geo` | `query` | Dimension filter (e.g. `DE`, `EU27_2020`); passed through to Eurostat — invalid codes fail upstream. |
| `geoLevel` | `query` | `aggregate` \| `country` \| `nuts1` \| `nuts2` \| `nuts3` \| `city` (validated locally). |
| `precision` | `query` | Decimal precision per official API. |

For multiple explicit periods, repeat `time` in the REST URL (`time=2019&time=2020`); this node sends one `time` query key per request — use multiple runs or extend INPUT if you need several `time` values in one call.

## Success output

On HTTP 2xx: `{ "status": "ok", "http_status": <number>, "data": <parsed JSON-stat / SDMX JSON> }`.  
`query` returns JSON-stat 2.0; `metadata_get` returns dataflow metadata JSON.

## Errors

Local: missing `datasetCode`, unknown `operation`, invalid `lang` / `time` / `geoLevel`, incompatible time filters.  
Upstream: structured provider JSON in the error payload (e.g. `HTTP_404` when the dataset is unknown or not disseminated, `HTTP_400` for invalid filters).  
If **every** dataset returns `ERR_NOT_FOUND_4 … not available for dissemination`, check daemon **outbound HTTPS** to `ec.europa.eu` (firewall/allow-list), not the wrapper.

## Legal

Commission reuse licence. Statistics API 1.0 JSON-stat 2.0. No API key. Citation required.

Operator accepts the provider terms. Official HTTP only — no HTML scrape.
