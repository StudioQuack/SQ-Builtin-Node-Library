# Microsoft Teams

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/microsoft-teams/api_spec.json`.

## Overview

Microsoft Graph v1.0 Teams (channels, chats, Planner tasks).

- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/teams-api-overview and https://learn.microsoft.com/en-us/graph/api/chatmessage-post?view=graph-rest-1.0
- **Auth:** bearer `access_token` (ChannelMessage.Send / ChatMessage.Send / Group.ReadWrite.All as needed).
- **Planner:** `update-task` / `delete-task` send `If-Match: {if_match}` (`@odata.etag` from get-task).

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `create-channel` | POST | `/teams/{team_id}/channels` | `displayName`, `description`, `membershipType` |
| `get-channel` / `list-channels` / `update-channel` / `delete-channel` | | `/teams/{team_id}/channels[/{channel_id}]` | |
| `create-channel-message` | POST | `/teams/{team_id}/channels/{channel_id}/messages` | official `body` `{contentType, content}` plus `subject` |
| `list-channel-messages` | GET | `.../messages` | `$top` |
| `create-chat-message` | POST | `/chats/{chat_id}/messages` | official `body` `{contentType, content}` |
| `get-chat-message` / `list-chat-messages` | GET | `/chats/{chat_id}/messages[/{message_id}]` | `$top` |
| `create-task` | POST | `/planner/tasks` | `planId`, `title`, `bucketId`, `assignments` |
| `get-task` / `list-tasks` / `update-task` / `delete-task` | | Planner | `if_match` required on write |
| `listen` / `send-and-wait` | ingress | — | BPMN message event |

## Configuration

`operation`, `access_token`, `team_id`, `chat_id`, `timeout_seconds`.
