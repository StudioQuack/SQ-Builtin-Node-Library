# MySQL

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/mysql/`
- **Researched:** 2026-08-20
- **Official API:** MySQL Client/Server Protocol (binary + text). Production LTS: MySQL 8.4 (docs example 8.4.9). Innovation: MySQL 9.x (protocol docs currently published as 9.7 / “latest” 26.x source tree). Default TCP port 3306.
- **Docs:** https://dev.mysql.com/doc/dev/mysql-server/8.4.9/PAGE_PROTOCOL.html — COM_QUERY: https://dev.mysql.com/doc/dev/mysql-server/8.4.9/page_protocol_com_query.html — prepared: https://dev.mysql.com/doc/refman/8.4/en/sql-prepared-statements.html
- **Auth:** `userPass` — secret binding: `password`

## Scope

Talks to a customer-owned MySQL-compatible server over the official client/server protocol (not JDK HTTP: binary framing, capability handshake, TLS). The customer owns the database.

This is the **canonical** node for MySQL wire. Do **not** build MariaDB or PlanetScale Vitess as separate nodes. Select `engine`:

- `engine=mysql` (default): Oracle MySQL 8.4 / 9.x.
- `engine=mariadb`: MariaDB client/server protocol (MySQL-compatible with documented extensions) ([MariaDB protocol](https://mariadb.com/kb/en/clientserver-protocol/)).
- `engine=planetscale`: PlanetScale **Vitess/MySQL** over MySQL protocol ([PlanetScale MySQL compatibility](https://planetscale.com/docs/vitess/troubleshooting/mysql-compatibility)). PlanetScale **Postgres** uses [`postgresql.md`](postgresql.md), not this node.

**Do not implement:** Unix-domain sockets (`unix_socket` — host FS). PlanetScale HTTP/serverless driver — the daemon has TCP. `procedure.call` when `engine=planetscale` (PlanetScale documents no stored routines).

Query parameters are **required** for any value that is not a validated identifier: COM_STMT_PREPARE / COM_STMT_EXECUTE (or equivalent Connector bind). Never string-build SQL.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.query` | TCP MySQL protocol | `COM_STMT_PREPARE` + `COM_STMT_EXECUTE` | Parameterized SQL (one statement) |
| `row.select` | TCP | prepared `SELECT` | Read rows |
| `row.insert` | TCP | prepared `INSERT` | Insert rows |
| `row.update` | TCP | prepared `UPDATE` | Update rows |
| `row.upsert` | TCP | prepared `INSERT … ON DUPLICATE KEY UPDATE` | Insert-or-update |
| `row.delete` | TCP | prepared `DELETE` | Delete rows |
| `procedure.call` | TCP | prepared `CALL proc(?,…)` | Stored procedure. **Disabled** when `engine=planetscale` |
| `trigger.listen` | TCP | long-poll prepared `SELECT` (see notes) | Wait for operator-owned change rows. MySQL has no `LISTEN`/`NOTIFY` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.query` | no | Chooses the row above |
| `engine` | select | no | `mysql` | no | `mysql` \| `mariadb` \| `planetscale` |
| `host` | string | yes | | no | TCP hostname (PlanetScale: `….connect.psdb.cloud` or documented MySQL host) |
| `port` | number | no | `3306` | no | TCP port. Reject socket paths |
| `database` | string | yes | | no | Schema/database name |
| `user` | string | yes | | no | Username |
| `password` | password | yes | | yes | Password. Never log |
| `ssl` | boolean | no | `true` | no | Require TLS. PlanetScale requires SSL |
| `charset` | string | no | `utf8mb4` | no | Connection character set |
| `connectTimeoutMs` | number | no | `10000` | no | TCP connect timeout |
| `queryTimeoutMs` | number | no | `30000` | no | Statement timeout |
| `listenTimeoutMs` | number | no | `60000` | no | Max wait for `trigger.listen` |
| `listenPollMs` | number | no | `2000` | no | Poll interval for `trigger.listen` |
| `maxRows` | number | no | `10000` | no | Result row cap |
| `maxBytes` | number | no | `1048576` | no | Result size cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never include secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | One statement with `?` placeholders |
| `params` | array | yes | Bind values (use `[]` only when SQL has zero placeholders) |

Reject `sql` that concatenates quotes/values. If placeholder count ≠ `params.length`, `{ error }`.

### `operation = row.select`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Validated identifier |
| `columns` | array of string | no | Validated identifiers |
| `where` | object | no | Column → value (AND, bound) |
| `orderBy` | string | no | Validated column |
| `orderDir` | select | no | `asc` \| `desc` |
| `limit` | number | no | Capped by `maxRows` |
| `offset` | number | no | Skip |

### `operation = row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `rows` | array of object | yes | Rows (max 1000) |

### `operation = row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `set` | object | yes | Assignments (bound) |
| `where` | object | yes | Filter. Reject empty |

### `operation = row.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `rows` | array of object | yes | Rows |
| `updateColumns` | array of string | no | Columns in `ON DUPLICATE KEY UPDATE`; default all non-PK columns present in the row |

### `operation = row.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `where` | object | yes | Filter. Reject empty |

### `operation = procedure.call`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `procedure` | string | yes | Validated routine name |
| `params` | array | no | `IN` bind values |

When `engine=planetscale`: do not send `CALL`. Return `{ error: "procedure.call is not available for engine=planetscale (Vitess has no stored routines)" }`.

MariaDB/MySQL: use binary protocol prepared `CALL`. Map `OUT`/`INOUT` only if the driver exposes them; otherwise document IN-only in v1.

### `operation = trigger.listen`

MySQL/MariaDB/Vitess have **no** `LISTEN`/`NOTIFY`. Do not use replication `COM_BINLOG_DUMP` (not an application API).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Operator-owned table to poll |
| `where` | object | yes | Parameterized filter for “new” rows |
| `orderBy` | string | no | Typically a monotonic `id` or timestamp column |

Poll with prepared `SELECT` until `rows.length > 0` or `listenTimeoutMs`. Then return those rows. This is not a server push.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `columns` | array of string | SELECT / listen | Names |
| `rows` | array | SELECT / listen / CALL resultsets | JSON-safe rows |
| `truncated` | boolean | SELECT | Cap hit |
| `rowCount` | number | DML | Affected rows |
| `insertId` | number/string | INSERT | `LAST_INSERT_ID` when present (string if BIGINT) |
| `error` | string | failure | No secrets |

## Legal

- MySQL documentation and licenses: https://www.mysql.com/about/legal/ — commercial MySQL is Oracle-licensed; Community Edition is GPL. The operator’s server license is theirs.
- MariaDB Server (when `engine=mariadb`): GPL; https://mariadb.com/legal/
- PlanetScale Terms (when `engine=planetscale`): https://planetscale.com/legal/terms
- Rate limits: protocol has none; cloud (PlanetScale) enforces connection and query limits. Map vendor errors to `{ error }`.
- PII: customer-owned rows. Do not log SQL bind values that may contain PII. Do not log passwords.
- Official-API-first: MySQL/MariaDB/Vitess wire only. No HTML scrape. No PlanetScale HTTP serverless driver.

## Implementation notes

- Not JDK HTTP. Reason: MySQL client/server protocol is binary (handshake, auth plugins, COM_STMT_*).
- TLS: enable when `ssl=true`. Do not pass `sslrootcert` as a host path; optional PEM string only if needed (`sslCaPem` secret) — otherwise JVM trust store. PlanetScale: `ssl=true` required.
- Timeouts: connect + query as configured. Prepared statements: close (`COM_STMT_CLOSE`) after each invocation unless a pool caches by connection.
- Size caps: `maxRows` / `maxBytes`.
- Retry: connect failures only (max 2, jitter). Never retry INSERT/UPDATE that may have committed. Never retry access-denied.
- Auth plugins: `caching_sha2_password` (MySQL 8 default), `mysql_native_password`, MariaDB `ed25519` when `engine=mariadb` if the chosen driver supports it. Do not implement Unix socket peer auth.
- Errors: server ERR packet → `{ error }` on OUTPUT, never throw past dispatch.
