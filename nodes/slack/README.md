# Slack

In-process StudioQuack builtin (`templateId` `c007907c-572b-5646-92b5-2c3a8bb540de`, override with `SQ_TEMPLATE_ID_SLACK`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Slack Web API + Events API** at `https://slack.com/api`. Auth: `bearer` via `bot_token`.

Provider docs: [https://docs.slack.dev/reference/methods/chat.postMessage](https://docs.slack.dev/reference/methods/chat.postMessage)

Terms: [https://slack.com/terms-of-service/api](https://slack.com/terms-of-service/api)

Official Web API. Do not call retired files.upload (sunset 2025-11-12); upload-file starts files.getUploadURLExternal. stars.add / stars.remove / stars.list are deprecated (https://docs.slack.dev/reference/methods/stars.add) — kept for backward compatibility. Incoming webhook URLs are send-only. listen = Events API verified with signing secret, delivered as a BPMN message event.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. File references are Slack file IDs (`file_id`). |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/chat.postMessage` | Official HTTP |
| `update-message` | POST | `/chat.update` | Official HTTP |
| `delete-message` | POST | `/chat.delete` | Official HTTP |
| `get-permalink` | GET | `/chat.getPermalink` | Official HTTP |
| `search-messages` | GET | `/search.messages` | Official HTTP |
| `archive-channel` | POST | `/conversations.archive` | Official HTTP |
| `close-channel` | POST | `/conversations.close` | Official HTTP |
| `create-channel` | POST | `/conversations.create` | Official HTTP |
| `get-channel` | GET | `/conversations.info` | Official HTTP |
| `list-channels` | GET | `/conversations.list` | Official HTTP |
| `channel-history` | GET | `/conversations.history` | Official HTTP |
| `invite-channel` | POST | `/conversations.invite` | Official HTTP |
| `join-channel` | POST | `/conversations.join` | Official HTTP |
| `kick-channel` | POST | `/conversations.kick` | Official HTTP |
| `leave-channel` | POST | `/conversations.leave` | Official HTTP |
| `list-channel-members` | GET | `/conversations.members` | Official HTTP |
| `open-dm` | POST | `/conversations.open` | Official HTTP |
| `rename-channel` | POST | `/conversations.rename` | Official HTTP |
| `get-replies` | GET | `/conversations.replies` | Official HTTP |
| `set-purpose` | POST | `/conversations.setPurpose` | Official HTTP |
| `set-topic` | POST | `/conversations.setTopic` | Official HTTP |
| `unarchive-channel` | POST | `/conversations.unarchive` | Official HTTP |
| `upload-file` | POST | `/files.getUploadURLExternal` | Official HTTP |
| `get-file` | GET | `/files.info` | Official HTTP; INPUT `file_id` is sent as query param `file` (https://docs.slack.dev/reference/methods/files.info) |
| `list-files` | GET | `/files.list` | Official HTTP |
| `add-reaction` | POST | `/reactions.add` | Official HTTP |
| `remove-reaction` | POST | `/reactions.remove` | Official HTTP |
| `list-reactions` | GET | `/reactions.list` | Official HTTP |
| `add-star` | POST | `/stars.add` | Deprecated (https://docs.slack.dev/reference/methods/stars.add); kept for compatibility |
| `remove-star` | POST | `/stars.remove` | Deprecated (https://docs.slack.dev/reference/methods/stars.remove); kept for compatibility |
| `list-stars` | GET | `/stars.list` | Deprecated (https://docs.slack.dev/reference/methods/stars.list); kept for compatibility |
| `get-user` | GET | `/users.info` | Official HTTP |
| `list-users` | GET | `/users.list` | Official HTTP |
| `update-user-profile` | POST | `/users.profile.set` | Official HTTP |
| `create-usergroup` | POST | `/usergroups.create` | Official HTTP |
| `list-usergroups` | GET | `/usergroups.list` | Official HTTP |
| `update-usergroup` | POST | `/usergroups.update` | Official HTTP |
| `send-and-wait` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `bot_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Official Web API. Do not call retired files.upload (sunset 2025-11-12); upload-file starts files.getUploadURLExternal. stars.add / stars.remove / stars.list are deprecated (https://docs.slack.dev/reference/methods/stars.add) — kept for backward compatibility. Incoming webhook URLs are send-only. listen = Events API verified with signing secret, delivered as a BPMN message event. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
