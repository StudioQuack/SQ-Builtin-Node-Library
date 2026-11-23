# Snowflake

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/snowflake/`
- **Researched:** 2026-08-20
- **Official API:** Snowflake SQL API **v2** — `POST /api/v2/statements`
- **Docs:** https://docs.snowflake.com/en/developer-guide/sql-api · https://docs.snowflake.com/en/developer-guide/sql-api/reference · https://docs.snowflake.com/en/developer-guide/sql-api/authenticating
- **Auth:** keyPair (JWT) / oauth / programmatic access token — secret binding: `private_key_pem` or `oauth_token` or `pat`. **SQL API does not use account passwords.**

## Scope

One node for **customer-owned** Snowflake accounts. Submit SQL via the official REST SQL API (`/api/v2/statements/`), poll `GET /api/v2/statements/{statementHandle}` if async, cancel with `POST …/cancel`.

`row.insert` / `row.update` are SQL DML submitted as statements (not a separate load API). Bindings use SQL API bind fields — never string-built SQL.

Credit consumption is billed to the customer’s warehouse.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.query` | POST | `/api/v2/statements` | SELECT / DDL / general SQL |
| `row.insert` | POST | `/api/v2/statements` | `INSERT INTO … VALUES` with binds |
| `row.update` | POST | `/api/v2/statements` | `UPDATE … WHERE` with binds |
| `statement.get` | GET | `/api/v2/statements/{handle}` | Poll async result |
| `statement.cancel` | POST | `/api/v2/statements/{handle}/cancel` | Cancel |

Host: `https://{account_identifier}.snowflakecomputing.com`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.query` | no | Chooses the row above |
| `auth` | select | yes | `keyPair` | no | `keyPair` \| `oauth` \| `pat` |
| `account` | string | yes | empty | no | Account identifier (no region in locator form as JWT docs require) |
| `user` | string | yes | empty | no | Snowflake user (uppercase in JWT `iss`/`sub`) |
| `warehouse` | string | no | empty | no | Warehouse (`nullable` in statement body) |
| `database` | string | no | empty | no | Session database |
| `schema` | string | no | empty | no | Session schema |
| `role` | string | no | empty | no | Role |
| `private_key_pem` | password | if keyPair | empty | yes | PKCS8 PEM. Not a host path. Optional passphrase: `private_key_passphrase` |
| `private_key_passphrase` | password | no | empty | yes | Encrypted key passphrase |
| `oauth_token` | password | if oauth | empty | yes | Bearer OAuth access token |
| `pat` | password | if pat | empty | yes | Programmatic access token |
| `timeout_seconds` | number | no | `45` | no | Statement `timeout` field + HTTP timeout |
| `row_limit` | number | no | `10000` | no | Truncate result partitions locally |

Headers: `Authorization: Bearer …`. Optional `X-Snowflake-Authorization-Token-Type`: `KEYPAIR_JWT` \| `OAUTH` \| `PROGRAMMATIC_ACCESS_TOKEN`.

**Unverified (2026-08-20) as a node auth:** workload identity federation (`WORKLOAD_IDENTITY_FEDERATION`) — documented on the SQL API auth page but not in the catalog; omit until requested.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No JWT/PEM |

FUNCTION ports: none.

## Input fields (by operation)

### `execute.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | Statement. Prefer binds |
| `bindings` | object | no | SQL API bindings map |
| `async` | boolean | no | If true, return handle without waiting |

### `row.insert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Qualified or schema-qualified name (identifiers quoted by handler) |
| `columns` | array | yes | Column names |
| `rows` | array | yes | Array of value arrays (max 500 rows / invoke) |

### `row.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Table |
| `set` | object | yes | Column → value (bound) |
| `where_sql` | string | yes | Predicate with `?` / bind names |
| `bindings` | object | yes | WHERE (and SET) binds |

Empty WHERE is an error.

### `statement.get` | `statement.cancel`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `statement_handle` | string | yes | Handle from async submit |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or `pending` |
| `statement_handle` | string | async / 202 | Handle to poll |
| `columns` | array | result | Names from resultSetMetaData |
| `rows` | array | result | Data rows (capped) |
| `truncated` | boolean | result | Local cap |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 | Backoff |

## Legal

- https://www.snowflake.com/legal/
- SQL API limits: https://docs.snowflake.com/en/developer-guide/sql-api (see limitations: some PUT/GET/call types unsupported)
- Compute credits billed to the customer warehouse
- Result sets may contain PII; customer is controller
- Official SQL API only (not unofficial JDBC wrappers as the HTTP surface)

## Implementation notes

- JDK HTTP is correct (REST). Generate JWT in-process from PEM secret; never write PEM to disk.
- Poll 202 responses until 200 or timeout. Cap poll interval with jitter.
- `requestId` UUID + `retry=true` only when retrying the **same** submit (Snowflake docs).
- Size: statement text cap per API; local row cap 10_000.
- 429 → retry-after. 5xx retry once. 4xx no retry.
- Errors → `{ error }` on OUTPUT. Never log tokens or PEM.
