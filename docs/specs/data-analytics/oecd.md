# OECD

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/oecd/`
- **Researched:** 2026-08-20
- **Official API:** OECD Data Explorer SDMX REST (`https://sdmx.oecd.org/public/rest`)
- **Docs:** https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html
- **Auth:** none

## Scope

OECD.Stat (`stats.oecd.org` / `http://stats.oecd.org/SDMX-JSON/…`) was **taken offline 2024-07-01**. Do **not** implement the legacy OECD.Stat SDMX-JSON URL. Current official API is the Data Explorer SDMX REST host:

`https://sdmx.oecd.org/public/rest`

Data query pattern:

`GET {host}/data/{agency},{dataflow}@{version}/{key}?startPeriod=&endPeriod=&dimensionAtObservation=AllDimensions&format=jsondata`

Structure/dataflow list via `/dataflow/...` as documented. Citation required. No key.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `dataflow_list` | GET | `/dataflow/{agency}` or `/dataflow` per live cheat sheet | Discover datasets |
| `data_get` | GET | `/data/{flowRef}/{key}` | Observations |

Prefer SDMX-JSON (`format=jsondata` or `Accept: application/vnd.sdmx.data+json`). SDMX v2 path `/v2/data/dataflow/{agency}/{flowId}/{version}/{key}` is also official — pick **one** (v1.5 `/data/{flow}/{key}`) and document; do not call both.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `data_get` | no | |
| `base_url` | string | no | `https://sdmx.oecd.org/public/rest` | no | Override only for OECD mirrors |
| `timeout_seconds` | number | no | `45` | no | Large extracts are slow |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Cap payload |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = dataflow_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `agency` | string | no | e.g. `OECD.SDD.NAD`; empty → documented list-all if the API allows |

### `operation = data_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `flow` | string | yes | e.g. `OECD.SDD.NAD,DSD_NAAG@DF_NAAG_I` (agency,dataflow@version as OECD docs) |
| `key` | string | no | Dimension key or `all` |
| `start_period` | string | no | `startPeriod` |
| `end_period` | string | no | `endPeriod` |
| `format` | string | no | Default `jsondata` |

Reject `flow` that tries to target non-OECD hosts (SSRF: only `base_url` origin).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `dataflows` | array | list | id, name, agency |
| `data` | object | get | SDMX-JSON `data` (or compact observations array). Cap 1 MiB. |
| `truncated` | boolean | over cap | |
| `error` | string | failure | Include “OECD.Stat retired 2024-07-01” if someone still sends a stats.oecd.org URL |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx / timeout | |

## Legal

- https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html
- Upgrade from OECD.Stat: OECD Data API migration PDF (gitlab.algobank.oecd.org public-documentation)
- OECD Terms and Conditions; citation required. No authentication for public data.
- Do not scrape the Data Explorer HTML.

## Implementation notes

- JDK GET. `User-Agent: StudioQuack/1.0`. TLS verified.
- Body cap 1 MiB (OECD extracts can be huge — fail closed with `truncated` or error).
- Timeout 45 s. No inner retry.
- Never call `stats.oecd.org`.
