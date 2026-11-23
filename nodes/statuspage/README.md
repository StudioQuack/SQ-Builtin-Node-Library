# Statuspage

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_STATUSPAGE`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Statuspage REST API](https://developer.statuspage.io/) at `https://api.statuspage.io/v1`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `page_get` | GET | `/pages/{pageId}` | Official HTTP |
| `incident_create` | POST | `/pages/{pageId}/incidents` | Official HTTP |
| `incident_update` | PATCH | `/pages/{pageId}/incidents/{incident_id}` | Official HTTP |
| `incident_list` | GET | `/pages/{pageId}/incidents` | Official HTTP |
| `component_get` | GET | `/pages/{pageId}/components/{component_id}` | Official HTTP |
| `component_update` | PATCH | `/pages/{pageId}/components/{component_id}` | Official HTTP |
| `subscriber_add` | POST | `/pages/{pageId}/subscribers` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_key` | password | yes | yes | Statuspage API key. Authorization: OAuth <key> (header only; query api_key retired 2026-05-15). Never logged. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [Statuspage REST API docs](https://developer.statuspage.io/). Terms: https://www.atlassian.com/legal/cloud-terms-of-service.
- Do not create incidents on pages you do not own. Atlassian Cloud Terms.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
