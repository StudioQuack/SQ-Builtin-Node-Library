# InfluxDB

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.influxdata.com/influxdb3/core/query-data/execute-queries/influxdb-v3-api/
- **Verified official HTTP:** `http://localhost:8181` (researched 2026-08-20)
- **Auth:** `token` (password secret). Bearer.
- Customer owns the database / project. StudioQuack stores only the secret.

Verified 2026-08-20: official InfluxDB 3 `POST /api/v3/query_sql` and `/api/v3/query_influxql` on port 8181. Flux is not supported on v3. Line-protocol write is raw text — skipped (OfficialApiNode JSON-encodes bodies).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `query.sql` | POST | `/api/v3/query_sql` |
| `query.influxql` | POST | `/api/v3/query_influxql` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `token` | password | yes | Official credential |
| `timeout_seconds` | number | no | Default 25 |

`api_spec.baseUrl` is static. OfficialApiNode does not substitute host. Point this file at your cluster origin when it is not the documented default.

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the SQL / rows / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP/SQL API only. No invented wire-over-HTTP proxy.
