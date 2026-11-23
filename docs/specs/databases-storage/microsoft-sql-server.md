# Microsoft SQL Server

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/microsoft-sql-server/`
- **Researched:** 2026-08-20
- **Official API:** Tabular Data Stream (TDS) over TCP (default **1433**)
- **Docs:** https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-tds/ · https://learn.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server
- **Auth:** userPass / azureAd — secret binding: `password` or `azure_ad_token`

## Scope

One node for **customer-owned** SQL Server / Azure SQL. Operations are parameterized T-SQL via the official TDS driver. Azure AD token auth is optional (`auth=azureAd`).

Encrypt in transit (`encrypt=true`). No host filesystem paths. `instanceName` is a SQL Browser name, not a file path.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.query` | TDS | prepared / `sp_executesql` | Parameterized T-SQL |
| `row.insert` | TDS | parameterized INSERT | Insert |
| `row.update` | TDS | parameterized UPDATE | Update with WHERE |
| `row.delete` | TDS | parameterized DELETE | Delete with WHERE |
| `procedure.call` | TDS | RPC call | Stored procedure |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.query` | no | |
| `auth` | select | no | `userPass` | no | `userPass` \| `azureAd` |
| `host` | string | yes | empty | no | Hostname |
| `port` | number | no | `1433` | no | |
| `database` | string | yes | empty | no | |
| `user` | string | if userPass | empty | no | |
| `password` | password | if userPass | empty | yes | |
| `azure_ad_token` | password | if azureAd | empty | yes | Access token |
| `encrypt` | boolean | no | `true` | no | TLS |
| `trust_server_certificate` | boolean | no | `false` | no | Dev-only; production false |
| `instance_name` | string | no | empty | no | Named instance |
| `timeout_ms` | number | no | `30000` | no | |
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
| `sql` | string | yes | T-SQL with `@p1` / `?` binds |
| `params` | array | no | Bind values |

### `row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Quoted identifier |
| `row` or `rows` | object / array | yes | Values (batch 500) |

### `row.update` | `row.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | |
| `row` | object | update | SET map |
| `where` | string | yes | Bound predicate; empty forbidden |
| `params` | array | yes | |

### `procedure.call`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `procedure` | string | yes | Proc name |
| `params` | object | no | Named params |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `columns` | array | queries | |
| `rows` | array | queries | |
| `row_count` | number | DML | |
| `truncated` | boolean | queries | |
| `error` | string | failure | No secrets |

## Legal

- Microsoft software license / Azure SQL ToS: https://www.microsoft.com/licensing/
- Customer-owned database. Results may be PII
- Official TDS / JDBC only

## Implementation notes

- Binary TDS — not JDK HTTP. Microsoft JDBC driver.
- TLS on. Timeouts 30 s. Row cap 10_000. SQL text ≤ 1 MiB.
- Retry connect only.
- Errors → `{ error }` on OUTPUT.
