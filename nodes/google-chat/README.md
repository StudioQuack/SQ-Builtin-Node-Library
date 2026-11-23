# Google Chat

In-process StudioQuack builtin (`templateId` `097e91c5-45a8-56bb-aed9-554bbfbc2a78`, override with `SQ_TEMPLATE_ID_GOOGLE_CHAT`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Google Chat API v1** at `https://chat.googleapis.com/v1`. Auth: `bearer` via `access_token`.

Provider docs: [https://developers.google.com/workspace/chat/api/reference/rest](https://developers.google.com/workspace/chat/api/reference/rest)

Terms: [https://developers.google.com/terms](https://developers.google.com/terms)

Chat app must be installed in the space. Workspace admin allow-list. listen = Chat/Workspace Events as BPMN message events. Customer-level preview subscriptions are out of v1.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create-message` | POST | `/{parent}/messages` | Official HTTP |
| `get-message` | GET | `/{name}` | Official HTTP |
| `update-message` | PATCH | `/{name}` | Official HTTP |
| `delete-message` | DELETE | `/{name}` | Official HTTP |
| `list-messages` | GET | `/{parent}/messages` | Official HTTP |
| `get-membership` | GET | `/{name}` | Official HTTP |
| `list-memberships` | GET | `/{parent}/members` | Official HTTP |
| `get-space` | GET | `/{name}` | Official HTTP |
| `list-spaces` | GET | `/spaces` | Official HTTP |
| `send-and-wait` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Chat app must be installed in the space. Workspace admin allow-list. listen = Chat/Workspace Events as BPMN message events. Customer-level preview subscriptions are out of v1. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
