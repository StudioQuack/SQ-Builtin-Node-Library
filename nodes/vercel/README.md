# Vercel

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_VERCEL`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Vercel REST API](https://vercel.com/docs/rest-api) at `https://api.vercel.com`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `deploy_create` | POST | `/v13/deployments` | `name` required; source from `gitSource` or `files` |
| `deploy_get` | GET | `/v13/deployments/{id}` | Official HTTP |
| `deploy_list` | GET | `/v6/deployments` | Official HTTP |
| `deploy_cancel` | PATCH | `/v12/deployments/{id}/cancel` | Official HTTP |
| `deploy_delete` | DELETE | `/v13/deployments/{id}` | Official HTTP |
| `project_get` | GET | `/v9/projects/{idOrName}` | Official HTTP |
| `project_create` | POST | `/v11/projects` | `name` required |
| `env_set` | POST | `/v10/projects/{idOrName}/env` | `key`, `value`, `type`, `target` required |
| `domain_get` | GET | `/v5/domains/{domain}` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | Vercel access token. Authorization: Bearer. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses raise a BPMN error (AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED).
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [Vercel REST API docs](https://vercel.com/docs/rest-api). Terms: https://vercel.com/legal/terms.
- Vercel Terms. Official REST + webhooks (listen = BPMN ingress).
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
