# Oracle

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/oracle/`
- **Researched:** 2026-08-20
- **Official API:** Oracle Database Net / thin JDBC (SQL*Net). Wallet for Autonomous DB (ATP/ADW).
- **Docs:** https://docs.oracle.com/en/database/oracle/oracle-database/ · https://www.oracle.com/database/technologies/appdev/jdbc.html
- **Auth:** userPass / wallet — secret binding: `password`; wallet = platform `file` ref-id (`wallet_zip`)

## Scope

One node for **customer-owned** Oracle Database. Parameterized SQL via the official thin driver. Autonomous DB uses a wallet archive: type **`file`** (StudioQuack ref-id), **never** a host filesystem path. `thickMode` (Instant Client) is optional and still must not mount host paths — Instant Client would have to ship in the node image if used.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.sql` | SQL*Net | prepared statement | Parameterized SQL |
| `row.select` | SQL*Net | prepared SELECT | Read |
| `row.insert` | SQL*Net | prepared INSERT | Insert |
| `row.update` | SQL*Net | prepared UPDATE | Update |
| `row.upsert` | SQL*Net | `MERGE` | Upsert |
| `row.delete` | SQL*Net | prepared DELETE | Delete |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | |
| `auth` | select | no | `userPass` | no | `userPass` \| `wallet` |
| `host` | string | if userPass | empty | no | |
| `port` | number | no | `1521` | no | |
| `service_name` | string | if userPass | empty | no | Service, not SID preferred |
| `user` | string | yes | empty | no | |
| `password` | password | yes | empty | yes | |
| `wallet_zip` | file | if wallet | empty | no | Platform file ref-id (zip). Not a host path |
| `thick_mode` | boolean | no | `false` | no | Instant Client; default thin |
| `timeout_ms` | number | no | `30000` | no | |
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
| `sql` | string | yes | SQL with `:1` / `?` binds |
| `params` | array | no | |

### `row.select`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | Identifier |
| `columns` | array | no | |
| `where` | string | no | Bound predicate |
| `params` | array | no | |
| `limit` | number | no | `FETCH FIRST n ROWS ONLY` |

### `row.insert` | `row.update` | `row.delete` | `row.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `table` | string | yes | |
| `row` / `rows` | object / array | insert | |
| `row` | object | update/upsert | |
| `on` | array | upsert | Match columns for MERGE |
| `where` | string | update/delete | Required bound WHERE |
| `params` | array | update/delete | |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `columns` | array | select | |
| `rows` | array | select | |
| `row_count` | number | DML | |
| `truncated` | boolean | select | |
| `error` | string | failure | No password |

## Legal

- Oracle Technology Network / Database license: https://www.oracle.com/legal/
- Customer-owned DB. Results may be PII
- Official JDBC/Net only

## Implementation notes

- Binary SQL*Net — not JDK HTTP. Oracle JDBC thin.
- Wallet: fetch bytes via platform file ref into tmpfs; never persist; never log.
- TLS as required by the connect descriptor.
- Timeouts 30 s. Row cap 10_000.
- Errors → `{ error }` on OUTPUT.
