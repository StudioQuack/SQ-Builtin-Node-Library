# Attio

Official Attio REST API v2 (`https://api.attio.com`, OpenAPI info.version 2.0.0). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.attio.com/rest-api/overview · Auth https://docs.attio.com/rest-api/guides/authentication
- **Base:** `https://api.attio.com`
- **Auth:** Bearer `api_key` (workspace token or access token). Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `record.create` | POST | `/v2/objects/{object}/records` |
| `record.get` | GET | `/v2/objects/{object}/records/{record_id}` |
| `record.update` | PATCH | `/v2/objects/{object}/records/{record_id}` |
| `record.search` | POST | `/v2/objects/{object}/records/query` |
| `record.delete` | DELETE | `/v2/objects/{object}/records/{record_id}` |
| `person.upsert` | PUT | `/v2/objects/people/records` |
| `company.upsert` | PUT | `/v2/objects/companies/records` |
| `list.create` | POST | `/v2/lists` |
| `list.entry.create` | POST | `/v2/lists/{list}/entries` |
| `list.entry.update` | PATCH | `/v2/lists/{list}/entries/{entry_id}` |
| `list.entry.delete` | DELETE | `/v2/lists/{list}/entries/{entry_id}` |
| `list.entry.search` | POST | `/v2/lists/{list}/entries/query` |
| `attribute.create` | POST | `/v2/objects/{object}/attributes` |
| `attribute.archive` | POST | `/v2/objects/{object}/attributes/{attribute_id}/archive` |
| `selectOption.create` | POST | `/v2/objects/{object}/attributes/{attribute_id}/options` |
| `task.create` | POST | `/v2/tasks` |
| `task.update` | PATCH | `/v2/tasks/{task_id}` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Bearer token. Workspace **API keys** from Attio Admin are **64 characters** (truncated keys → HTTP 401). OAuth access tokens may differ in length. |
| `timeout_seconds` | number | no | Default 25 |

**Agent / tool testing:** Local routing and per-operation `required` checks passed in cycle testing; API happy paths need a valid `api_key`. INPUT schema exposes operation-specific fields for DuckTape / `ToolCatalog`. `trigger.listen` is BPMN ingress only — not callable from tools.

Shared production host: `api.attio.com` (no regional API host). Rate limits: 100 rps read, 25 rps write. `trigger.listen` is ingress (verify Attio signatures on the BPMN message event). avatar_url / logo_url cannot be set via API.

## Ports

`input` / `output` only. Write ops send the official Attio `data` envelope (`values` for records, `name`/`parent_object` for lists, `content` for tasks). Purpose fields (`email`, `name`, `content`, `values`, …) bodyMap into `data` / `data.values`; keep `data` as the escape hatch. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://attio.com — OAuth apps at https://build.attio.com. Scopes e.g. record_permission:read-write.
- Official API only. Do not scrape the vendor dashboard.
