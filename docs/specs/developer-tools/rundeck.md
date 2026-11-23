# Rundeck

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/rundeck/`
- **Researched:** 2026-08-20
- **Official API:** Rundeck Web API **version 59** (path `/api/59`)
- **Docs:** https://docs.rundeck.com/docs/api/ (verified Current = 59, Minimum = 17, Deprecation = 34)
- **Auth:** api_key (token header) or basic (session login) — secret binding: `token` or `password`

## Scope

One node for a **customer Rundeck** (or Process Automation) instance. Calls `{baseUrl}/api/{apiVersion}/…` with JSON. Default `apiVersion` is **59** (current as of 2026-08-20). Operators with older servers may lower it to ≥ 17.

Prefer **API token** (`X-Rundeck-Auth-Token`). Username/password is a session cookie login (`POST /j_security_check` or documented auth URL) — more fragile; implement token first, basic as fallback.

Do not scrape the Rundeck UI. Rundeck 6+ is JSON-only (XML removed).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `job_run` | POST | `/api/{v}/job/{id}/run` | Run a job by UUID. JSON body for options (`argString` / `options`). Alternate documented path `/api/12/job/{id}/executions` — **use `/run`**. |
| `job_get` | GET | `/api/{v}/job/{id}` | Job definition/metadata |
| `execution_get` | GET | `/api/{v}/execution/{id}` | One execution |
| `execution_list` | GET | `/api/{v}/job/{id}/executions` or `/api/{v}/project/{project}/executions` | Recent executions |

Dropped: XML content types, `authtoken` query parameter (token in URL leaks to logs/proxies), incubating endpoints.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `job_get` | no | Chooses the row above |
| `baseUrl` | string | yes | empty | no | Instance origin, e.g. `https://rundeck.example.com`. No trailing slash. HTTPS in production. |
| `apiVersion` | number | no | `59` | no | Path version. Clamp 17–59. |
| `auth` | select | yes | `token` | no | `token` \| `basic` |
| `token` | password | if `auth=token` | empty | yes | Header `X-Rundeck-Auth-Token`. Never query `authtoken`. |
| `username` | string | if `auth=basic` | empty | no | Session login user |
| `password` | password | if `auth=basic` | empty | yes | Session login password |
| `project` | string | no | empty | no | Default project for `execution_list` when listing by project |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout |
| `insecureTls` | boolean | no | `false` | no | Lab-only |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation; no secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No token/password |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = job_run`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job_id` | string | yes | Job UUID |
| `arg_string` | string | no | `-opt value -opt2 value` |
| `options` | object | no | JSON `options` map (API v18+). If set, send JSON body and ignore `arg_string`. |
| `filter` | string | no | Node filter |
| `loglevel` | string | no | `DEBUG` \| `VERBOSE` \| `INFO` \| `WARN` \| `ERROR` |
| `run_at_time` | string | no | ISO-8601 (API v18+) |

JSON body when `Content-Type: application/json`:

```json
{ "options": { "myopt1": "value" }, "loglevel": "INFO", "filter": "" }
```

### `operation = job_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job_id` | string | yes | Job UUID |

### `operation = execution_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `execution_id` | string | yes | Execution id |

### `operation = execution_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job_id` | string | one of job/project | List executions of this job → `/job/{id}/executions` |
| `project` | string | one of job/project | Else `/project/{project}/executions` (config `project` as fallback) |
| `max` | number | no | Page size (default 20, cap 100) |
| `offset` | number | no | Offset |
| `status` | string | no | Filter if documented on the instance (`succeeded`, `failed`, `running`, …) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `job` | object | `job_get` | id, name, project, href, scheduled |
| `execution` | object | `job_run`, `execution_get` | id, status, permalink, job, date-started |
| `executions` | array | `execution_list` | Summaries |
| `error` | string | failure | Rundeck `message` / `errorCode` if present |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 30–60 + jitter |

## Legal

- API reference (v59): https://docs.rundeck.com/docs/api/
- Basics: https://docs.rundeck.com/docs/api/api_basics.html
- Customer instance. Job options and execution logs may contain secrets/PHI — do not log option values or output text.
- No single public SaaS quota; honor 429.

## Implementation notes

- JDK HTTP only. TLS verified unless lab `insecureTls`.
- Headers: `Accept: application/json`, `X-Rundeck-Auth-Token: {token}` (token mode), `User-Agent: StudioQuack/1.0`. POST JSON: `Content-Type: application/json`.
- **Do not** put the token in the URL.
- `auth=basic`: one login POST, cookie jar for subsequent calls in the **same invoke only**. Do not persist cookies.
- Body cap **8 MiB**. 429 retry with jitter (max 3).
- Execution output (`GET /execution/{id}/output`) is **not** a catalog verb — omit (unbounded logs).
- Logs: operation, job/execution ids, HTTP status — never token, password, option values.
- Errors → `{ error }` on OUTPUT.
