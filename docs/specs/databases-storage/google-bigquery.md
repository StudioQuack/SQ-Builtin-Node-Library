# Google BigQuery

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/google-bigquery/`
- **Researched:** 2026-08-20
- **Official API:** BigQuery REST **v2**
- **Docs:** https://docs.cloud.google.com/bigquery/docs/reference/rest/v2/jobs/query · https://docs.cloud.google.com/bigquery/docs/reference/rest/v2/tabledata/insertAll
- **Auth:** oauth2 / serviceAccount — secret binding: `private_key_pem` (or daemon-held OAuth refresh)

## Scope

One node for **customer-owned** GCP projects. `job.query` runs GoogleSQL via `POST https://bigquery.googleapis.com/bigquery/v2/projects/{projectId}/queries`. `table.insert` uses `tabledata.insertAll`.

Always `useLegacySql=false`. Always parameterized queries (`parameterMode=NAMED` or `POSITIONAL`) for user values. Bytes billed are charged to the customer project.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `job.query` | POST | `/bigquery/v2/projects/{projectId}/queries` | Synchronous query (poll `jobs.getQueryResults` if `jobComplete=false`) |
| `table.insert` | POST | `/bigquery/v2/projects/{projectId}/datasets/{datasetId}/tables/{tableId}/insertAll` | Streaming insert |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `job.query` | no | Chooses the row above |
| `auth` | select | yes | `serviceAccount` | no | `serviceAccount` \| `oauth2` |
| `project_id` | string | yes | empty | no | GCP project billed |
| `location` | string | no | empty | no | Job location (e.g. `US`, `EU`) |
| `client_email` | string | if SA | empty | no | Service account email |
| `private_key_pem` | password | if SA | empty | yes | SA private key PEM. Not a host path |
| `oauth_access_token` | password | if oauth2 | empty | yes | Short-lived access token |
| `timeout_ms` | number | no | `20000` | no | `timeoutMs` on jobs.query (API may return early) |
| `max_results` | number | no | `1000` | no | Page size; hard cap 10_000 total fetched |
| `maximum_bytes_billed` | string | no | empty | no | Optional query cost guard |

OAuth scope: `https://www.googleapis.com/auth/bigquery` or `cloud-platform`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No PEM |

FUNCTION ports: none.

## Input fields (by operation)

### `job.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | GoogleSQL |
| `query_parameters` | array | no | `{ name, parameterType, parameterValue }` as REST QueryParameter |
| `default_dataset` | string | no | `datasetId` or `project.dataset` |
| `dry_run` | boolean | no | Validate / estimate without running |
| `use_query_cache` | boolean | no | Default true |

If `jobComplete` is false: poll `jobs.getQueryResults` until complete or timeout. Do not hold unbounded.

### `table.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dataset_id` | string | yes | Dataset |
| `table_id` | string | yes | Table |
| `rows` | array | yes | Objects with `json` row maps (max 500 / invoke; 10 MB request) |
| `skip_invalid_rows` | boolean | no | Default false |
| `ignore_unknown_values` | boolean | no | Default false |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `job_id` | string | query | Job reference |
| `schema` | object | query | TableSchema |
| `rows` | array | query | Result rows |
| `total_rows` | string | query | From API |
| `truncated` | boolean | query | Local cap |
| `bytes_processed` | string | query | From job stats when present |
| `insert_errors` | array | insert | Per-row insert errors |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | Google status |

## Legal

- Google APIs ToS: https://developers.google.com/terms
- BigQuery: https://cloud.google.com/bigquery/docs
- Query bytes billed to the customer GCP billing account
- Result rows may be PII; customer owns the dataset
- Official REST v2 only

## Implementation notes

- JDK HTTP + OAuth SA JWT. No BigQuery SDK required.
- Connect 10 s; overall poll budget = config timeout + 60 s max.
- Retry 429 / 503 with jitter (Google retry guidance). No retry on 400.
- Never log `private_key_pem` or row JSON.
- Errors → `{ error }` on OUTPUT.
