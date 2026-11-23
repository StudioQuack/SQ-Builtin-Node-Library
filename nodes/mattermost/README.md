# Mattermost

In-process StudioQuack builtin (`templateId` `2a7bc52f-4102-5f41-9bd7-68e35a7ef438`, override with `SQ_TEMPLATE_ID_MATTERMOST`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Mattermost REST API v4** at `https://`. Auth: `bearer` via `access_token`.

Provider docs: [https://api.mattermost.com/](https://api.mattermost.com/)

Terms: [https://mattermost.com/terms/](https://mattermost.com/terms/)

Customer-hosted. PAT needs post:channel (admin for user ops). listen = outgoing webhooks / WebSocket is not opened — BPMN message events.

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
| `create-channel` | POST | `{host}/api/v4/channels` | Official HTTP |
| `add-channel-member` | POST | `{host}/api/v4/channels/{channel_id}/members` | Official HTTP |
| `delete-channel` | DELETE | `{host}/api/v4/channels/{channel_id}` | Official HTTP |
| `restore-channel` | POST | `{host}/api/v4/channels/{channel_id}/restore` | Official HTTP |
| `search-channel` | POST | `{host}/api/v4/channels/search` | Official HTTP |
| `get-channel-stats` | GET | `{host}/api/v4/channels/{channel_id}/stats` | Official HTTP |
| `list-channel-members` | GET | `{host}/api/v4/channels/{channel_id}/members` | Official HTTP |
| `post-message` | POST | `{host}/api/v4/posts` | Official HTTP |
| `post-ephemeral` | POST | `{host}/api/v4/posts/ephemeral` | Official HTTP |
| `delete-post` | DELETE | `{host}/api/v4/posts/{post_id}` | Official HTTP |
| `add-reaction` | POST | `{host}/api/v4/reactions` | Official HTTP |
| `remove-reaction` | DELETE | `{host}/api/v4/users/{user_id}/posts/{post_id}/reactions/{emoji_name}` | Official HTTP |
| `list-reactions` | GET | `{host}/api/v4/posts/{post_id}/reactions` | Official HTTP |
| `create-user` | POST | `{host}/api/v4/users` | Official HTTP |
| `deactivate-user` | DELETE | `{host}/api/v4/users/{user_id}` | Official HTTP |
| `list-users` | GET | `{host}/api/v4/users` | Official HTTP |
| `get-user-by-email` | GET | `{host}/api/v4/users/email/{email}` | Official HTTP |
| `get-user` | GET | `{host}/api/v4/users/{user_id}` | Official HTTP |
| `invite-user` | POST | `{host}/api/v4/teams/{team_id}/invite/email` | Official HTTP |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `access_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Customer-hosted. PAT needs post:channel (admin for user ops). listen = outgoing webhooks / WebSocket is not opened — BPMN message events. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
