# Render

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_RENDER`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Render API](https://api-docs.render.com/) at `https://api.render.com/v1`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `service_get` | GET | `/services/{serviceId}` | Official HTTP |
| `service_list` | GET | `/services` | Official HTTP |
| `deploy_trigger` | POST | `/services/{serviceId}/deploys` | Body: `clearCache` (`clear`\|`do_not_clear`), optional `commitId`, `imageUrl` |
| `deploy_get` | GET | `/services/{serviceId}/deploys/{deployId}` | Official HTTP |
| `env_get` | GET | `/services/{serviceId}/env-vars` | Query `limit`, `cursor` |
| `env_set` | PUT | `/services/{serviceId}/env-vars` | Whole-body JSON array (`envVars`: `[{key,value}]` or `{key,generateValue:true}`). Replaces all vars; does not deploy. |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_key` | password | yes | yes | Render API key. Authorization: Bearer. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

**Default operation:** Omitted INPUT `operation` uses instance config (`service_get`).

**AI Agent tool schema:** Exposed `request` properties come from DuckTape mappings to INPUT (`operation`, `serviceId`, `deployId`, `envVars`, …). Unmapped tools show `properties: {}` while runtime still enforces per-operation `required` fields.

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses raise a BPMN error with `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [Render API docs](https://api-docs.render.com/). Terms: https://render.com/terms.
- Render Terms. Deploy hooks are official. No dashboard scrape.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
