# PagerDuty


Incident/note writes take purpose fields (`title`, `type`, `service_id`, `urgency`, `content`, …) merged into the official `incident` / `note` envelopes via bodyMap. Docs: https://developer.pagerduty.com/api-reference/a7d81b0e9200f-create-an-incident

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_PAGERDUTY`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [PagerDuty REST API v2](https://developer.pagerduty.com/api-reference/) at `https://api.pagerduty.com`. Every request sends `Accept: application/vnd.pagerduty+json;version=2` via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `incident_create` | POST | `/incidents` | Body wrapper `incident` {type, title, service{id,type}, urgency, body.details, incident_key} |
| `incident_get` | GET | `/incidents/{id}` | Official HTTP |
| `incident_list` | GET | `/incidents` | `limit`, `offset`, `statuses[]`, `service_ids[]`, `urgencies[]`, `since`, `until` |
| `incident_update` | PUT | `/incidents/{id}` | Body wrapper `incident` {type, status, urgency, resolution} |
| `note_create` | POST | `/incidents/{id}/notes` | Body wrapper `note` {content} |
| `note_list` | GET | `/incidents/{id}/notes` | Official HTTP |
| `log_get` | GET | `/incidents/{id}/log_entries` | Official HTTP |
| `user_get` | GET | `/users/{id}` | Official HTTP |
| `oncall_get` | GET | `/oncalls` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | PagerDuty REST API key. Authorization: Token token=<key>. Accept: application/vnd.pagerduty+json;version=2 is sent via extraHeaders. Include requester in body when the API requires From. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [PagerDuty REST API v2 docs](https://developer.pagerduty.com/api-reference/). Terms: https://www.pagerduty.com/terms-of-service/.
- PagerDuty Terms. Events API v2 ingest is a different host (events.pagerduty.com); this node is REST v2.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
