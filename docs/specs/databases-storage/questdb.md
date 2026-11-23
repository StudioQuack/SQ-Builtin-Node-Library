# QuestDB

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/questdb/`
- **Researched:** 2026-08-20
- **Official API:** ILP (Influx Line Protocol) for ingest + PostgreSQL wire / HTTP SQL for query
- **Docs:** https://questdb.com/docs/ingestion/influxdb-line-protocol/ · https://questdb.com/docs/develop/query-data/
- **Auth:** userPass / none — secret binding: `password` when enabled

## Scope

One node for **customer-owned** QuestDB. Keep separate from PostgreSQL: **ILP insert** is a distinct protocol (`ilpPort`, often 9009). SQL query may use HTTP `/exec` or PG wire (`pgPort` 8812). Default installs often expose **unauthenticated HTTP** — the node must document lock-down; prefer auth + TLS in production.

No host filesystem (`COPY FROM` local files forbidden).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.sql` | GET/POST | `/exec` (`?query=`) or PG wire | Parameterized SQL where supported |
| `row.insert` | TCP/HTTP | ILP | High-volume ingest |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | |
| `host` | string | yes | empty | no | |
| `http_port` | number | no | `9000` | no | REST |
| `pg_port` | number | no | `8812` | no | PG wire if used |
| `ilp_port` | number | no | `9009` | no | ILP TCP |
| `ilp_http` | boolean | no | `true` | no | Prefer ILP over HTTP if documented for the version |
| `user` | string | no | empty | no | |
| `password` | password | no | empty | yes | |
| `ssl` | boolean | no | `true` | no | |
| `timeout_seconds` | number | no | `30` | no | |
| `row_limit` | number | no | `10000` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `execute.sql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | SQL. Bind via HTTP `bind` / PG `$1` when available |
| `params` | array | no | |

### `row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Measurement / table |
| `rows` | array | yes | `{ symbols?, fields, timestamp? }` max 5000. Serialized as ILP |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `dataset` | array | query | QuestDB JSON dataset |
| `count` | number | insert | Lines accepted |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |

## Legal

- Apache 2.0 / QuestDB Cloud ToS: https://questdb.com
- Open HTTP by default is an operator security issue — require TLS+auth in production notes
- Customer-owned DB
- Official ILP + SQL APIs only

## Implementation notes

- Prefer JDK HTTP for `/exec` and ILP-over-HTTP. TCP ILP if HTTP ILP unavailable (**Unverified per deploy**).
- Never enable `allow_http` assumptions; if 401, fail with auth guidance.
- Timeouts 30 s. Retry idempotent SELECT only.
- Errors → `{ error }` on OUTPUT.
