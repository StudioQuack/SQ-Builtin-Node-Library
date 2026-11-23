# Rundeck

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_RUNDECK`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Rundeck API](https://docs.rundeck.com/docs/api/) (current version 59, minimum 17) at `{base_url}/api/59` (config `base_url`, e.g. `https://rundeck.example.com`).

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `job_run` | POST | `/job/{id}/run` | Official HTTP |
| `job_get` | GET | `/job/{id}` | Official HTTP |
| `execution_get` | GET | `/execution/{id}` | Official HTTP |
| `execution_list` | GET | `/project/{project}/executions` | Official HTTP |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | Rundeck API token. Header X-Rundeck-Auth-Token. |
| `base_url` | string | yes | no | Server URL without `/api`, e.g. `https://rundeck.example.com`. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [Rundeck API 47 docs](https://docs.rundeck.com/docs/api/). Terms: https://www.pagerduty.com/terms-of-service/.
- Customer Rundeck. Packaged baseUrl is a placeholder. Official API only.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
