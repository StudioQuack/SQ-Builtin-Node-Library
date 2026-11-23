# Vonage

In-process StudioQuack builtin (`templateId` `e05ddfa5-221f-5a10-a3a4-a1e6d9d3d7e9`, override with `SQ_TEMPLATE_ID_VONAGE`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Vonage Messages API** at `https://api.nexmo.com/v1`. Auth: `basic` via `api_key`:`api_secret`.

Provider docs: [Messages API reference](https://developer.vonage.com/en/api/messages)

Terms: [https://www.vonage.com/legal/](https://www.vonage.com/legal/)

Official Messages API (ex-Nexmo; `rest.nexmo.com` SMS API is the legacy SMS-only host). Local telecom rules apply. JWT application auth is an alternative the engine does not mint — supply Basic `api_key`:`api_secret`.

## Removed: `get-message`

The Messages API documents **two** HTTP operations:

- `POST /v1/messages` — send
- `PATCH /v1/messages/{message_uuid}` — update status (WhatsApp mark-as-read, RCS revoke)

There is **no GET** `/v1/messages/{uuid}`. `get-message` was removed. Delivery status arrives on inbound webhooks (`listen`), not by polling.

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
| `send-sms` | POST | `/messages` | Official send (`to` + `from` + `text`; `channel: sms` and `message_type: text` are fixed) |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

Removed: `get-message`.

## Configuration parameters

`operation` (select), `api_secret` (password), `api_key` (password; Basic username), `timeout_seconds` (default 25).

## Security & legal

Official API only. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
