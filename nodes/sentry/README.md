# Sentry

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_SENTRY`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Sentry Web API](https://docs.sentry.io/api/) at `https://sentry.io/api/0`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `auth_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `event_get` | GET | `/projects/{org}/{project}/events/{event_id}/` | Official HTTP |
| `event_list` | GET | `/projects/{org}/{project}/events/` | Official HTTP |
| `issue_get` | GET | `/organizations/{org}/issues/{issue_id}/` | Official HTTP |
| `issue_list` | GET | `/organizations/{org}/issues/` | Official HTTP |
| `issue_update` | PUT | `/organizations/{org}/issues/{issue_id}/` | Official HTTP |
| `issue_delete` | DELETE | `/organizations/{org}/issues/{issue_id}/` | Official HTTP |
| `project_create` | POST | `/teams/{org}/{team}/projects/` | Official HTTP |
| `project_get` | GET | `/projects/{org}/{project}/` | Official HTTP |
| `project_update` | PUT | `/projects/{org}/{project}/` | Official HTTP |
| `release_create` | POST | `/organizations/{org}/releases/` | Official HTTP |
| `release_get` | GET | `/organizations/{org}/releases/{version}/` | Official HTTP |
| `org_get` | GET | `/organizations/{org}/` | Official HTTP |
| `team_get` | GET | `/teams/{org}/{team}/` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `auth_token` | password | yes | yes | Sentry auth token (org token or user token). Authorization: Bearer. Never logged. |
| `org` | string | yes | no | Organization slug (`{org}` in paths). |
| `team` | string | no | no | Default team slug for `project_create` / `team_get` when INPUT omits `team`. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.auth_token`. Never logged, never returned.
- [Sentry Web API docs](https://docs.sentry.io/api/). Terms: https://sentry.io/terms/.
- Sentry Terms. Self-host uses the same /api/0 paths; packaged baseUrl is sentry.io.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
