# Twilio

In-process StudioQuack builtin (`templateId` `2f1b746a-fa8e-52f9-b8de-5ff3bc6c19fe`, override with `SQ_TEMPLATE_ID_TWILIO`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Twilio Programmable Messaging + Voice (2010-04-01)** at `https://api.twilio.com/2010-04-01`. Auth: `basic` via `auth_token`.

Provider docs: [https://www.twilio.com/docs/messaging/api/message-resource](https://www.twilio.com/docs/messaging/api/message-resource)

Terms: [https://www.twilio.com/legal/tos](https://www.twilio.com/legal/tos)

A2P 10DLC / TCPA / GDPR apply to SMS. send-whatsapp is Twilio's WhatsApp channel — not graph.facebook.com (see nodes/whatsapp). listen = inbound + status callbacks as BPMN message events.

`To` / `From` are validated as E.164 (or `whatsapp:+E.164` where applicable); `MediaUrl` must be http(s). `account_sid` must look like `AC` + 32 hex digits before any REST call.

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
| `send-sms` | POST | `/Accounts/{account_sid}/Messages.json` | Official HTTP |
| `send-mms` | POST | `/Accounts/{account_sid}/Messages.json` | Official HTTP |
| `send-whatsapp` | POST | `/Accounts/{account_sid}/Messages.json` | Official HTTP |
| `make-call-tts` | POST | `/Accounts/{account_sid}/Calls.json` | Official HTTP |
| `get-message` | GET | `/Accounts/{account_sid}/Messages/{MessageSid}.json` | Official HTTP |
| `list-messages` | GET | `/Accounts/{account_sid}/Messages.json` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `auth_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. A2P 10DLC / TCPA / GDPR apply to SMS. send-whatsapp is Twilio's WhatsApp channel — not graph.facebook.com (see nodes/whatsapp). listen = inbound + status callbacks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
