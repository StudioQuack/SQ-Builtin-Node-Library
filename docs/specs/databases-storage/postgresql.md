# PostgreSQL

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/postgresql/`
- **Researched:** 2026-08-20
- **Official API:** PostgreSQL Frontend/Backend Protocol 3.2 (current server PostgreSQL 18; libpq still defaults to protocol 3.0; servers 7.4+ speak 3.0). IANA TCP port 5432.
- **Docs:** https://www.postgresql.org/docs/current/protocol.html
- **Auth:** `userPass` — secret binding: `password` (and any password embedded in `connectionUri`)

## Scope

Talks to a customer-owned PostgreSQL-compatible server over the official wire protocol (not JDK HTTP: the surface is a binary message protocol). The customer owns the database, network allowlist, and data residency. StudioQuack holds only connection secrets.

This is the **canonical** node for Postgres wire. Do **not** build separate nodes for Neon, CockroachDB, TimescaleDB, pgvector, or PlanetScale Postgres. Select `engine` instead.

- `engine=postgres` (default): community/self-hosted/cloud Postgres.
- `engine=neon`: same wire; prefer pooler hostname (`-pooler` suffix) except `trigger.listen` / session features, which need the **direct** hostname ([Neon connection pooling](https://neon.com/docs/connect/connection-pooling)).
- `engine=cockroach`: pgwire **3.0** ([CockroachDB PostgreSQL compatibility](https://www.cockroachlabs.com/docs/stable/postgresql-compatibility)); default port 26257; cloud TLS `sslMode=verify-full`.
- `engine=timescale`: same wire; hypertables are ordinary SQL (`CREATE TABLE … WITH (timescaledb.hypertable)` / `create_hypertable()`), not a second protocol ([Timescale hypertables](https://docs.timescale.com/use-timescale/latest/hypertables/create/)).

**Do not implement:** host filesystem paths (`sslrootcert` file, Unix-domain socket path, `COPY … FROM '/path'`). Custom CA is an optional PEM **string secret** (`sslRootCertPem`), or skip custom CA and use the JVM/OS trust store. Neon Cloud API (project CRUD) is a different auth surface — out of scope.

`vector.*` operations emit pgvector SQL on this connection ([pgvector](https://github.com/pgvector/pgvector)). The extension must already be installed on the customer database (`CREATE EXTENSION vector`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.query` | TCP pgwire | extended query: Parse → Bind → Execute → Sync | Arbitrary parameterized SQL (one statement) |
| `row.select` | TCP pgwire | `SELECT … FROM {table} WHERE …` prepared | Read rows from a named table |
| `row.insert` | TCP pgwire | `INSERT INTO {table} (…) VALUES (…)` prepared | Insert one or more rows |
| `row.update` | TCP pgwire | `UPDATE {table} SET … WHERE …` prepared | Update matching rows |
| `row.upsert` | TCP pgwire | `INSERT … ON CONFLICT (…) DO UPDATE` prepared | Insert-or-update by unique key |
| `row.delete` | TCP pgwire | `DELETE FROM {table} WHERE …` prepared | Delete matching rows |
| `trigger.listen` | TCP pgwire | SQL `LISTEN {channel}`; wait for `NotificationResponse` | Wait for `NOTIFY` on a channel (session connection; not a pooler) |
| `vector.upsert` | TCP pgwire | `INSERT … ON CONFLICT DO UPDATE` on vector table | Upsert embedding rows (pgvector) |
| `vector.query` | TCP pgwire | `SELECT … ORDER BY col {op} $1::vector LIMIT n` | Nearest-neighbor search |
| `vector.delete` | TCP pgwire | `DELETE FROM {table} WHERE …` prepared | Delete embedding rows |

Retired / unofficial verbs are omitted.

**Prepared statements are required** for every SQL operation (extended query protocol). Never concatenate user values into SQL text.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.query` | no | Chooses the row above |
| `engine` | select | no | `postgres` | no | `postgres` \| `neon` \| `cockroach` \| `timescale` |
| `host` | string | yes* | | no | TCP hostname. *Omit only when `connectionUri` is set. Neon: `ep-…-pooler.…` vs direct `ep-…`. Cockroach Cloud: cluster routing host. |
| `port` | number | no | `5432` (`26257` when `engine=cockroach` and port omitted) | no | TCP port. Not a Unix socket. |
| `database` | string | yes* | | no | Database name. *Omit only when present in `connectionUri`. |
| `user` | string | yes* | | no | Role name. |
| `password` | password | yes* | | yes | Role password (or operator-supplied IAM/token used *as* the Postgres password). Never log. |
| `connectionUri` | password | no | | yes | `postgresql://` URI. If set, fills host/port/database/user/password unless discrete fields override. Redact password in logs/errors. |
| `sslMode` | select | no | `require` (`verify-full` recommended when `engine=cockroach` or Neon) | no | libpq `sslmode`: `disable` \| `allow` \| `prefer` \| `require` \| `verify-ca` \| `verify-full` ([SSL Support](https://www.postgresql.org/docs/current/libpq-ssl.html)). |
| `sslRootCertPem` | password | no | | yes | Optional CA certificate PEM **string**. Do **not** accept a filesystem path. Empty = JVM/OS trust store. |
| `schema` | string | no | `public` | no | Default `search_path` schema for table-oriented ops. Send as a bind parameter in generated SQL identifiers via quoting, not string concat of untrusted names. |
| `connectTimeoutMs` | number | no | `10000` | no | TCP connect timeout |
| `queryTimeoutMs` | number | no | `30000` | no | Statement timeout. `trigger.listen` uses `listenTimeoutMs` instead. |
| `listenTimeoutMs` | number | no | `60000` | no | Max wait for `trigger.listen` |
| `maxRows` | number | no | `10000` | no | Cap rows returned on SELECT-like ops |
| `maxBytes` | number | no | `1048576` | no | Cap serialized result payload (1 MiB) |

Identifier fields (`table`, `schema`, `channel`, column names) must match `[A-Za-z_][A-Za-z0-9_]*` or be passed as quoted identifiers after validation. Reject anything else.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets on edges |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include secrets, URIs with passwords, or PEM |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | Single SQL statement with `$1`, `$2`, … placeholders only |
| `params` | array | no | Bind values in order. Default `[]`. Reject if SQL has no placeholders but this is used to smuggle fragments — still bind, never interpolate |

### `operation = row.select`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name (validated identifier) |
| `columns` | array of string | no | Column list; default `*` of validated identifiers only (if omitted, select all) |
| `where` | object | no | Map of column → value, combined with AND, each as `$n`. Empty = all rows (still capped by `maxRows`) |
| `orderBy` | string | no | Validated column name |
| `orderDir` | select | no | `asc` \| `desc` (default `asc`) |
| `limit` | number | no | Row cap; min(`limit`, `maxRows`) |
| `offset` | number | no | Skip rows |

### `operation = row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `rows` | array of object | yes | One or more row objects (same keys). Bound as parameters. Max 1000 rows per call |

### `operation = row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `set` | object | yes | Column → value to assign (bound) |
| `where` | object | yes | Column → value filter (AND). Reject empty `where` (no unconstrained UPDATE) |

### `operation = row.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `rows` | array of object | yes | Rows to insert |
| `conflictColumns` | array of string | yes | Unique key columns for `ON CONFLICT` |
| `updateColumns` | array of string | no | Columns to update on conflict; default = all non-conflict columns |

Cockroach: `ON CONFLICT` is supported; some Postgres-only features differ — see Cockroach compatibility page. Do not invent Cockroach HTTP APIs.

### `operation = row.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `where` | object | yes | Column → value filter. Reject empty `where` |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | yes | `LISTEN` channel name (validated identifier) |

Uses a **dedicated session** connection. When `engine=neon`, connect to the **direct** hostname (no `-pooler`). PgBouncer transaction pooling does not support `LISTEN`/`NOTIFY`. CockroachDB does **not** support LISTEN/NOTIFY (unsupported “Events”) — return `{ error }` with that fact, no retry.

### `operation = vector.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table with a vector column |
| `vectorColumn` | string | no | Default `embedding` |
| `idColumn` | string | no | Default `id` |
| `rows` | array of object | yes | Each row: `{ id, embedding: number[], …metadata }` |

SQL (illustrative, always prepared): `INSERT INTO t (id, embedding) VALUES ($1, $2::vector) ON CONFLICT (id) DO UPDATE SET embedding = EXCLUDED.embedding`.

### `operation = vector.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `vectorColumn` | string | no | Default `embedding` |
| `embedding` | array of number | yes | Query vector |
| `metric` | select | no | `l2` (`<->`) \| `ip` (`<#>`) \| `cosine` (`<=>`) \| `l1` (`<+>`). Default `cosine` |
| `limit` | number | no | Default 5, max `maxRows` |
| `probes` | number | no | Optional `SET LOCAL ivfflat.probes` — session; skip on Neon pooler |

### `operation = vector.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table name |
| `where` | object | yes | Filter (typically `{ id: … }`). Reject empty |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `columns` | array of string | SELECT-like | Column names |
| `rows` | array of array/object | SELECT-like | Result rows (JSON-safe). Truncated if over `maxRows`/`maxBytes` |
| `truncated` | boolean | SELECT-like | True if cap hit |
| `rowCount` | number | DML | Rows affected (`CommandComplete` tag) |
| `channel` | string | `trigger.listen` | Channel that fired |
| `payload` | string | `trigger.listen` | `NOTIFY` payload (may be empty) |
| `pid` | number | `trigger.listen` | Backend PID from notification (not a secret) |
| `distances` | array of number | `vector.query` | Distance per row when requested |
| `error` | string | failure | Actionable message. Never secrets, PEM, or connection URI |

## Legal

- PostgreSQL License: https://www.postgresql.org/about/licence/
- Neon ToS (when `engine=neon`): https://neon.tech/terms-of-service — customer’s Neon project; StudioQuack is not the processor of Neon Cloud Admin API.
- Cockroach Labs Cloud terms (when `engine=cockroach`): https://www.cockroachlabs.com/cloud-terms-and-conditions/
- Timescale / TigerData terms (when `engine=timescale`): https://www.timescale.com/legal/terms
- pgvector: PostgreSQL License (extension on the customer’s server): https://github.com/pgvector/pgvector
- Rate limits: none at the protocol layer. Cloud vendors apply their own connection and compute quotas (Neon `max_connections` / pooler `max_client_conn=10000`; Cockroach SQL connection limits; Timescale compute). Honor vendor 429/connection-refused as `{ error }` without retry storms.
- PII / residency: row payloads are customer data. The daemon egresses to the configured host only. Do not log row bodies. HIPAA/GDPR lawful basis is the operator’s.
- Official-API-first: wire protocol and SQL only. No scrape of vendor consoles. No unofficial HTTP wrappers.

## Implementation notes

- JDK HTTP is not used. Reason: PostgreSQL Frontend/Backend Protocol is a binary TCP protocol (current docs: 3.2 on PostgreSQL 18; speak 3.0 with optional negotiation to 3.2). Prefer parameterized extended query (Parse/Bind/Execute/Sync). Do not use simple Query for user SQL.
- Timeouts: connect `connectTimeoutMs`; statement `queryTimeoutMs` (set `statement_timeout` on the session after connect, as a literal integer from config — not from input). `trigger.listen` waits up to `listenTimeoutMs` then `{ error: "listen timed out" }`.
- Size caps: `maxRows` and `maxBytes`. Oversize → success with `truncated: true` or `{ error }` if a single row exceeds `maxBytes`.
- Retry: retry only TCP connect failures and `08xxx` / serialization failure `40001` with bounded exponential backoff + jitter (max 2 retries). Never retry DML that may have committed. Never retry auth failures (`28xxx`).
- SSL: do not read `~/.postgresql/root.crt` or any host path. Trust store = JVM default and/or `sslRootCertPem`.
- No Unix-domain sockets.
- Errors: map server `ErrorResponse` to `{ error }` on OUTPUT. Never throw past dispatch. Never put `password` or URI userinfo in the message.
- Connection: short-lived per invocation unless the runtime already pools by host/user/db **without** sharing across workflows. `trigger.listen` must not return the connection to a transaction-mode pool.
- Neon autosuspend: first query after idle may be slow; do not treat that as fatal until `queryTimeoutMs`.
