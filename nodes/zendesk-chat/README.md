# Zendesk Chat

In-process StudioQuack builtin (`templateId` `cbad184c-fbda-5c16-86de-9e4ebf38f93e`, override with `SQ_TEMPLATE_ID_ZENDESK_CHAT`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Zendesk Sunshine Conversations API** at `https://api.smooch.io/v2`. Auth: `basic` via `api_secret`.

Provider docs: [https://developer.zendesk.com/api-reference/conversations/introduction/](https://developer.zendesk.com/api-reference/conversations/introduction/)

Terms: [https://www.zendesk.com/company/agreements-and-terms/](https://www.zendesk.com/company/agreements-and-terms/)

This is Zendesk Messaging / Sunshine Conversations — not the Support ticketing API (other category). The Zendesk Chat product sunsets 2028-01-31 (https://support.zendesk.com/hc/en-us/articles/4408843026714). Legacy Chat REST is retired; this host is api.smooch.io/v2. listen = webhooks as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/apps/{app_id}/conversations/{conversation_id}/messages` | Official HTTP |
| `get-conversation` | GET | `/apps/{app_id}/conversations/{conversation_id}` | Official HTTP |
| `list-conversations` | GET | `/apps/{app_id}/conversations` | Official HTTP |
| `update-visitor` | PATCH | `/apps/{app_id}/users/{user_id}` | Official HTTP |
| `list-agents` | GET | `/apps/{app_id}/integrations` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | send-message | no | Default operation when INPUT omits operation. EXACT: send-message, get-conversation, list-conversations, update-visitor, list-agents, listen. listen/send-and-wait instruct the o... |
| `api_secret` | password | yes |  | yes | Sunshine key id + secret (HTTP Basic). StudioQuack secret. Never log. Never put on INPUT. |
| `api_key_id` | string | yes |  | no | Sunshine Conversations / Zendesk Messaging key id (HTTP Basic username / JWT kid). Not the secret; pair with the password config. Never log the secret. |
| `app_id` | string | yes |  | no | Sunshine Conversations app id. Path {appId}. |
| `conversation_id` | string | no |  | no | Sunshine conversation id. Purpose: operation input. Omit when unused. |
| `author` | string | no |  | no | Message author {type, userId?}. send-message. Example: {"type":"business"}. |
| `type` | string | no |  | no | Sunshine content type for send-message. EXACT: text / image / file / location / carousel / list / form / formResponse. Nested as content.type. Default text. |
| `text` | string | no |  | no | Message text for send-message. Nested as content.text. Example: Hello. https://developer.zendesk.com/api-reference/conversations/conversations-api/messages/ |
| `page[size]` | string | no |  | no | Query-string parameter `page[size]` for operation: list-conversations. |
| `filter` | string | no |  | no | Query-string parameter `filter` for operation: list-conversations. |
| `user_id` | string | no |  | no | User id for update-visitor. Purpose: operation input. Omit when unused. |
| `givenName` | string | no |  | no | Request-body parameter `givenName` for operation: update-visitor. |
| `email` | string | no |  | no | Request-body parameter `email` for operation: update-visitor. |
| `surname` | string | no |  | no | Request-body parameter `surname` for operation: update-visitor. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for each official HTTPS call. Positive integer. Default 25. 0/blank → 25. |

## Security & legal

Official API only. This is Zendesk Messaging / Sunshine Conversations — not the Support ticketing API (other category). The Zendesk Chat product sunsets 2028-01-31 (https://support.zendesk.com/hc/en-us/articles/4408843026714). Legacy Chat REST is retired; this host is api.smooch.io/v2. listen = webhooks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
