# Docker Hub

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_DOCKER_HUB`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Docker Hub API](https://docs.docker.com/reference/api/hub/latest/) at `https://hub.docker.com/v2`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `BPMN error` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `repo_get` | GET | `/namespaces/{namespace}/repositories/{name}` | Official HTTP |
| `repo_list` | GET | `/namespaces/{namespace}/repositories` | Official HTTP |
| `tag_list` | GET | `/namespaces/{namespace}/repositories/{name}/tags` | Official HTTP |
| `tag_delete` | DELETE | `/repositories/{namespace}/{name}/tags/{tag}` | Legacy path; current OpenAPI documents GET/HEAD on `/namespaces/.../tags/{tag}` only |
| `webhook_set` | POST | `/webhooks/{namespace}/{name}/` | Not listed in current Hub OpenAPI |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_token` | password | yes | yes | JWT from POST /v2/auth/token. Never logged. Not an Engine socket token. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.access_token`. Never logged, never returned.
- [Docker Hub API docs](https://docs.docker.com/reference/api/hub/latest/). Terms: https://www.docker.com/legal/docker-terms-of-service/.
- Docker Hub Terms. Not Docker Engine (/var/run/docker.sock). No hub HTML scrape.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
