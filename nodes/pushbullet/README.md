# Pushbullet

In-process StudioQuack builtin (`templateId` `5c17e68b-9397-5950-bb94-7b6d4336b7c3`, override with `SQ_TEMPLATE_ID_PUSHBULLET`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Pushbullet API v2** at `https://api.pushbullet.com/v2`. Auth: `bearer` via `access_token`.

Provider docs: [https://docs.pushbullet.com/](https://docs.pushbullet.com/)

Terms: [https://www.pushbullet.com/tos](https://www.pushbullet.com/tos)

Account token sees all devices. listen = websocket tickle is not opened — BPMN message events for inbound pushes.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create-push` | POST | `/pushes` | Official HTTP |
| `list-pushes` | GET | `/pushes` | Official HTTP |
| `update-push` | POST | `/pushes/{iden}` | Official HTTP |
| `delete-push` | DELETE | `/pushes/{iden}` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Account token sees all devices. listen = websocket tickle is not opened — BPMN message events for inbound pushes. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
