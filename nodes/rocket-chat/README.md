# Rocket.Chat

In-process StudioQuack builtin (`templateId` `b795d16e-f55f-5388-a0a7-265a0f3deb14`, override with `SQ_TEMPLATE_ID_ROCKET_CHAT`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Rocket.Chat REST API** on the customer-hosted `host`. Auth: `X-Auth-Token` via `auth_token` plus `X-User-Id` via config/input `user_id`.

Provider docs:

- [API docs](https://developer.rocket.chat/apidocs)
- [Authentication](https://developer.rocket.chat/apidocs/authentication-api)
- [Personal access tokens](https://docs.rocket.chat/docs/manage-personal-access-tokens)

Terms: [https://www.rocket.chat/terms](https://www.rocket.chat/terms)

Every authenticated request sends both headers. `user_id` is interpolated into spec-level `extraHeaders: { "X-User-Id": "{user_id}" }`. A personal access token belongs in `auth_token`; do not put it on INPUT.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the workspace host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `post-message` | POST | `{host}/api/v1/chat.postMessage` | Official HTTP |
| `send-dm` | POST | `{host}/api/v1/chat.postMessage` | Official HTTP |
| `list-channels` | GET | `{host}/api/v1/channels.list` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `auth_token` (password secret), `host`, `user_id` (required, `X-User-Id`), `timeout_seconds` (default 25).

## Security & legal

Official API only. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
