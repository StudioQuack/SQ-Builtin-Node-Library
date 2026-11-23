# Discord

In-process StudioQuack builtin (`templateId` `aa0847f8-86b8-5c23-b795-f4ac7955540f`, override with `SQ_TEMPLATE_ID_DISCORD`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official Discord API v10** at `https://discord.com/api/v10`. Auth: `header` via `bot_token`.

Provider docs: [https://discord.com/developers/docs/reference](https://discord.com/developers/docs/reference)

Terms: [https://discord.com/developers/docs/policies-and-agreements/developer-policy](https://discord.com/developers/docs/policies-and-agreements/developer-policy)

Canonical messaging node; social-media.md is discovery only — same folder nodes/discord. Incoming webhooks are send-only (send-webhook). listen = interactions HTTP or gateway events as BPMN message events. Privileged intents required for list-members. No user-token selfbots.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}` or `BPMN error`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-message` | POST | `/channels/{channel_id}/messages` | Official HTTP |
| `send-webhook` | POST | `/webhooks/{webhook_id}/{webhook_token}` | Official HTTP |
| `get-message` | GET | `/channels/{channel_id}/messages/{message_id}` | Official HTTP |
| `list-messages` | GET | `/channels/{channel_id}/messages` | Official HTTP |
| `delete-message` | DELETE | `/channels/{channel_id}/messages/{message_id}` | Official HTTP |
| `add-reaction` | PUT | `/channels/{channel_id}/messages/{message_id}/reactions/{emoji}/@me` | Official HTTP |
| `create-channel` | POST | `/guilds/{guild_id}/channels` | Official HTTP |
| `get-channel` | GET | `/channels/{channel_id}` | Official HTTP |
| `list-channels` | GET | `/guilds/{guild_id}/channels` | Official HTTP |
| `update-channel` | PATCH | `/channels/{channel_id}` | Official HTTP |
| `delete-channel` | DELETE | `/channels/{channel_id}` | Official HTTP |
| `list-members` | GET | `/guilds/{guild_id}/members` | Official HTTP |
| `add-role` | PUT | `/guilds/{guild_id}/members/{user_id}/roles/{role_id}` | Official HTTP |
| `remove-role` | DELETE | `/guilds/{guild_id}/members/{user_id}/roles/{role_id}` | Official HTTP |
| `send-and-wait` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

`operation` (select), `bot_token` (password secret unless auth is none), `timeout_seconds` (default 25), plus provider ids listed on the definition.

## Security & legal

Official API only. Canonical messaging node; social-media.md is discovery only — same folder nodes/discord. Incoming webhooks are send-only (send-webhook). listen = interactions HTTP or gateway events as BPMN message events. Privileged intents required for list-members. No user-token selfbots. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. Inbound events are delivered through the StudioQuack engine's message-event ingress. This node is not a Webhook node.
