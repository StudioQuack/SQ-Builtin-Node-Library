# CrateDB

Official CrateDB **HTTP SQL** (`OfficialApiNode` via `api_spec.json`). Customer-owned cluster. Not PostgreSQL wire (`:5432`).

- **Verified HTTP:** `POST http://localhost:4200/_sql` with JSON `{ "stmt", "args"? }` — [HTTP endpoint](https://cratedb.com/docs/crate/reference/en/latest/interfaces/http.html) (2026-08-20).
- **Bulk insert:** same path with `bulk_args` (list of arg lists) instead of / in addition to `args`. Official; not a second protocol.
- **Placeholder `baseUrl`:** `http://localhost:4200`. OfficialApiNode does **not** substitute host. Edit `api_spec.json` `baseUrl` for TLS Cloud (`https://<host>:4200`).
- **Auth:** HTTP Basic (`username` + `password`). OfficialApiNode requires a non-empty `password` even when the cluster uses trust auth.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | POST | `/_sql` |
| `table.insert` | POST | `/_sql` |

`execute.sql` bodyFrom `[stmt, args]`. `table.insert` bodyFrom `[stmt, args, bulk_args]` (official bulk INSERT). Use `?` or `$1` placeholders; never concatenate user values into `stmt`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `username` | string | no | Basic username (often `crate`) |
| `password` | password | yes | Basic password |
| `timeout_seconds` | number | no | Default 25 |

The official `Default-Schema` header is not sent (engine sends auth + JSON only). Qualify names in `stmt` or `SET` in SQL.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `password`.

## Legal

- Customer is controller of table data. Minimize: send only `stmt` / binds required.
- License / Cloud ToS: https://cratedb.com
- Official `/_sql` only. Do not scrape the Admin UI.
