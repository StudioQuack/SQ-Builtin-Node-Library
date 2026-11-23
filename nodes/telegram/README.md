# Telegram

In-process StudioQuack builtin (`templateId` `6fefe152-8f48-5823-a963-84c780622a87`, override with `SQ_TEMPLATE_ID_TELEGRAM`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Telegram Bot API** at `https://api.telegram.org`. Auth: `none` via `bot_token`.

Provider docs: [https://core.telegram.org/bots/api](https://core.telegram.org/bots/api)

Terms: [https://telegram.org/tos/bot-developers](https://telegram.org/tos/bot-developers)

Canonical messaging node; social-media.md is discovery only — same folder nodes/telegram. listen = setWebhook HTTPS or getUpdates, delivered as a BPMN message event. secret_token header for webhook verify.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/bot{bot_token}/sendMessage` | Official HTTP |
| `send-photo` | POST | `/bot{bot_token}/sendPhoto` | Official HTTP |
| `send-document` | POST | `/bot{bot_token}/sendDocument` | Official HTTP |
| `send-audio` | POST | `/bot{bot_token}/sendAudio` | Official HTTP |
| `send-video` | POST | `/bot{bot_token}/sendVideo` | Official HTTP |
| `send-animation` | POST | `/bot{bot_token}/sendAnimation` | Official HTTP |
| `send-sticker` | POST | `/bot{bot_token}/sendSticker` | Official HTTP |
| `send-location` | POST | `/bot{bot_token}/sendLocation` | Official HTTP |
| `send-media-group` | POST | `/bot{bot_token}/sendMediaGroup` | Official HTTP |
| `send-chat-action` | POST | `/bot{bot_token}/sendChatAction` | Official HTTP |
| `edit-message` | POST | `/bot{bot_token}/editMessageText` | Official HTTP |
| `delete-message` | POST | `/bot{bot_token}/deleteMessage` | Official HTTP |
| `pin-message` | POST | `/bot{bot_token}/pinChatMessage` | Official HTTP |
| `unpin-message` | POST | `/bot{bot_token}/unpinChatMessage` | Official HTTP |
| `get-file` | GET | `/bot{bot_token}/getFile` | Official HTTP |
| `get-chat` | GET | `/bot{bot_token}/getChat` | Official HTTP |
| `get-chat-administrators` | GET | `/bot{bot_token}/getChatAdministrators` | Official HTTP |
| `get-chat-member` | GET | `/bot{bot_token}/getChatMember` | Official HTTP |
| `leave-chat` | POST | `/bot{bot_token}/leaveChat` | Official HTTP |
| `set-chat-title` | POST | `/bot{bot_token}/setChatTitle` | Official HTTP |
| `set-chat-description` | POST | `/bot{bot_token}/setChatDescription` | Official HTTP |
| `answer-callback-query` | POST | `/bot{bot_token}/answerCallbackQuery` | Official HTTP |
| `answer-inline-query` | POST | `/bot{bot_token}/answerInlineQuery` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `bot_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Canonical messaging node; social-media.md is discovery only — same folder nodes/telegram. listen = setWebhook HTTPS or getUpdates, delivered as a BPMN message event. secret_token header for webhook verify. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
