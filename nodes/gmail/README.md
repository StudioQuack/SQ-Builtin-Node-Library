# Gmail

In-process StudioQuack builtin (`templateId` `6565547f-cf65-5ba6-bfd6-f5cb2c57d8f2`, override with `SQ_TEMPLATE_ID_GMAIL`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Google Gmail API v1** at `https://gmail.googleapis.com/gmail/v1`. Auth: `bearer` via `access_token`.

Provider docs: [https://developers.google.com/gmail/api/reference/rest](https://developers.google.com/gmail/api/reference/rest)

Terms: [https://developers.google.com/terms](https://developers.google.com/terms)

Restricted Gmail scopes need Google CASA/verification. Never log message bodies. listen = users.watch + Pub/Sub delivered as a BPMN message event — not a socket on this node. Cross-ref none.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Send/reply/draft take `to`, `from`, `cc`, `bcc`, `reply_to`, `subject`, `text`, `html`, `in_reply_to`, `references`, `attachments` and the node builds `raw`; a prebuilt `raw` is sent as-is. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `{error}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/users/{user_id}/messages/send` | Official HTTP |
| `reply-message` | POST | `/users/{user_id}/messages/send` | Official HTTP |
| `get-message` | GET | `/users/{user_id}/messages/{id}` | Official HTTP |
| `list-messages` | GET | `/users/{user_id}/messages` | Official HTTP |
| `delete-message` | DELETE | `/users/{user_id}/messages/{id}` | Official HTTP |
| `mark-read` | POST | `/users/{user_id}/messages/{id}/modify` | Official HTTP |
| `mark-unread` | POST | `/users/{user_id}/messages/{id}/modify` | Official HTTP |
| `add-label-message` | POST | `/users/{user_id}/messages/{id}/modify` | Official HTTP |
| `remove-label-message` | POST | `/users/{user_id}/messages/{id}/modify` | Official HTTP |
| `create-draft` | POST | `/users/{user_id}/drafts` | Official HTTP |
| `get-draft` | GET | `/users/{user_id}/drafts/{id}` | Official HTTP |
| `list-drafts` | GET | `/users/{user_id}/drafts` | Official HTTP |
| `delete-draft` | DELETE | `/users/{user_id}/drafts/{id}` | Official HTTP |
| `create-label` | POST | `/users/{user_id}/labels` | Official HTTP |
| `get-label` | GET | `/users/{user_id}/labels/{id}` | Official HTTP |
| `list-labels` | GET | `/users/{user_id}/labels` | Official HTTP |
| `delete-label` | DELETE | `/users/{user_id}/labels/{id}` | Official HTTP |
| `get-thread` | GET | `/users/{user_id}/threads/{id}` | Official HTTP |
| `list-threads` | GET | `/users/{user_id}/threads` | Official HTTP |
| `reply-thread` | POST | `/users/{user_id}/messages/send` | Official HTTP |
| `trash-thread` | POST | `/users/{user_id}/threads/{id}/trash` | Official HTTP |
| `untrash-thread` | POST | `/users/{user_id}/threads/{id}/untrash` | Official HTTP |
| `delete-thread` | DELETE | `/users/{user_id}/threads/{id}` | Official HTTP |
| `add-label-thread` | POST | `/users/{user_id}/threads/{id}/modify` | Official HTTP |
| `remove-label-thread` | POST | `/users/{user_id}/threads/{id}/modify` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | send-message | no | Default operation when INPUT omits operation. EXACT: send-message, reply-message, get-message, list-messages, delete-message, mark-read, mark-unread, add-label-message, remove-l... |
| `access_token` | password | yes |  | yes | Google OAuth 2.0 access token with Gmail scopes (gmail.send / gmail.modify / gmail.readonly as needed). Header Authorization: Bearer. StudioQuack secret. Never on INPUT. Refresh... |
| `user_id` | string | no | me | no | Gmail userId. Default me. Accepted: me or the mailbox email. |
| `from` | string | no |  | no | Sender address used when INPUT from is empty. Must be the account or a verified send-as alias. Example: Ada <ada@example.com>. |
| `raw` | string | no |  | no | Optional RFC 2822 message as Gmail raw (URL-safe base64), including any attachments. Sent only when INPUT raw is empty, on send-message, reply-message, reply-thread, and create-... |
| `html` | string | no |  | no | HTML body. Omit for plain-text mail. |
| `cc` | string | no |  | no | Cc recipients, comma-separated. Omit when unused. |
| `subject` | string | no |  | no | Message subject. For replies keep the original subject (optionally prefixed with Re:) so Gmail threads it. |
| `attachments` | list | no |  | no | Files to attach: list of StudioQuack file blocks {mime_type, source, data, name}. Up to 25. |
| `threadId` | string | no |  | no | Thread id for reply-message / reply-thread / create-draft. Purpose: operation input. Omit when unused. |
| `text` | string | no |  | no | Plain-text body. When html is also set the message is multipart/alternative. |
| `reply_to` | string | no |  | no | Reply-To address(es), comma-separated. Omit when unused. |
| `bcc` | string | no |  | no | Bcc recipients, comma-separated. Omit when unused. |
| `to` | string | no |  | no | Recipients for send-message, reply-message, reply-thread and create-draft. Comma-separated addresses. Example: ada@example.com, Bob <bob@example.com>. Required unless raw is set. |
| `in_reply_to` | string | no |  | no | Message-ID header of the message being answered (reply-message / reply-thread), e.g. <CAF...@mail.gmail.com>. Use with threadId so the reply stays in the thread. |
| `references` | string | no |  | no | References header for replies: the Message-IDs of the thread, space-separated. Defaults to in_reply_to when empty. |
| `id` | string | no |  | no | Gmail message, draft, label, or thread id for get/delete/modify. Purpose: operation input. Omit when unused. |
| `format` | string | no |  | no | Gmail format: full / metadata / minimal / raw. Default full. Purpose: operation input. Omit when unused. |
| `labelIds` | string | no |  | no | Label id filter for list-messages and list-threads. Sent as one query value. Modify operations use addLabelIds and removeLabelIds, not this field. Example: INBOX. Purpose: opera... |
| `q` | string | no |  | no | Gmail search query for list-messages, list-threads, and list-drafts. Example: from:ada@example.com is:unread. Purpose: operation input. Omit when unused. |
| `maxResults` | number | no |  | no | Page size for list-messages, list-threads, and list-drafts. Positive integer. Gmail caps messages and threads at 500. Example: 20. Omit when unused. |
| `pageToken` | string | no |  | no | nextPageToken from a previous list-messages, list-threads, or list-drafts response. Example: 0123456789. Omit when unused. |
| `removeLabelIds` | list | no |  | no | Label ids to remove. JSON array of strings. mark-read uses ["UNREAD"]. remove-label-message and remove-label-thread use this list. Example: ["UNREAD"]. Omit when unused. |
| `addLabelIds` | list | no |  | no | Label ids to add. JSON array of strings. mark-unread uses ["UNREAD"]. add-label-message and add-label-thread use this list. Example: ["UNREAD"]. Omit when unused. |
| `messageListVisibility` | string | no |  | no | create-label only. EXACT: show / hide. Example: show. Omit when unused. |
| `labelListVisibility` | string | no |  | no | create-label only. EXACT: labelShow / labelShowIfUnread / labelHide. Example: labelShow. Omit when unused. |
| `name` | string | no |  | no | Label name for create-label. Purpose: operation input. Omit when unused. |
| `timeout_seconds` | number | no | 25 | no | Connect+read timeout for each official HTTPS call. Positive integer. Default 25. 0/blank → 25. |

## Security & legal

Official API only. Restricted Gmail scopes need Google CASA/verification. Never log message bodies. listen = users.watch + Pub/Sub delivered as a BPMN message event — not a socket on this node. Cross-ref none. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
