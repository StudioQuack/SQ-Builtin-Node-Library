# Turso

Official libSQL SQL-over-HTTP **v2** (`OfficialApiNode` via `api_spec.json`). Customer-owned Turso / libSQL database. Not the Turso Platform control-plane API (`https://api.turso.tech` is wrong for SQL).

- **Verified HTTP:** `POST https://[databaseName]-[organizationSlug].turso.io/v2/pipeline` — [SQL over HTTP](https://docs.turso.tech/sdk/http/reference) (2026-08-20). Protocol: [HTTP v2 spec](https://github.com/tursodatabase/libsql/blob/main/docs/HTTP_V2_SPEC.md).
- **Health:** `GET /health` (empty 200 body) is official on the **database** origin, with Bearer token.
- **Host:** set config `database_url` (HTTPS origin from the dashboard) or `database_name` + `organization_slug`. `libsql://` / `turso://` normalize to `https://`. Placeholder hosts are rejected before HTTP.
- **Auth:** Bearer `auth_token` (per-database token: `turso db tokens create`). Never on INPUT. Never logged.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | POST | `/v2/pipeline` |
| `database.health` | GET | `/health` |

`execute.sql` accepts top-level `sql` (or query/statement aliases), a `requests` pipeline array (JSON object shapes and JSON strings coerced), and appends `type: close` when missing. Wire body is `{ "requests": [ … ] }` only.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `auth_token` | password | yes | Bearer database token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is controller of SQLite rows they store. Minimize: send only the SQL/binds required.
- Provider terms: https://turso.tech/legal/terms
- Official pipeline HTTP only. Do not scrape the dashboard. Do not call Platform org/database CRUD from this node.
