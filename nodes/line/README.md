# LINE

In-process StudioQuack builtin (`templateId` `3a9333ea-c9eb-5c0a-8d25-cbbd72a743d5`, override with `SQ_TEMPLATE_ID_LINE`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official LINE Messaging API** at `https://api.line.me/v2`. Auth: `bearer` via `channel_access_token`.

Provider docs: [https://developers.line.biz/en/reference/messaging-api/](https://developers.line.biz/en/reference/messaging-api/)

Terms: [https://terms2.line.me/LINE_Developers_Developer_Agreement](https://terms2.line.me/LINE_Developers_Developer_Agreement)

Messaging API only. LINE Notify ended 2025-04-01 — do not implement Notify. Canonical vs social-media.md Line row — same node. listen = webhook signature (channel secret) as BPMN message events.

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
| `push-message` | POST | `/bot/message/push` | Official HTTP |
| `reply-message` | POST | `/bot/message/reply` | Official HTTP |
| `multicast` | POST | `/bot/message/multicast` | Official HTTP |
| `broadcast` | POST | `/bot/message/broadcast` | Official HTTP |
| `get-profile` | GET | `/bot/profile/{userId}` | Official HTTP |
| `get-content` | GET | `/bot/message/{messageId}/content` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `channel_access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Messaging API only. LINE Notify ended 2025-04-01 — do not implement Notify. Canonical vs social-media.md Line row — same node. listen = webhook signature (channel secret) as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
