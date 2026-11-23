# TheHive

Official [TheHive 5 API](https://docs.strangebee.com/thehive/api-docs) **v1 only** (`/api/v1`). Public API v0 is deprecated since 5.4. This node does **not** dual-stack TheHive 3/4 paths.

- **Docs:** https://docs.strangebee.com/thehive/api-docs
- **Base:** `https://{thehiveHost}`
- **Auth:** bearer `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`. `listen` is `kind: ingress` (BPMN message start/catch/boundary — no socket).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-alert` | POST | `{thehiveHost}/api/v1/alert` | Required: type, source, sourceRef, title, description |
| `get-alert` | GET | `{thehiveHost}/api/v1/alert/{alertId}` |
| `search-alerts` | POST | `{thehiveHost}/api/v1/query` | Body: query (Query DSL) |
| `update-alert` | PATCH | `{thehiveHost}/api/v1/alert/{alertId}` |
| `delete-alert` | DELETE | `{thehiveHost}/api/v1/alert/{alertId}` |
| `promote-alert` | POST | `{thehiveHost}/api/v1/alert/{alertId}/case` |
| `merge-alert` | POST | `{thehiveHost}/api/v1/alert/{alertId}/merge/{caseId}` |
| `create-case` | POST | `{thehiveHost}/api/v1/case` | Required: title, description |
| `get-case` | GET | `{thehiveHost}/api/v1/case/{caseId}` |
| `search-cases` | POST | `{thehiveHost}/api/v1/query` |
| `update-case` | PATCH | `{thehiveHost}/api/v1/case/{caseId}` |
| `delete-case` | DELETE | `{thehiveHost}/api/v1/case/{caseId}` |
| `get-timeline` | GET | `{thehiveHost}/api/v1/case/{caseId}/timeline` |
| `add-case-attachment` | POST | `{thehiveHost}/api/v1/case/{caseId}/attachments` |
| `create-observable` | POST | `{thehiveHost}/api/v1/case/{caseId}/observable` |
| `get-observable` | GET | `{thehiveHost}/api/v1/observable/{observableId}` |
| `search-observables` | POST | `{thehiveHost}/api/v1/query` |
| `update-observable` | PATCH | `{thehiveHost}/api/v1/observable/{observableId}` |
| `delete-observable` | DELETE | `{thehiveHost}/api/v1/observable/{observableId}` |
| `execute-analyzer` | POST | `{thehiveHost}/api/v1/connector/cortex/job` |
| `create-task` | POST | `{thehiveHost}/api/v1/case/{caseId}/task` |
| `get-task` | GET | `{thehiveHost}/api/v1/task/{taskId}` |
| `search-tasks` | POST | `{thehiveHost}/api/v1/query` |
| `update-task` | PATCH | `{thehiveHost}/api/v1/task/{taskId}` |
| `delete-task` | DELETE | `{thehiveHost}/api/v1/task/{taskId}` |
| `create-log` | POST | `{thehiveHost}/api/v1/task/{taskId}/log` |
| `get-log` | POST | `{thehiveHost}/api/v1/query` |
| `create-comment` | POST | `{thehiveHost}/api/v1/case/{caseId}/comment` |
| `search-comments` | POST | `{thehiveHost}/api/v1/query` |
| `execute-query` | POST | `{thehiveHost}/api/v1/query` |
| `listen` | ingress | — |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `thehiveHost` | string | no | Host without scheme |
| `api_key` | password | yes | Bearer |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Mapping notes

- Search verbs (`search-alerts`, `search-cases`, `search-observables`, `search-tasks`, `search-comments`, `execute-query`, `get-log`) all POST `/api/v1/query`. There is **no** `GET /api/v1/log/{logId}` in the TheHive 5 API v1 pages fetched 2026-08-20 (`log.find` is query-only).
- `create-observable` uses `POST /api/v1/case/{caseId}/observable`. Alert observables: `POST /api/v1/alert/{alertId}/observable` is official but not a separate catalog op.
- `add-case-attachment` is `POST /api/v1/case/{caseId}/attachments` (type `file`). Official call is multipart; OfficialApiNode JSON-posts.
- `execute-analyzer` is TheHive’s Cortex connector: `POST /api/v1/connector/cortex/job`.
- `listen` is BPMN ingress only.

## Legal

Self-hosted. Case/alert/observable payloads are incident data (may include PII). Never log API keys or attachment bytes.
