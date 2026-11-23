# Snowflake

Official Snowflake **SQL API v2** (`OfficialApiNode` via `api_spec.json`). Customer-owned account. Not JDBC/ODBC and not account-password login.

- **Verified HTTP:** `POST https://<account_identifier>.snowflakecomputing.com/api/v2/statements` — [SQL API reference](https://docs.snowflake.com/en/developer-guide/sql-api/reference) (2026-08-20). Poll `GET /api/v2/statements/{statementHandle}`. Cancel `POST /api/v2/statements/{statementHandle}/cancel`.
- **Base:** `https://{account_url}` — config `account_url` is the account locator host (e.g. `xy12345.us-east-1.snowflakecomputing.com`). See [account identifiers](https://docs.snowflake.com/en/user-guide/admin-account-identifier).
- **Auth:** Bearer `access_token` — programmatic access token (PAT), OAuth access token, or key-pair JWT. **Not** the account password. extraHeaders send `X-Snowflake-Authorization-Token-Type` from config `token_type` (default `KEYPAIR_JWT`; also `OAUTH` / `PROGRAMMATIC_ACCESS_TOKEN`). Blank `token_type` omits the header. See [authenticating](https://docs.snowflake.com/en/developer-guide/sql-api/authenticating).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | POST | `/api/v2/statements` |
| `statement.get` | GET | `/api/v2/statements/{statementHandle}` |
| `statement.cancel` | POST | `/api/v2/statements/{statementHandle}/cancel` |

`execute.sql` bodyFrom `[statement, timeout, database, schema, warehouse, bindings]`. Bind with SQL API `bindings` (never concatenate). Async 202 responses: poll `statement.get`. `timeout` in the JSON body is the **statement** timeout (seconds), distinct from config `timeout_seconds` (HTTP).

`statement.cancel` bodyFrom `[requestId]` so a missing `requestId` yields `{}` (official cancel has no required body). `requestId` on GET is a query param.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer PAT / OAuth / JWT |
| `account_url` | string | no | Account host without scheme |
| `token_type` | string | no | `X-Snowflake-Authorization-Token-Type` (default KEYPAIR_JWT) |
| `timeout_seconds` | number | no | HTTP timeout, default 45 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is controller of warehouse results; compute credits bill to their account.
- https://www.snowflake.com/legal/
- Official SQL API only. Some statement types are unsupported (see SQL API limitations).
