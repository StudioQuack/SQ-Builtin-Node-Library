# Aircall

In-process StudioQuack builtin (`templateId` `c87b365e-9829-5f20-9917-d9cfcbb32c19`, override with `SQ_TEMPLATE_ID_AIRCALL`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Aircall Public API v1** at `https://api.aircall.io/v1`. Auth: `basic` via `api_id`:`api_token`.

Provider docs:

- [API reference](https://developer.aircall.io/api-references/)
- [Send and receive SMS (proxy mode)](https://developer.aircall.io/docs/send-and-receive-sms-proxy-mode)
- [SMS overview](https://developer.aircall.io/docs/sms)

Terms: [https://aircall.io/terms/](https://aircall.io/terms/)

Call recordings are personal data and may require two-party consent. Messaging via API is plan-gated (Professional). listen = Aircall webhooks as BPMN message events.

## `send-message` path

`send-message` is **`POST /v1/numbers/{id}/messages/send`** (not `POST /messages`).

- Path `{id}` is the **Aircall number id** (the SMS-capable line), not the user id used by `start-call`.
- Body: `to` (E.164), `body` (text), optional `mediaUrl`.
- The number must first be configured for API messaging (`POST /v1/numbers/:id/messages/configuration`). Configuring a number for this proxy/send path hides it from the Aircall inbox. The agent-conversation variant is `POST /v1/numbers/:id/messages/native/send` and is not a separate operation here.

`start-call` remains `POST /v1/users/{id}/calls`.

`transfer-call` takes exactly one target: `user_id`, `team_id` (optional `dispatching_strategy`) or external `number` (E.164). `tag-call` sends `tags` as an array of tag IDs (`[545]`). `create-contact` requires `phone_numbers`; `create-user` requires `email`, `first_name` and `last_name`.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `start-call` | POST | `/users/{id}/calls` | Official HTTP |
| `transfer-call` | POST | `/calls/{id}/transfers` | Official HTTP |
| `comment-call` | POST | `/calls/{id}/comments` | Official HTTP |
| `tag-call` | POST | `/calls/{id}/tags` | Official HTTP |
| `archive-call` | PUT | `/calls/{id}/archive` | Official HTTP |
| `get-call` | GET | `/calls/{id}` | Official HTTP |
| `search-calls` | GET | `/calls` | Official HTTP |
| `create-contact` | POST | `/contacts` | Official HTTP |
| `update-contact` | POST | `/contacts/{id}` | Official HTTP |
| `delete-contact` | DELETE | `/contacts/{id}` | Official HTTP |
| `send-message` | POST | `/numbers/{id}/messages/send` | Official send (skipping inbox) |
| `create-user` | POST | `/users` | Official HTTP |
| `update-user` | PUT | `/users/{id}` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `api_token` (password secret), `api_id` (Basic username), `timeout_seconds` (default 25).

## Security & legal

Official API only. Call recordings are personal data and may require two-party consent. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
