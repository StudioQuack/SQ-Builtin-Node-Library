# Opsgenie

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_OPSGENIE`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Opsgenie REST API](https://docs.opsgenie.com/docs/api-overview) at `https://{api_host}` (config `api_host`: `api.opsgenie.com` US default, `api.eu.opsgenie.com` EU; alerts under `/v2`). Atlassian [end of support is 2027-04-05](https://www.atlassian.com/software/opsgenie/migration); REST APIs keep working until then.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `alert_create` | POST | `/alerts` | Official HTTP |
| `alert_get` | GET | `/alerts/{id}` | Official HTTP |
| `alert_ack` | POST | `/alerts/{id}/acknowledge` | Official HTTP |
| `alert_close` | POST | `/alerts/{id}/close` | Official HTTP |
| `alert_note` | POST | `/alerts/{id}/notes` | Body `note` (string, required) |
| `incident_create` | POST | `/incidents` | Official HTTP |
| `schedule_oncall` | GET | `/schedules/{id}/on-calls` | Official HTTP |
| `heartbeat_ping` | POST | `/heartbeats/{name}/ping` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_key` | password | yes | yes | Opsgenie API key. Authorization: GenieKey <key>. |
| `api_host` | select | no | no | `api.opsgenie.com` (US, default) or `api.eu.opsgenie.com` (EU). |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [Opsgenie REST API docs](https://docs.opsgenie.com/docs/api-overview). End of support 2027-04-05: https://www.atlassian.com/software/opsgenie/migration. Terms: https://www.atlassian.com/legal/cloud-terms-of-service.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
