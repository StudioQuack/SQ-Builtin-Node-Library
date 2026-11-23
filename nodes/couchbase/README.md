# Couchbase

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.couchbase.com/server/current/n1ql-rest-query/index.html
- **Verified official HTTP:** `http://localhost:8093` (researched 2026-08-20)
- **Auth:** `password` (password secret). HTTP Basic (`username` + `password`).
- Customer owns the database / project. StudioQuack stores only the secret.

Official Query Service `POST /query/service` on port 8093. Body fields: `statement` (required), `args`, `timeout`, `scan_consistency` (`not_bounded` | `request_plus` | `statement_plus`), `query_context`, `client_context_id`, `readonly`, `max_parallelism`, `pretty`, `metrics`. KV Data API on 8091 is a different surface. Replace `api_spec.baseUrl` for Capella Query.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `query.n1ql` | POST | `/query/service` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `password` | password | yes | Official credential |
| `timeout_seconds` | number | no | Default 25 |

`api_spec.baseUrl` is static. OfficialApiNode does not substitute host. Point this file at your cluster origin when it is not the documented default.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `password`.

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the SQL / rows / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP/SQL API only. No invented wire-over-HTTP proxy.
