# Teamwork


Project / person / task / todo-item writes take purpose fields (`name`, `firstName`, `lastName`, `email`, `content`, `dueAt`, …) merged into the official wrappers via bodyMap. Docs: https://apidocs.teamwork.com/docs/teamwork

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/teamwork/api_spec.json`.

## Overview

Official Teamwork.com **API v3**. Token is site-admin scoped. Config `site_host` is the hostname only (e.g. `acme.teamwork.com`) interpolated into `https://{site_host}/projects/api/v3` (https://apidocs.teamwork.com/docs/teamwork).

- **Official API:** Teamwork.com API
- **API v3:** base `https://{site_host}/projects/api/v3` (https://apidocs.teamwork.com/docs/teamwork). `task_create` is `POST /tasklists/{tasklist_id}/tasks.json`. `task_complete` is `PATCH /tasks/{task_id}.json`.
- **Docs:** https://apidocs.teamwork.com/
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `api_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/teamwork/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_create` | POST | `https://{site_host}/projects/api/v3/projects.json` | Official |
| `person_add` | POST | `https://{site_host}/projects/api/v3/people.json` | Official |
| `task_create` | POST | `https://{site_host}/projects/api/v3/tasklists/{tasklist_id}/tasks.json` | Official |
| `task_complete` | PATCH | `https://{site_host}/projects/api/v3/tasks/{task_id}.json` | Official |
| `subtask_create` | POST | `https://{site_host}/projects/api/v3/tasks/{task_id}/subtasks.json` | Official |
| `milestone_create` | POST | `https://{site_host}/projects/api/v3/projects/{project_id}/milestones.json` | Official |
| `company_create` | POST | `https://{site_host}/projects/api/v3/companies.json` | Official |
| `calendar_event_create` | POST | `https://{site_host}/projects/api/v3/calendarevents.json` | Official |
| `link_create` | POST | `https://{site_host}/projects/api/v3/projects/{project_id}/links.json` | Official |
| `listen` | — | ingress | BPMN message event; this node does not open a socket |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.| Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

See `node_definition.json`. Always includes `operation` (select), the auth secret, `timeout_seconds` (default 25), and provider ids listed in the catalog row.

## Legal

- Terms: https://www.teamwork.com/legal/terms-of-service
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
