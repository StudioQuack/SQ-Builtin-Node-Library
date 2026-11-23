# Mailgun

In-process StudioQuack builtin (`templateId` `5b5ebf8b-e6d8-5b4a-9924-d02a740ef1a7`, override with `SQ_TEMPLATE_ID_MAILGUN`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Mailgun API v3** at `https://{region}` (config `region`: `api.mailgun.net` US, `api.eu.mailgun.net` EU, `api.au.mailgun.net` AU). Auth: `basic` via `api_key` (username `api`).

Provider docs: [Send an email](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages)

Terms: [https://www.mailgun.com/legal/terms/](https://www.mailgun.com/legal/terms/)

Pick the `region` host the sending domain was created in. Basic auth username is the literal `api`.

`send-email` uses `bodyFormat: multipart` (`multipart/form-data`) as Mailgun documents. Pass one of `text`, `html`, `amp-html`, or `template`. File attachments go in `attachment` as the platform file list `[{kind, mime_type, source, data|url, name}]` — one multipart part per item (a single ContentBlock and the legacy `{dataBase64|url, fileName, contentType}` map are still accepted). Send options starting with `o:`, `h:`, `v:`, or `t:` are limited to 16KB total.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. Attachments are `attachment`. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-email` | POST | `/v3/{domain}/messages` | multipart/form-data |
| `get-event` | GET | `/v3/{domain}/events` | Official HTTP |
| `list-events` | GET | `/v3/{domain}/events` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `api_key` (password secret), `api_user` (default `api`), `domain`, `region` (API host select, default `api.mailgun.net`), `timeout_seconds` (default 25).

## Security & legal

Official API only. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
