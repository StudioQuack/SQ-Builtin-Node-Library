# MySQL

Run SQL against a customer-owned MySQL database. Handler: `SqlDatabaseNode` (shared with the other SQL nodes) using the MariaDB Connector/J (works with MySQL 5.7+, 8.x and MariaDB). Password is a secret; it is never logged or returned and is redacted from errors. Failures raise a BPMN error (`INVALID_INPUT`, `AUTH_FAILED`, `REQUEST_FAILED`) with `sql_state` and `vendor_code` in the error data.

## Operations

| operation | What it does | Output |
|---|---|---|
| `query` | Runs one SELECT with `?` parameters | `rows`, `columns`, `row_count`, `truncated` |
| `execute` | Runs INSERT / UPDATE / DELETE / DDL / a procedure call | `affected_rows`, plus `rows` when the statement returns a result set |
| `insert` | Batch-inserts `rows` into `table` in one transaction | `affected_rows`, `columns` |

Values are always bound as `?` parameters. Put flow data in `parameters` (or `rows`), never into the SQL text. Identifiers for `insert` are validated against `[A-Za-z_][A-Za-z0-9_$#]*` and quoted, for example ``orders``.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | query | no | What to run. EXACT: query (SELECT, returns rows) / execute (INSERT/UPDATE/DELETE/DDL or a call; returns affected_rows, plus rows when the statement returns a result set) / inser... |
| `host` | string | yes |  | no | Database server hostname or IP. Purpose: TCP target. Accepted: hostname, IPv4 or [IPv6]; no scheme, port, path or ?options. Example: 'db.internal.example.com'. |
| `port` | number | no | 3306 | no | TCP port. Default 3306. Accepted: 1–65535. |
| `database` | string | no |  | no | Default schema (database) for the session. Purpose: unqualified table names resolve here. Accepted: letters, digits, _ $ # . - (max 128); blank = none. Example: 'shop'. |
| `user` | string | yes |  | no | Database login name. Purpose: authenticates the session. Use a least-privilege account. |
| `password` | password | yes |  | yes | Database password. Stored as a secret; never logged or returned. Redacted from error messages. |
| `ssl_mode` | select | no | require | no | Transport encryption. EXACT: disable (plain TCP — loopback or private network only) / require (TLS, server certificate not verified) / verify-full (TLS, certificate chain verifi... |
| `allow_public_key_retrieval` | boolean | no | false | no | MySQL 8 caching_sha2_password without TLS needs the server RSA key. true lets the driver fetch it (open to a man-in-the-middle on untrusted networks). Prefer ssl_mode require or... |
| `query` | longtext | no |  | no | SQL statement for query and execute. Purpose: one statement; use ? placeholders for values and put them in parameters — never concatenate input into SQL. Example: SELECT id, tot... |
| `parameters` | list | no |  | no | Positional values for the ? placeholders, in order. Accepted: JSON array of strings, numbers, booleans or null; objects/arrays are sent as JSON text. Example: [42, 100.5]. |
| `max_rows` | number | no | 1000 | no | Row cap for returned result sets. Purpose: bound memory and output size. Default 1000, max 10000. truncated=true when more rows existed. |
| `table` | string | no |  | no | Target table for insert: table, schema.table or database.schema.table. Accepted: identifiers [A-Za-z_][A-Za-z0-9_$#]*; each part is quoted. Example: 'sales.orders'. |
| `rows` | list | no |  | no | Rows for insert. Accepted: JSON array of objects (column → value), up to 10000. Columns are the union of all keys; a missing key inserts NULL. All rows commit together or roll b... |
| `timeout_seconds` | number | no | 30 | no | Connect, login and statement timeout. Default 30, max 300. |

## Connection notes

- The node builds the JDBC URL itself from `host`, `port` and `database`; driver options cannot be passed through these fields.
- `ssl_mode`: `disable` (plain TCP), `require` (TLS without certificate verification), `verify-full` (TLS, chain checked against the JVM trust store, hostname verified). Use `disable` only on loopback or a private network.
- **MySQL 8 without TLS:** `caching_sha2_password` needs `allow_public_key_retrieval=true`, or use `ssl_mode` `require`/`verify-full` instead.
- The same node works with MariaDB servers.
- Result sets are capped by `max_rows` (default 1000, max 10000); binary columns are base64 and LOBs are capped at 1 MiB.

## Legal

Customer-owned MySQL or MariaDB. License and terms of the server you run. StudioQuack does not host MySQL. The node uses MariaDB Connector/J (LGPL-2.1). The customer is the controller of the data the workflow reads or writes (may include PHI). Use a least-privilege database account.
