# SurrealDB

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://surrealdb.com/docs/reference/rest-api/http-protocol
- **Verified official HTTP:** `http://localhost:8000` (researched 2026-08-22)
- **Auth:** `password` (password secret). HTTP Basic (`username` + `password`).
- Customer owns the database / project. StudioQuack stores only the secret.

Verified 2026-08-22: official HTTP `/key/:table/:id` ([HTTP protocol](https://surrealdb.com/docs/reference/rest-api/http-protocol)). extraHeaders send `Surreal-NS` and `Surreal-DB` from config `namespace` and `database`. `/sql` expects raw SurrealQL text; skipped because the engine JSON-encodes bodies.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `record.select` | GET | `/key/{table}/{id}` |
| `record.create` | POST | `/key/{table}` |
| `record.update` | PUT | `/key/{table}/{id}` |
| `record.delete` | DELETE | `/key/{table}/{id}` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `username` | string | no | HTTP Basic username |
| `password` | password | yes | HTTP Basic password |
| `namespace` | string | no | `Surreal-NS` header |
| `database` | string | no | `Surreal-DB` header |
| `surreal_url` | string | no | HTTP origin, e.g. `http://127.0.0.1:8000` |
| `timeout_seconds` | number | no | Default 25 |

`record.create` / `record.update` merge INPUT `record` as the JSON body (object = one record; array = bulk create). Config `surreal_url` is the HTTP origin (default `http://localhost:8000`).

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the SQL / rows / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP/SQL API only. No invented wire-over-HTTP proxy.
