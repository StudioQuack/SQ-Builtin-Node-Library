# ngrok

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_NGROK`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [ngrok API](https://ngrok.com/docs/api/) at `https://api.ngrok.com`. Every request sends `Ngrok-Version: 2` via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `tunnel_list` | GET | `/endpoints` | Cloud Endpoints list (replaces /tunnels) |
| `tunnel_get` | GET | `/endpoints/{id}` | Cloud Endpoint by id |
| `edge_get` | GET | `/edges/https/{id}` | Deprecated: Edges sunset 2025-12-31 |
| `reserved_domain_list` | GET | `/reserved_domains` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_key` | password | yes | yes | ngrok API key. Authorization: Bearer. Ngrok-Version: 2 is sent via extraHeaders. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

**Operation default:** If INPUT omits `operation`, the instance config default (`tunnel_list`) is used. AI Agent tool calls strip blank string fields before execute, so `operation: ""` behaves like omission (not an error).

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_key`. Never logged, never returned.
- [ngrok API docs](https://ngrok.com/docs/api/). Terms: https://ngrok.com/tos.
- ngrok Terms. Tunnel URLs are customer ingress. Official API only.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
