# CloudConvert

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/cloudconvert/`
- **Official API:** https://cloudconvert.com/docs/getting-started/introduction
- **Auth secret:** `api_token`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded or converted bytes land on OUTPUT `file` (Platform file ref-id
(sq-file: / sq-ref:gridfs:). Never inline bytes or base64 as the stored type.).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `convert` | POST | `/convert` |
| `capture_website` | POST | `/capture-website` |
| `archive_create` | POST | `/archive` |
| `job_create` | POST | `/jobs` |
| `job_get` | GET | `/jobs/{id}` |
| `job_delete` | DELETE | `/jobs/{id}` |
| `task_cancel` | POST | `/tasks/{id}/cancel` |
| `task_delete` | DELETE | `/tasks/{id}` |
| `file_download` | GET | `/tasks/{id}` |
| `listen` | ingress | BPMN message event — does not open a socket |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Configuration

`operation` (select), `api_token` (password), `sandbox` (host prefix: `api` live / `sandbox.api` sandbox), `timeout_seconds`.

`baseUrl` embeds `{sandbox}`; `api_spec.placeholderDefaults.sandbox` is **`api`** when INPUT and config both omit `sandbox`, so the node reaches `https://api.cloudconvert.com/v2` without extra config. Override via instance `sandbox` or INPUT when using EU/US hostnames or the provider sandbox.

**IDs:** `job_get`, `job_delete`, `task_cancel`, `task_delete`, and `file_download` require INPUT **`id`** (CloudConvert job/task UUID). Tool calls may send `job_id` / `task_id`; plain string values mistakenly sent as `file` are mapped to `id`. **`file` on OUTPUT** is a downloaded bytes ref — not a job id.

**Agent tests:** `SubProcessFailedException: edge-transform … failed/empty result` means the BPMN **edge DuckTape** between the agent and this node failed before `OfficialApiNode` ran — fix workflow wiring, not this API spec.

## Legal

- Terms: https://cloudconvert.com/terms
- Official-API-first. No scrape. No unofficial web wrapper.
- API v1 is retired (2022). Official create path is POST /v2/jobs (tasks: import/convert/export). Files processed on CloudConvert infra — DPA. import/upload uses type file.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
