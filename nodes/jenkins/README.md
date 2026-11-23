# Jenkins

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_JENKINS`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Jenkins Remote Access API](https://www.jenkins.io/doc/book/using/remote-access-api/) at `{jenkins_url}` (config, e.g. `https://ci.example.com`).

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `job_build` | POST | `/job/{jobPath}/build` | Query `token`, `delay` |
| `job_build_wait` | POST | `/job/{jobPath}/build` | Query `token`, `delay` |
| `job_get` | GET | `/job/{jobPath}/api/json` | Official HTTP |
| `job_copy` | POST | `/createItem` | Query `name`, `mode=copy`, `from` |
| `job_create` | POST | `/createItem` | Query `name`; raw `configXml` as `application/xml` |
| `build_list` | GET | `/job/{jobPath}/api/json` | Official HTTP |
| `queue_get` | GET | `/queue/api/json` | Whole build queue |
| `queue_item_get` | GET | `/queue/item/{queue_id}/api/json` | One queue item (`queue_id` from job_build) |
| `quiet_down` | POST | `/quietDown` | Official HTTP |
| `restart` | POST | `/safeRestart` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | Jenkins user API token (not account password). Basic user:token. CSRF crumb is not auto-fetched by OfficialApiNode. |
| `user` | string | yes | no | Basic username paired with `api_token` |
| `jenkins_url` | string | no | no | Jenkins origin (scheme + host, no trailing slash) |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [Jenkins Remote Access API docs](https://www.jenkins.io/doc/book/using/remote-access-api/). Terms: https://www.jenkins.io/security/.
- Customer Jenkins controller. Packaged baseUrl is a placeholder — OfficialApiNode cannot override host from config. Never Docker.sock. TLS on in production.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
