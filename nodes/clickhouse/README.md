# ClickHouse

Official ClickHouse **HTTP interface** (`OfficialApiNode` via `api_spec.json`). Customer-owned ClickHouse (self-host or Cloud). Not native TCP.

Provider docs: [HTTP interface](https://clickhouse.com/docs/interfaces/http)

- **Query:** `GET /?query=<sql>` plus official URL params `database`, `query_id`, `session_id`, `wait_end_of_query`, `default_format`.
- **Insert:** `POST /?query=INSERT+INTO+…+FORMAT+JSONEachRow` with newline-delimited JSON objects in the body (`rows`).
- **Ping:** `GET /ping` returns `Ok.`
- **Auth:** HTTP Basic (`user` + `password`). OfficialApiNode requires a non-empty `password`.
- Default origin `http://localhost:8123`. Edit `api_spec.json` `baseUrl` for Cloud (`https://<host>:8443`).

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `execute.sql` | GET | `/` | Official `query` (+ `database`, `query_id`, `session_id`, `wait_end_of_query`, `default_format`) |
| `insert.rows` | POST | `/` | Same query params; body is JSONEachRow NDJSON in `rows` |
| `ping` | GET | `/ping` | Health check |

`insert.rows` POSTs INPUT `rows` as JSONEachRow text (`bodyFormat=raw`): **one JSON object per line** (NDJSON), not a JSON array ([JSONEachRow](https://clickhouse.com/docs/interfaces/formats/JSONEachRow)). Set `query` to `INSERT INTO … FORMAT JSONEachRow`.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus official HTTP params. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `user` | string | no | Basic username (often `default`) |
| `password` | password | yes | Basic password |
| `database` | string | no | Default `database` query param |
| `timeout_seconds` | number | no | Default 25 |

## Legal

Customer is controller of query results. Official HTTP interface only. https://clickhouse.com/legal
