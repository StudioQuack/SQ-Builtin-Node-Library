# WhatsApp

In-process StudioQuack builtin (`templateId` `6563db61-8bd4-565d-ac0e-52cc5914a742`, override with `SQ_TEMPLATE_ID_WHATSAPP`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official WhatsApp Business Cloud API (Meta Graph v26.0)** at `https://graph.facebook.com/v26.0`. Auth: `bearer` via `access_token`.

Provider docs: [https://developers.facebook.com/docs/whatsapp/cloud-api/overview/](https://developers.facebook.com/docs/whatsapp/cloud-api/overview/)

Terms: [https://www.whatsapp.com/legal/business-policy](https://www.whatsapp.com/legal/business-policy)

Canonical Cloud API node; social-media.md is discovery only — same folder nodes/whatsapp. DO NOT BUILD unofficial WhatsApp Web / Baileys / session scrape. 24h customer-care window vs templates. Twilio WhatsApp is the twilio node. listen = Meta webhooks as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. Media fields use type file. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/{phone_number_id}/messages` | Official HTTP |
| `send-template` | POST | `/{phone_number_id}/messages` | Official HTTP; `template_name` + `language` map to `template.name` / `template.language.code` |
| `upload-media` | POST | `/{phone_number_id}/media` | Official HTTP |
| `download-media` | GET | `/{media_id}` | Official HTTP |
| `delete-media` | DELETE | `/{media_id}` | Official HTTP |
| `send-and-wait` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Canonical Cloud API node; social-media.md is discovery only — same folder nodes/whatsapp. DO NOT BUILD unofficial WhatsApp Web / Baileys / session scrape. 24h customer-care window vs templates. Twilio WhatsApp is the twilio node. listen = Meta webhooks as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
