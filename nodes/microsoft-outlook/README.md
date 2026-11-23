# Microsoft Outlook

In-process StudioQuack builtin (`templateId` `efdfe68d-d27b-598a-8628-e147821bcffd`, override with `SQ_TEMPLATE_ID_MICROSOFT_OUTLOOK`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Microsoft Graph v1.0 mail/calendar/contacts** at `https://graph.microsoft.com/v1.0`. Auth: `bearer` via `access_token`.

Provider docs:

- [Mail API overview](https://learn.microsoft.com/en-us/graph/api/resources/mail-api-overview)
- [user: sendMail](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0)

Terms: [https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use](https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use)

Graph v1.0 only (no beta). Mailbox data is personal data. send-and-wait and listen are BPMN message events, not Graph RPCs.

## Delegated `/me` vs app-only `/users/{id}`

This node keeps **`/me/...` paths**. That is the delegated (signed-in user) contract.

| Auth | Path this node uses | What Graph requires |
|---|---|---|
| Delegated (authorization code / on-behalf-of) | `/me/sendMail`, `/me/messages`, `/me/mailFolders`, `/me/calendars`, `/me/contacts` | Valid. `/me` is the signed-in user. |
| App-only (client credentials) | **Not swapped.** Still `/me/...` | **Fails.** Graph returns 400: `/me request is only valid with delegated authentication flow.` App-only must call `POST /users/{id\|userPrincipalName}/sendMail` (and the same substitution on every other path). |

App-only also needs application permission `Mail.Send` (and `Mail.ReadWrite` / `Calendars.ReadWrite` / `Contacts.ReadWrite` as needed) plus admin consent. Restrict which mailboxes the app can act on with an Exchange **application access policy**. This node does **not** rewrite `/me` to `/users/{id}` — use a delegated token, or a different client that targets `/users/{id}`.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. |

FUNCTION ports: none.

## Send / draft fields

`send-message` takes `subject`, `body:{contentType,content}`, `toRecipients`, `ccRecipients`, `bccRecipients` (mapped into the official Graph `message` object; a full `message` object on INPUT is merged with them) plus `saveToSentItems`. `create-draft` / `update-draft` take `subject`, `body`, `toRecipients`, `ccRecipients`, `bccRecipients` at the message root. Docs: https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/me/sendMail` | Delegated `/me` |
| `reply-message` | POST | `/me/messages/{id}/reply` | Official HTTP |
| `get-message` | GET | `/me/messages/{id}` | Official HTTP |
| `list-messages` | GET | `/me/messages` | Official HTTP |
| `update-message` | PATCH | `/me/messages/{id}` | Official HTTP |
| `delete-message` | DELETE | `/me/messages/{id}` | Official HTTP |
| `move-message` | POST | `/me/messages/{id}/move` | Official HTTP |
| `create-draft` | POST | `/me/messages` | Official HTTP |
| `send-draft` | POST | `/me/messages/{id}/send` | Official HTTP |
| `get-draft` | GET | `/me/messages/{id}` | Official HTTP |
| `update-draft` | PATCH | `/me/messages/{id}` | Official HTTP |
| `delete-draft` | DELETE | `/me/messages/{id}` | Official HTTP |
| `add-attachment` | POST | `/me/messages/{id}/attachments` | Official HTTP |
| `get-attachment` | GET | `/me/messages/{id}/attachments/{attachment_id}` | Official HTTP |
| `list-attachments` | GET | `/me/messages/{id}/attachments` | Official HTTP |
| `download-attachment` | GET | `/me/messages/{id}/attachments/{attachment_id}/$value` | Official HTTP |
| `create-folder` | POST | `/me/mailFolders` | Official HTTP |
| `get-folder` | GET | `/me/mailFolders/{id}` | Official HTTP |
| `list-folders` | GET | `/me/mailFolders` | Official HTTP |
| `update-folder` | PATCH | `/me/mailFolders/{id}` | Official HTTP |
| `delete-folder` | DELETE | `/me/mailFolders/{id}` | Official HTTP |
| `create-calendar` | POST | `/me/calendars` | Official HTTP |
| `get-calendar` | GET | `/me/calendars/{id}` | Official HTTP |
| `list-calendars` | GET | `/me/calendars` | Official HTTP |
| `update-calendar` | PATCH | `/me/calendars/{id}` | Official HTTP |
| `delete-calendar` | DELETE | `/me/calendars/{id}` | Official HTTP |
| `create-contact` | POST | `/me/contacts` | Official HTTP |
| `get-contact` | GET | `/me/contacts/{id}` | Official HTTP |
| `list-contacts` | GET | `/me/contacts` | Official HTTP |
| `update-contact` | PATCH | `/me/contacts/{id}` | Official HTTP |
| `delete-contact` | DELETE | `/me/contacts/{id}` | Official HTTP |
| `send-and-wait` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Mailbox data is personal data. Admin consent for app-only (which this node does not path-swap). Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
