# SendGrid

In-process StudioQuack builtin (`templateId` `c7ad0508-ce1b-5536-8a88-c17b2e916bff`, override with `SQ_TEMPLATE_ID_SENDGRID`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Twilio SendGrid Web API v3** at `https://api.sendgrid.com`. Auth: `bearer` via `api_key`.

Provider docs: [https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send)

Terms: [https://www.twilio.com/legal/sendgrid/terms-of-service](https://www.twilio.com/legal/sendgrid/terms-of-service)

CAN-SPAM. Verified sender/domain required. Marketing contacts are personal data. listen = Event Webhook as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. Mail attachments go in `attachments`. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-email` | POST | `/v3/mail/send` | Flat `to`/`cc`/`bcc`/`from`/`from_name`/`subject`/`text`/`html` nest into `personalizations[0]`, `from.email`, `content[0]|[1]`. `text_type`/`html_type` config defaults (`text/plain`, `text/html`). Keep `personalizations`/`content` as one-object escape hatches. |
| `upsert-contact` | PUT | `/v3/marketing/contacts` | Required: contacts[{email}]. Optional list_ids |
| `get-contact` | GET | `/v3/marketing/contacts/{id}` | Official HTTP |
| `list-contacts` | GET | `/v3/marketing/contacts` | Official HTTP |
| `delete-contact` | DELETE | `/v3/marketing/contacts` | Official HTTP |
| `create-list` | POST | `/v3/marketing/lists` | Official HTTP |
| `get-list` | GET | `/v3/marketing/lists/{id}` | Official HTTP |
| `list-lists` | GET | `/v3/marketing/lists` | Official HTTP |
| `update-list` | PATCH | `/v3/marketing/lists/{id}` | Official HTTP |
| `delete-list` | DELETE | `/v3/marketing/lists/{id}` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | send-email | no | Default operation when INPUT omits operation. EXACT: send-email, upsert-contact, get-contact, list-contacts, delete-contact, create-list, get-list, list-lists, update-list, dele... |
| `api_key` | password | yes |  | yes | SendGrid API key. Header Authorization: Bearer. StudioQuack secret. Never log. Never put on INPUT. |
| `from` | string | no |  | no | Default verified sender email for send-email (from.email) when INPUT omits from. |
| `text_type` | string | no | text/plain | no | Default MIME type for send-email content[0]. Official text/plain. |
| `html_type` | string | no | text/html | no | Default MIME type for send-email content[1]. Official text/html. |
| `content` | object | no |  | no | send-email one content-part object escape hatch {type,value} merged into content[0]. Prefer text/html purpose fields (types from config defaults). Example: {"type":"text/plain",... |
| `attachments` | list | no |  | no | send-email attachments [{content (base64), filename, type, disposition}]. Do not log content. |
| `from_name` | string | no |  | no | send-email from display name. Wired to from.name. Example: Ada. |
| `to` | string | no |  | no | send-email recipient email. Wired to personalizations[0].to[0].email. Required unless using the personalizations object escape hatch. Example: bob@example.com. |
| `text` | string | no |  | no | send-email plain body. Wired to content[0].value. content[0].type comes from config text_type (default text/plain). HTML-only: set text too or use the content escape hatch — map... |
| `mail_settings` | object | no |  | no | send-email mail_settings object. |
| `cc` | string | no |  | no | send-email CC email. Wired to personalizations[0].cc[0].email. Example: cc@example.com. |
| `personalizations` | object | no |  | no | send-email personalizations object escape hatch (first item). Extra keys merge into personalizations[0]; named to/cc/bcc win. For many recipients send this as one personalizatio... |
| `categories` | list | no |  | no | send-email categories (strings, max 10). |
| `tracking_settings` | object | no |  | no | send-email tracking_settings object. |
| `html` | string | no |  | no | send-email HTML body. Wired to content[1].value. content[1].type comes from config html_type (default text/html). |
| `subject` | string | no |  | no | send-email subject (root). Example: Your receipt. |
| `reply_to` | object | no |  | no | send-email reply_to {email, name}. |
| `bcc` | string | no |  | no | send-email BCC email. Wired to personalizations[0].bcc[0].email. Example: bcc@example.com. |
| `template_id` | string | no |  | no | Dynamic template id for send-email. When set, content may be omitted; use personalizations.dynamic_template_data. |
| `send_at` | number | no |  | no | send-email unix send_at (not more than 72h). |
| `asm` | object | no |  | no | send-email ASM group {group_id, groups_to_display}. |
| `list_ids` | list | no |  | no | upsert-contact list_ids to add the contacts to. UUID strings. |
| `contacts` | list | no |  | no | upsert-contact contacts array (required). Each: email (required), first_name, last_name, custom_fields. |
| `id` | string | no |  | no | Contact or list UUID. Purpose: operation input. Omit when unused. |
| `ids` | string | no |  | no | Comma-separated contact UUIDs for delete-contact. Purpose: operation input. Omit when unused. |
| `name` | string | no |  | no | Marketing list name. Purpose: operation input. Omit when unused. |
| `page_token` | string | no |  | no | Query-string parameter `page_token` for operation: list-lists. |
| `page_size` | number | no |  | no | Query-string parameter `page_size` for operation: list-lists. |
| `delete_contacts` | string | no |  | no | Query-string parameter `delete_contacts` for operation: delete-list. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for each official HTTPS call. Positive integer. Default 25. 0/blank → 25. |

## Security & legal

Official API only. CAN-SPAM. Verified sender/domain required. Marketing contacts are personal data. listen = Event Webhook as BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
