# CircleCI

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_CIRCLECI`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [CircleCI API v2](https://circleci.com/docs/api/v2/index.html) at `https://circleci.com/api/v2`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `pipeline_get` | GET | `/pipeline/{pipeline_id}` | Official HTTP |
| `pipeline_list` | GET | `/project/{project_slug}/pipeline` | Official HTTP |
| `pipeline_trigger` | POST | `/project/{project_slug}/pipeline` | Older trigger; still documented for CircleCI Server |
| `pipeline_run` | POST | `/project/{project_slug}/pipeline/run` | Current Cloud trigger (definition_id + config/checkout) |
| `workflow_cancel` | POST | `/workflow/{id}/cancel` | Official HTTP |
| `job_get` | GET | `/project/{project_slug}/job/{job_number}` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | CircleCI personal API token. Header Circle-Token. Never logged. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Non-2xx raise a BPMN error.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [CircleCI API v2 docs](https://circleci.com/docs/api/v2/index.html). Terms: https://circleci.com/legal/terms-of-service/.
- CircleCI Terms. Official API v2 only.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
