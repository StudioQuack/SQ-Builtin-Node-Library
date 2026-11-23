# Cassandra

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/cassandra/`
- **Researched:** 2026-08-20
- **Official API:** Apache Cassandra **native protocol v4** (CQL binary). Spec: https://cassandra.apache.org/doc/latest/cassandra/_attachments/native_protocol_v4.html · source: https://github.com/apache/cassandra/blob/trunk/doc/native_protocol_v4.spec
- **Docs:** Protocol v4 HTML above. CQL: https://cassandra.apache.org/doc/latest/cassandra/cql/
- **Auth:** `userPass` — secret binding: `password`

## Scope

Executes CQL on the **customer’s** Apache Cassandra or ScyllaDB cluster. One node; `engine=cassandra|scylla`. Default native port **9042**.

This library **locks protocol version 4** (Cassandra 2.2+ / Scylla compatible). Do not speak v5 unless a later spec explicitly upgrades. v4 includes unset bind values, warnings, and custom payloads.

**Prepared statements are required** for every operation. The handler `PREPARE`s the CQL once, then `EXECUTE`s with bound values. Never concatenate user values into CQL strings.

`engine=scylla`: same CQL v4. Shard-aware port **19042** is optional (see [`scylladb.md`](scylladb.md)) — not a new API.

Customer owns the cluster. No host CA file paths (PEM secret only).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.cql` | — | `PREPARE` + `EXECUTE` (opcode `0x09` / `0x0A`) | Parameterized CQL (any statement type the role allows) |
| `row.select` | — | prepared `SELECT … WHERE …` | Read rows |
| `row.insert` | — | prepared `INSERT INTO … VALUES …` | Insert |
| `row.update` | — | prepared `UPDATE … SET … WHERE …` | Update |
| `row.delete` | — | prepared `DELETE … WHERE …` | Delete |

Unprepared `QUERY` (opcode `0x07`) is **not** used for these ops.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `row.select` | no | Chooses the row above |
| `engine` | select | yes | `cassandra` | no | `cassandra` \| `scylla` |
| `auth` | select | yes | `userPass` | no | PasswordAuthenticator / `userPass` |
| `contactPoints` | string | yes | | no | Comma-separated hosts (no URI userinfo) |
| `port` | number | no | `9042` | no | Native transport |
| `shardAwarePort` | number | no | `19042` | no | Scylla advanced shard-aware listen; ignored unless `engine=scylla` |
| `datacenter` | string | yes | | no | Local DC for load-balancing (`USE` / token-aware local DC) |
| `keyspace` | string | no | | no | Default keyspace (`STARTUP` / `USE`) |
| `user` | string | yes | | no | |
| `password` | password | yes | | yes | |
| `ssl` | boolean | no | `false` | no | Client-to-node TLS |
| `tlsCaPem` | password | no | | yes | CA **PEM string**, never a path |
| `consistency` | select | no | `LOCAL_QUORUM` | no | v4 consistency: `ONE` \| `QUORUM` \| `LOCAL_QUORUM` \| `ALL` \| `LOCAL_ONE` |
| `connectTimeoutMs` | number | no | `10000` | no | |
| `queryTimeoutMs` | number | no | `30000` | no | |
| `maxRows` | number | no | `1000` | no | Page size / cap |
| `maxFrameBytes` | number | no | `8388608` | no | 8 MiB frame cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Bound values only — no passwords |
| `output` | OUTPUT | `{ rows, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.cql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cql` | string | yes | Statement with `?` bind markers only |
| `values` | array | no | Positional binds matching `?` |
| `keyspace` | string | no | Override |

Reject `cql` that contains no `?` **and** interpolates INPUT — if there are no markers, `values` must be empty and the statement must be a constant operator-authored string from **config** is not available; require markers whenever `values` is non-empty. If `values` is empty, still PREPARE the constant CQL (idempotent DDL/DML the operator typed on INPUT — treat as trusted workflow author, still PREPARE).

### `operation = row.select`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyspace` | string | no | |
| `table` | string | yes | |
| `columns` | array | no | Default `*` |
| `where` | string | no | `WHERE` with `?` only, e.g. `id = ?` |
| `values` | array | if `where` | Binds |
| `limit` | number | no | Default 100, max `maxRows` |
| `allowFiltering` | boolean | no | Default `false`; if `true`, append `ALLOW FILTERING` (expensive) |

### `operation = row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyspace` | string | no | |
| `table` | string | yes | |
| `row` | object | yes | Column → value; keys become columns, values are binds |
| `ifNotExists` | boolean | no | `IF NOT EXISTS` |
| `ttlSeconds` | number | no | `USING TTL ?` |

### `operation = row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyspace` | string | no | |
| `table` | string | yes | |
| `set` | object | yes | Assignments as binds |
| `where` | string | yes | `?` markers |
| `values` | array | yes | Binds for `WHERE` (after SET binds) |
| `ifExists` | boolean | no | |

### `operation = row.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyspace` | string | no | |
| `table` | string | yes | |
| `where` | string | yes | Must include partition key equality with `?` |
| `values` | array | yes | |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `rows` | array | SELECT / execute that returns rows | Column maps (capped) |
| `warnings` | array | when protocol warnings present | Server warnings (v4) |
| `applied` | boolean | LWT | `[applied]` |
| `pagingState` | string | more pages | Hex paging state; do not fetch extra pages beyond `maxRows` |
| `error` | object | failure | `{ message, code }` — no password |

## Legal

- Apache Cassandra: Apache License 2.0. https://cassandra.apache.org
- ScyllaDB: operator’s Scylla license / cloud terms.
- Quotas: cluster capacity is the operator’s.
- Rows may contain PII. Customer owns residency. Logs: opcode, keyspace, table, row counts — never bind values or passwords.
- Official native protocol only. No Thrift (removed). No unofficial HTTP CQL gateways.

## Implementation notes

- **Not JDK HTTP.** Native protocol is length-prefixed binary frames (version nibble `0x4`, stream id, opcode). STARTUP (`CQL_VERSION=3.0.0`), then `AUTHENTICATE` / `AUTH_RESPONSE` for PasswordAuthenticator, then `REGISTER` optional. OPTIONS/SUPPORTED as needed.
- Prepared cache: keyed by CQL string + keyspace; re-PREPARE on `unprepared` (0x2500) errors.
- Timeouts: connect 10s, query 30s, hard 60s. Page with protocol paging; stop at `maxRows`.
- Size: `maxFrameBytes` 8 MiB; reject larger.
- Retries: retry **idempotent** SELECT once on `Overloaded` / connection drop. No retry on INSERT/UPDATE/DELETE after unknown outcome. Respect `unprepared` by re-prepare + one execute.
- SSL: in-memory trust from `tlsCaPem`. No `sslrootcert` path.
- Scylla: if `engine=scylla` and `shardAwarePort` reachable, drivers **may** use shard-aware connections; if not, `9042` is correct and sufficient.
- Errors: protocol error codes → `{ error }`. Never log `AUTH_RESPONSE` body.
