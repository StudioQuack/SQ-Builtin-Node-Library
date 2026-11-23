# CrateDB

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/cratedb/`
- **Researched:** 2026-08-20
- **Official API:** HTTP `POST /_sql` (default `:4200`). PG wire exists on 5432 but **this node is HTTP** (JDK HTTP, distinct dialect).
- **Docs:** https://cratedb.com/docs/crate/reference/en/latest/interfaces/http.html
- **Auth:** userPass — secret binding: `password` (HTTP Basic)

## Scope

One node for **customer-owned** CrateDB. Not merged into PostgreSQL: SQL dialect and HTTP `/_sql` are the documented automation surface. Parameter substitution via JSON `args` (never string-built SQL). `bulk_args` for bulk INSERT.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.query` | POST | `/_sql` | `{ "stmt", "args"? }` |
| `row.insert` | POST | `/_sql?types=true` optional | INSERT with `args` or `bulk_args` |
| `row.update` | POST | `/_sql` | UPDATE with `args` |

Header `Default-Schema` sets schema.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.query` | no | |
| `host` | string | yes | empty | no | |
| `port` | number | no | `4200` | no | HTTP |
| `ssl` | boolean | no | `true` | no | HTTPS |
| `schema` | string | no | `doc` | no | Default-Schema header |
| `user` | string | no | empty | no | |
| `password` | password | no | empty | yes | Basic auth |
| `timeout_seconds` | number | no | `30` | no | |
| `row_limit` | number | no | `10000` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `execute.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | `stmt` |
| `args` | array | no | Parameter substitution |

### `row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | `INSERT INTO … VALUES (?, …)` |
| `bulk_args` | array | yes | List of arg lists (max 500) |

### `row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | UPDATE with `?` |
| `args` | array | yes | |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `cols` | array | query | Column names |
| `rows` | array | query | |
| `rowcount` | number | DML | |
| `col_types` | array | if requested | Type ids |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |

## Legal

- CrateDB license / Cloud ToS: https://cratedb.com
- Customer-owned cluster
- Official HTTP `_sql` only

## Implementation notes

- JDK HTTP JSON. Basic auth. `?types=true` optional.
- Bulk statements cannot return result sets (official).
- Timeouts 30 s. Retry GET-equivalent reads only.
- Errors → `{ error }` on OUTPUT.
