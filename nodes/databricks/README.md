# Databricks

Official **SQL Statement Execution API 2.0** (`OfficialApiNode` via `api_spec.json`). Customer-owned workspace and SQL warehouse. Not Jobs / Spark submit.

- **Verified HTTP:** `POST https://<workspace>/api/2.0/sql/statements` — [Statement Execution](https://docs.databricks.com/api/workspace/statementexecution) (2026-08-20). Poll `GET /api/2.0/sql/statements/{statement_id}`. Cancel `POST /api/2.0/sql/statements/{statement_id}/cancel`.
- **Placeholder `baseUrl`:** `https://dbc-xxxxxxxx-xxxx.cloud.databricks.com` (AWS workspace-style host). Azure is often `https://adb-<id>.<region>.azuredatabricks.net`; GCP differs. OfficialApiNode does **not** substitute `{workspace}`. Replace `api_spec.json` `baseUrl` with the workspace origin (no `/api` suffix).
- **Auth:** Bearer `access_token` (PAT or OAuth access token). OAuth M2M client-credentials exchange is **not** performed (engine: one bearer). Never on INPUT.

The warehouse must be **running**. Principal needs **CAN USE** on the warehouse plus Unity Catalog privileges. Do not download `EXTERNAL_LINKS` result URLs with this Bearer token (official warning).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | POST | `/api/2.0/sql/statements` |
| `statement.get` | GET | `/api/2.0/sql/statements/{statement_id}` |
| `statement.cancel` | POST | `/api/2.0/sql/statements/{statement_id}/cancel` |

`execute.sql` bodyFrom `[warehouse_id, statement, wait_timeout, catalog, schema, parameters]`. `parameters` is the official named-parameter array `[{name, value, type}]` ([Execute Statement](https://docs.databricks.com/api/workspace/statementexecution/executestatement)). `wait_timeout` is a string such as `10s` (`0s` or `5s`–`50s`). This node does not auto-poll; use `statement.get` when state is `PENDING` / `RUNNING`. `statement.cancel` has no `bodyFrom` — official cancel body is empty ([Cancel statement](https://docs.databricks.com/api/workspace/statementexecution/cancelexecution)). Empty `bodyFrom` JSON-encodes the entire INPUT; keep cancel INPUT to `statement_id` only.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer PAT / OAuth token |
| `timeout_seconds` | number | no | HTTP timeout, default 60 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes `access_token`.

## Legal

- Warehouse DBU / cloud costs bill to the customer. SQL and results may be personal data; residency follows the workspace region.
- Master Cloud Services Agreement: https://www.databricks.com/legal/mcsa
- Official REST only. No Spark UI scrape.
