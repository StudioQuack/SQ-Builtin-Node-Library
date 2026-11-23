# Supabase

Official Supabase **PostgREST Data API** (`OfficialApiNode` via `api_spec.json`). Customer-owned project. Not PostgreSQL wire (use a Postgres node for that).

- **Verified HTTP:** `https://<project-ref>.supabase.co/rest/v1/` — [API guides](https://supabase.com/docs/guides/api) (2026-08-20). Table verbs: GET/POST/PATCH/DELETE `/rest/v1/{table}`. Functions: `POST /rest/v1/rpc/{fn}`.
- **Base:** config `project_url` (e.g. `https://abcd.supabase.co`) is substituted as `{project_url}`. No `/rest/v1` suffix.
- **Auth:** `apikey` plus `Authorization: Bearer` from the same `api_key` (official hosted Data API). Prefer publishable/`anon` plus RLS; `service_role` / secret keys bypass RLS. Never log `api_key`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `table.get` | GET | `/rest/v1/{table}` |
| `table.insert` | POST | `/rest/v1/{table}` |
| `table.update` | PATCH | `/rest/v1/{table}` |
| `table.delete` | DELETE | `/rest/v1/{table}` |
| `rpc` | POST | `/rest/v1/rpc/{fn}` |

`table.get` query: `select`, `limit`, `offset`, `order`, `id` (PostgREST filter value, e.g. `eq.1`). `table.update` / `table.delete` use `id=eq.<pk>`; PostgREST rejects unfiltered PATCH/DELETE by default.

`table.insert`, `table.update`, and `rpc` merge INPUT `record` as the JSON body (object = one row / args; array = bulk insert). Agent tools accept JSON objects or JSON strings for `record`. `table.delete` requires INPUT `id` (PostgREST filter). `Prefer` defaults to `return=representation`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | `apikey` + Bearer (anon, service_role, publishable, or secret) |
| `project_url` | string | no | Project origin, e.g. `https://abcd.supabase.co` |
| `prefer` | string | no | Default `return=representation` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is controller; RLS is their duty. `service_role` / secret keys bypass RLS.
- Terms: https://supabase.com/terms · Privacy: https://supabase.com/privacy
- Official PostgREST only. No Management API. No dashboard scrape. Legacy JWT `anon` / `service_role` keys are deprecated by end of 2026 — prefer `sb_publishable_` / `sb_secret_`.
