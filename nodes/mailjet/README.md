# Mailjet

In-process StudioQuack builtin (`templateId` `a9584408-1b61-54fb-8c0f-258ea5d97bac`, override with `SQ_TEMPLATE_ID_MAILJET`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Mailjet Email v3.1 + SMS v4** at `https://api.mailjet.com`. Email auth: `basic` via `api_key`:`api_secret`. SMS auth: Bearer `sms_token`.

Provider docs:

- [Send emails](https://dev.mailjet.com/email/reference/send-emails/)
- [SMS authentication](https://dev.mailjet.com/sms/reference/overview/authentication/)
- [Send SMS](https://dev.mailjet.com/sms/reference/send-message/)

Terms: [https://www.mailjet.com/legal/tos/](https://www.mailjet.com/legal/tos/)

## Dual auth

Email (`send-email`, `send-email-template`) uses HTTP Basic (`api_key` + `api_secret`).

`send-sms` (`POST /v4/sms-send`) **overrides** `Authorization` with `Bearer {sms_token}` (op-level extraHeader). Generate that token in the [SMS Dashboard](https://app.mailjet.com/sms). It is not the email API key. Email-only graphs may leave `sms_token` blank; SMS sends omit the Bearer header if the placeholder cannot resolve and then fail against Basic.

EU residency options. listen = event callbacks as BPMN message events.

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
| `send-email` | POST | `/v3.1/send` | `From`/`To`/`Subject`/`TextPart`/`HTMLPart` → `Messages[0].From.Email`, `Messages[0].To[0].Email`, … |
| `send-email-template` | POST | `/v3.1/send` | Same plus `TemplateID` / `Variables` |
| `send-sms` | POST | `/v4/sms-send` | Bearer `sms_token` |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `api_secret` (password), `api_key` (password, Basic username), `sms_token` (password, SMS only), `timeout_seconds` (default 25).

## Security & legal

Official API only. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
