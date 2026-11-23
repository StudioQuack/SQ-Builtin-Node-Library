# Databricks

- **Status:** planned
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** `nodes/databricks/`
- **Researched:** 2026-08-20
- **Official API:** SQL Statement Execution API 2.0 + Unity Catalog REST 2.1 (list ops)
- **Docs:** https://docs.databricks.com/api/workspace/statementexecution/executestatement
- **Auth:** `pat` | `oauthM2m` — secret binding: `token` / `clientSecret`

## Scope

Executes SQL on a **Databricks SQL warehouse** (not Spark Jobs, not `/api/2.1/jobs`). Statement API: `POST /api/2.0/sql/statements` on the workspace host. Catalog/schema/table listing uses Unity Catalog: `GET /api/2.1/unity-catalog/catalogs|schemas|tables`. The warehouse must be **running**; the principal needs **CAN USE** on that warehouse plus Unity Catalog privileges (`USE CATALOG`, `USE SCHEMA`, `SELECT` / `MODIFY` as applicable). The customer owns the workspace, warehouse, and data. `httpPath` is the JDBC path (`/sql/1.0/warehouses/{warehouse_id}`); the Statement API field is `warehouse_id` extracted from that path.

Do not download `EXTERNAL_LINKS` result URLs with the Databricks `Authorization` header (official warning). Prefer `INLINE` + `JSON_ARRAY` with a byte cap.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `execute.sql` | POST then GET | `/api/2.0/sql/statements` then `/api/2.0/sql/statements/{statement_id}` | Run SQL on the warehouse. Poll until terminal. |
| `catalog.list` | GET | `/api/2.1/unity-catalog/catalogs` | List catalogs the caller may `USE CATALOG`. |
| `schema.list` | GET | `/api/2.1/unity-catalog/schemas?catalog_name=` | List schemas in a catalog. |
| `table.list` | GET | `/api/2.1/unity-catalog/tables?catalog_name=&schema_name=` | List tables in a schema. |

Optional internal: `GET /api/2.0/sql/statements/{statement_id}/result/chunks/{chunk_index}` for INLINE paging. Cancel: `POST /api/2.0/sql/statements/{statement_id}/cancel` on handler timeout.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `execute.sql` | no | Chooses the row above |
| `auth` | select | yes | `pat` | no | `pat` \| `oauthM2m` |
| `host` | string | yes | | no | Workspace URL, e.g. `https://adb-….azuredatabricks.net` (no `/api` suffix) |
| `httpPath` | string | yes for SQL | | no | JDBC HTTP path; parse `warehouse_id` from `/sql/1.0/warehouses/{id}` |
| `warehouseId` | string | if no parseable `httpPath` | | no | Explicit warehouse id for Statement API |
| `token` | password | if `auth=pat` | | yes | Personal access token |
| `clientId` | string | if `auth=oauthM2m` | | no | Service-principal application id |
| `clientSecret` | password | if `auth=oauthM2m` | | yes | OAuth M2M secret |
| `waitTimeout` | string | no | `10s` | no | Statement API `wait_timeout` (`0s` or `5s`–`50s`) |
| `onWaitTimeout` | select | no | `CONTINUE` | no | `CONTINUE` \| `CANCEL` |
| `requestTimeoutMs` | number | no | `60000` | no | Per HTTP call timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | SQL / names. No tokens. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include PAT or client secret |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = execute.sql`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sql` | string | yes | Statement text (max 16 MiB per API). Use `:name` parameters. |
| `parameters` | array | no | `{ name, value, type? }[]` as Statement API `parameters` |
| `catalog` | string | no | Default catalog (`USE CATALOG`) |
| `schema` | string | no | Default schema |
| `rowLimit` | number | no | API `row_limit` (sets `truncated` if trimmed) |
| `byteLimit` | number | no | API `byte_limit` — also enforce a node cap (default 8 MiB INLINE) |
| `disposition` | string | no | Default `INLINE`. Do not use `EXTERNAL_LINKS` unless explicitly required; never send Bearer to those URLs. |

### `operation = catalog.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `maxResults` | number | no | ≤ 1000; `0` uses server page size (recommended by Databricks) |
| `pageToken` | string | no | Pagination |
| `includeBrowse` | boolean | no | Include browse-only catalogs |

### `operation = schema.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `catalogName` | string | yes | Parent catalog |
| `maxResults` | number | no | ≤ 1000 |
| `pageToken` | string | no | Pagination |

### `operation = table.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `catalogName` | string | yes | Catalog |
| `schemaName` | string | yes | Schema |
| `maxResults` | number | no | Pagination size |
| `pageToken` | string | no | Pagination |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | SQL | Statement `status.state`: `PENDING` \| `RUNNING` \| `SUCCEEDED` \| `FAILED` \| `CANCELED` \| `CLOSED` |
| `statementId` | string | SQL | Poll id |
| `manifest` | object | SQL success | Schema / chunks metadata (no secrets) |
| `result` | object | SQL success INLINE | First chunk (`data_array` for `JSON_ARRAY`) |
| `truncated` | boolean | SQL | Row or byte limit hit |
| `catalogs` | array | catalog.list | Catalog names + metadata |
| `schemas` | array | schema.list | Schema objects |
| `tables` | array | table.list | Table objects |
| `nextPageToken` | string | list ops | Continue paging |
| `error` | object | failure | `{ message, httpStatus }` — no Bearer tokens |

Warehouse not running / CAN USE missing → `{ error }` with the provider message (typically HTTP 400/403).

## Legal

- Databricks Master Cloud Services Agreement: https://www.databricks.com/legal/mcsa
- Statement Execution: https://docs.databricks.com/api/workspace/statementexecution
- Tutorial (warehouse + PAT + Unity Catalog ACLs): https://docs.databricks.com/aws/en/dev-tools/sql-execution-tutorial
- OAuth M2M: https://docs.databricks.com/aws/en/dev-tools/auth/oauth-m2m
- Unity Catalog list catalogs: https://docs.databricks.com/api/workspace/catalogs/list
- SQL warehouse DBU / cloud costs are billed to the customer. **Unverified (2026-08-20)** numeric API rate limits — confirm in workspace/account limits at implement time.
- SQL and results may be personal data. Residency follows the workspace cloud region.
- Official REST only. No Spark UI scrape. No Jobs API.

## Implementation notes

- JDK HTTP only. Host = configured workspace origin.
- `pat`: `Authorization: Bearer {token}`.
- `oauthM2m`: `POST {host}/oidc/v1/token` with HTTP Basic `clientId:clientSecret` and body `grant_type=client_credentials&scope=all-apis`. Cache `access_token` for `expires_in` (typically 3600s). Then Bearer that token.
- Parse `warehouse_id` from `httpPath` (`/sql/1.0/warehouses/{id}`). Reject unparseable paths with `{ error }` rather than guessing.
- Default `wait_timeout=10s`, `on_wait_timeout=CONTINUE`. If state is `PENDING`/`RUNNING`, poll GET statement with jitter until `SUCCEEDED`/`FAILED`/`CANCELED`/`CLOSED` or wall-clock cap. Terminal statements expire ~12h (then HTTP 404).
- INLINE results abort above 25 MiB at Databricks; keep node cap lower (8 MiB).
- Retry 429/5xx with jitter. Do not retry 401/403. Do not retry failed SQL as a duplicate POST without a new client intent.
- Errors → `{ error }`. Never log PAT, OAuth secret, or SQL text. Log statement_id, warehouse_id, HTTP status, row counts.
- Unity Catalog enforces ACLs independently of warehouse CAN USE — both must pass.
