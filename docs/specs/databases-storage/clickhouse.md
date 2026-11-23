# ClickHouse

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/clickhouse/`
- **Researched:** 2026-08-20
- **Official API:** ClickHouse **HTTP interface** (default `:8123`, HTTPS `:8443`)
- **Docs:** https://clickhouse.com/docs/interfaces/http
- **Auth:** userPass / jwt — secret binding: `password` or `jwt`

## Scope

One node for **customer-owned** ClickHouse (self-host or Cloud). Prefer HTTP over the native TCP protocol so the node can use JDK HTTP. `POST` body carries SQL; `INSERT` may send data in the body with `FORMAT JSONEachRow`.

Parameterized queries: use ClickHouse `{name:Type}` or `param_*` query params as documented — never concatenate user values.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `ping` | GET | `/ping` | Liveness |
| `execute.sql` | POST | `/?query=` or body | Query / DDL / DML SQL |
| `row.insert` | POST | `/?query=INSERT+INTO+…+FORMAT+JSONEachRow` | Bulk JSON rows |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | |
| `host` | string | yes | empty | no | Hostname |
| `port` | number | no | `8443` | no | 8123 http / 8443 https |
| `secure` | boolean | no | `true` | no | HTTPS |
| `database` | string | no | `default` | no | |
| `user` | string | no | `default` | no | |
| `password` | password | no | empty | yes | `X-ClickHouse-Key` or URL userinfo — prefer headers `X-ClickHouse-User` / `X-ClickHouse-Key` |
| `jwt` | password | no | empty | yes | When `auth` is JWT (Cloud) |
| `timeout_seconds` | number | no | `30` | no | |
| `row_limit` | number | no | `10000` | no | Add `LIMIT` if missing on SELECT |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `ping`

None.

### `execute.sql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | SQL |
| `params` | object | no | Named ClickHouse parameters |
| `format` | string | no | Default `JSONEachRow` for SELECT |

### `row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `rows` | array | yes | JSON objects (max 5000, 10 MiB) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `rows` | array | query | Parsed JSONEachRow |
| `truncated` | boolean | query | |
| `error` | string | failure | ClickHouse exception message without secrets |
| `http_status` | number | HTTP error | |

## Legal

- ClickHouse Cloud ToS / Apache 2.0 for OSS: https://clickhouse.com/legal
- IP allowlists on Cloud are the customer’s duty
- Query results may be PII
- Official HTTP interface only

## Implementation notes

- JDK HTTP. Prefer headers for auth (do not put password in query string).
- URL `query` param size is limited (~1 MiB); send large SQL in POST body.
- Timeouts 30 s. Retry GET ping / idempotent SELECT 429/5xx once.
- Errors → `{ error }` on OUTPUT.
