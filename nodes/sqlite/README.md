# SQLite

Run SQL on a SQLite database file. Handler: `SqlDatabaseNode` (shared with the other SQL nodes) using the SQLite JDBC driver (sqlite-jdbc). Failures raise a BPMN error (`INVALID_INPUT`, `REQUEST_FAILED`) with `sql_state` and `vendor_code` in the error data.

## How the database file flows

The node runs in the sandbox and never reads host paths, so the database travels as a **file value**:

1. Wire a file into `database` (from Files, an HTTP download, a storage node, …) or leave it blank to start a new, empty database.
2. The node opens a private temporary copy (max 8 MiB) and deletes it after the call.
3. `execute` and `insert` return the updated database on output `file` (`application/vnd.sqlite3`). Wire it to a storage node to keep the change. `query` never changes the database and returns no file.

## Operations

| operation | What it does | Output |
|---|---|---|
| `query` | Runs one SELECT with `?` parameters (writes are refused via `PRAGMA query_only`) | `rows`, `columns`, `row_count`, `truncated` |
| `execute` | Runs INSERT / UPDATE / DELETE / DDL | `affected_rows`, `file`, plus `rows` when the statement returns a result set |
| `insert` | Batch-inserts `rows` into `table` in one transaction | `affected_rows`, `columns`, `file` |

Values are always bound as `?` parameters. Put flow data in `parameters` (or `rows`), never into the SQL text. Identifiers for `insert` are validated against `[A-Za-z_][A-Za-z0-9_$#]*` and quoted, for example `"orders"`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | query | no | What to run. EXACT: query (SELECT, returns rows) / execute (INSERT/UPDATE/DELETE/DDL or a call; returns affected_rows, plus rows when the statement returns a result set) / inser... |
| `database` | file | no |  | no | SQLite database file (path, ContentBlock or $fileRef from an upstream node), max 8 MiB. Input database overrides this. Blank starts a new empty database. execute and insert retu... |
| `query` | longtext | no |  | no | SQL statement for query and execute. Purpose: one statement; use ? placeholders for values and put them in parameters — never concatenate input into SQL. Example: SELECT id, tot... |
| `parameters` | list | no |  | no | Positional values for the ? placeholders, in order. Accepted: JSON array of strings, numbers, booleans or null; objects/arrays are sent as JSON text. Example: [42, 100.5]. |
| `max_rows` | number | no | 1000 | no | Row cap for returned result sets. Purpose: bound memory and output size. Default 1000, max 10000. truncated=true when more rows existed. |
| `table` | string | no |  | no | Target table for insert: table, schema.table or database.schema.table. Accepted: identifiers [A-Za-z_][A-Za-z0-9_$#]*; each part is quoted. Example: 'sales.orders'. |
| `rows` | list | no |  | no | Rows for insert. Accepted: JSON array of objects (column → value), up to 10000. Columns are the union of all keys; a missing key inserts NULL. All rows commit together or roll b... |
| `timeout_seconds` | number | no | 30 | no | Statement timeout (and lock wait for SQLite). Default 30, max 300. |

## Limits and safety

- `ATTACH DATABASE` and extension loading are disabled, so SQL cannot open other files in the container.
- The database is capped at 8 MiB (`PRAGMA max_page_count`); a write that would exceed it fails with `REQUEST_FAILED`.
- A file that is not a SQLite database fails with `INVALID_INPUT`.
- Result sets are capped by `max_rows` (default 1000, max 10000); BLOB values are base64 and capped at 1 MiB.

## Legal

SQLite is in the public domain; the node uses sqlite-jdbc (Apache-2.0). The database file is customer data supplied by the workflow; StudioQuack stores no copy. The customer is the controller of the data in the file (may include PHI).
