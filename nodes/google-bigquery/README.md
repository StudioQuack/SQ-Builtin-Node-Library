# Google BigQuery

Official BigQuery **REST v2** (`OfficialApiNode` via `api_spec.json`). Customer-owned GCP project.

- **Verified HTTP:** origin `https://bigquery.googleapis.com` — [REST reference](https://cloud.google.com/bigquery/docs/reference/rest) (2026-08-20).
  - Query: `POST /bigquery/v2/projects/{projectId}/queries` ([jobs.query](https://cloud.google.com/bigquery/docs/reference/rest/v2/jobs/query))
  - Stream insert: `POST /bigquery/v2/projects/{projectId}/datasets/{datasetId}/tables/{tableId}/insertAll` ([tabledata.insertAll](https://cloud.google.com/bigquery/docs/reference/rest/v2/tabledata/insertAll))
- **`baseUrl`:** `https://bigquery.googleapis.com` (static; no host substitution). Regional `bigquery.<location>.googleapis.com` is unused unless you edit `api_spec.json`.
- **Auth:** Bearer `oauth_access_token` (OAuth 2 access token or service-account access token). Scope `https://www.googleapis.com/auth/bigquery` or `cloud-platform`. Not a service-account PEM (engine cannot mint JWT). Never on INPUT.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | POST | `/bigquery/v2/projects/{projectId}/queries` |
| `table.insert` | POST | `/bigquery/v2/projects/{projectId}/datasets/{datasetId}/tables/{tableId}/insertAll` |

`execute.sql` bodyFrom `[query, useLegacySql, maxResults, defaultDataset]`. Set `useLegacySql` to `false` (GoogleSQL). `defaultDataset` is a DatasetReference object (`datasetId`, optional `projectId`). Parameterized queries (`queryParameters`) are official but not in this `bodyFrom` list — add keys to `api_spec.json` if needed. If `jobComplete` is false, poll is **not** automatic (no follow-up GET in this node).

`table.insert` bodyFrom `[rows, skipInvalidRows, ignoreUnknownValues]`. Each row is `{ "json": { … }, "insertId"? }` per insertAll.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `oauth_access_token` | password | yes | Bearer access token |
| `timeout_seconds` | number | no | HTTP timeout, default 45 |

`projectId` / `datasetId` / `tableId` are path params on INPUT (or config if you add them).

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Bytes billed to the customer GCP project. Result rows may be personal data; customer is controller.
- Google APIs ToS: https://developers.google.com/terms · Cloud: https://cloud.google.com/terms
- Official REST v2 only. Do not scrape the console.
