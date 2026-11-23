# Netlify

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_NETLIFY`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Netlify API](https://docs.netlify.com/api-and-cli-guides/api-guides/get-started-with-api/) at `https://api.netlify.com/api/v1`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `deploy_create` | POST | `/sites/{site_id}/deploys` | Official HTTP |
| `deploy_get` | GET | `/deploys/{deploy_id}` | Official HTTP |
| `deploy_list` | GET | `/sites/{site_id}/deploys` | Official HTTP |
| `deploy_cancel` | POST | `/deploys/{deploy_id}/cancel` | Official HTTP |
| `site_get` | GET | `/sites/{site_id}` | Official HTTP |
| `site_list` | GET | `/sites` | Official HTTP |
| `site_delete` | DELETE | `/sites/{site_id}` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | Netlify personal access token. Authorization: Bearer. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [Netlify API docs](https://docs.netlify.com/api-and-cli-guides/api-guides/get-started-with-api/). Terms: https://www.netlify.com/legal/terms/.
- Netlify Terms. Official API only.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
