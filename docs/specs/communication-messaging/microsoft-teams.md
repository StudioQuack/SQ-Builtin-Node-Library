# Microsoft Teams

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/microsoft-teams/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Graph REST **v1.0** (Teams: teams, channels, chats, chatMessage, Planner tasks)
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/teams-api-overview?view=graph-rest-1.0 · chatMessage: https://learn.microsoft.com/en-us/graph/api/resources/chatmessage?view=graph-rest-1.0 · Channel send: https://learn.microsoft.com/en-us/graph/api/channel-post-messages?view=graph-rest-1.0 · Planner task: https://learn.microsoft.com/en-us/graph/api/plannertask-get?view=graph-rest-1.0 · Change notifications: https://learn.microsoft.com/en-us/graph/teams-change-notification-in-microsoft-teams-overview
- **Auth:** `oauth2` (delegated — required to **send** messages in v1) or `entra_app_only` (read / Planner / channel CRUD as granted) — secret binding: `oauth_refresh_token` / `client_secret`

## Scope

Calls **Microsoft Graph v1.0** for Microsoft Teams channels, channel messages, 1:1/group **chats**, and **Planner** tasks (Teams Planner tabs). Same Entra token pattern as Outlook; JDK HTTP; **no Graph SDK**.

`listen` is Graph **subscriptions** on channel/chat messages (and optionally channels). Polling the same resource in a tight loop **violates** Microsoft APIs Terms — Graph documents a **once-per-day** poll cap for change detection; use subscriptions.

`send-and-wait` is **StudioQuack correlation** (post a message, wait for a reply `chatMessage` notification with `replyToId` / chat id) — not a Graph RPC.

**v1.0 only.** Do not call `/beta/`.

**Application permissions cannot send ordinary channel/chat messages.** Graph v1.0 allows application `Teamwork.Migrate.All` **only** for **migration mode** import. **DO NOT BUILD** migration-import send for v1. Sending requires **delegated** `ChannelMessage.Send` / `ChatMessage.Send` (signed-in user). App-only may still **list/get** where `ChannelMessage.Read.All` / RSC applies.

Task ops use **Planner** (`/planner/tasks`, `/planner/plans/{id}/tasks`) on Graph v1.0 — not Outlook `outlookTask` (preview/deprecated) and not Graph beta “task messages”.

## Operations (`operation` config values)

`{graph}/v1.0`. `{team-id}` = Graph team/group id.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-channel` | POST | `/teams/{team-id}/channels` | `{ displayName, description, membershipType }`. |
| `get-channel` | GET | `/teams/{team-id}/channels/{channel-id}` | One channel. |
| `list-channels` | GET | `/teams/{team-id}/channels` | List. |
| `update-channel` | PATCH | `/teams/{team-id}/channels/{channel-id}` | Rename/description. |
| `delete-channel` | DELETE | `/teams/{team-id}/channels/{channel-id}` | Delete. |
| `create-channel-message` | POST | `/teams/{team-id}/channels/{channel-id}/messages` | Send in channel. **Delegated.** Body `{ body: { content, contentType } }`. Hosted contents / file attachments via `hostedContents` + `file` ref-ids (inline images). |
| `get-channel-message` | GET | `/teams/{team-id}/channels/{channel-id}/messages/{message-id}` | First-class; not in catalog seed. |
| `list-channel-messages` | GET | `/teams/{team-id}/channels/{channel-id}/messages` | Root messages. `$top` ≤ 50. Date `$filter` required for some poll/delta scenarios. |
| `create-chat-message` | POST | `/chats/{chat-id}/messages` | Send in an existing chat. Does **not** create the chat. **Delegated.** |
| `get-chat-message` | GET | `/chats/{chat-id}/messages/{message-id}` | One chat message. |
| `list-chat-messages` | GET | `/chats/{chat-id}/messages` | `$top` ≤ 50. |
| `send-and-wait` | POST + correlate | create-*-message then `listen` | StudioQuack wait for reply. |
| `create-task` | POST | `/planner/tasks` | `{ planId, bucketId, title, assignments }`. Requires `If-Match` on later PATCH. |
| `get-task` | GET | `/planner/tasks/{id}` | Planner task. |
| `list-tasks` | GET | `/planner/plans/{plan-id}/tasks` | Tasks in a plan. |
| `update-task` | PATCH | `/planner/tasks/{id}` | **Requires** `If-Match: {etag}` (`@odata.etag`). |
| `delete-task` | DELETE | `/planner/tasks/{id}` | Requires `If-Match`. |
| `listen` | POST | `/subscriptions` | e.g. `/teams/{id}/channels/{id}/messages` or `/chats/{id}/messages`. Max expiration often **1 hour** unless `lifecycleNotificationUrl` is set (Teams messages). |

Do not use Teams as a log file (Graph + Microsoft APIs ToS).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-channel-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2` | no | `oauth2` \| `entra_app_only`. Send-message ops **reject** app-only with a clear `{ error }`. |
| `tenant_id` | string | yes | — | no | Entra tenant. |
| `graph_base_url` | string | no | `https://graph.microsoft.com` | no | National clouds as Outlook. |
| `client_id` | string | yes | — | no | App id. |
| `client_secret` | password | yes | — | **yes** | Client secret. |
| `oauth_refresh_token` | password | oauth2 | — | **yes** | Delegated refresh. |
| `team_id` | string | no | — | no | Default team id. |
| `channel_id` | string | no | — | no | Default channel id. |
| `chat_id` | string | no | — | no | Default chat id. |
| `plan_id` | string | no | — | no | Default Planner plan id for task list/create. |
| `scopes` | string | no | `ChannelMessage.Send ChatMessage.Send Channel.ReadWrite.All Chat.ReadWrite Tasks.ReadWrite offline_access` | no | Delegated least-privilege set. App-only uses `.default`. |
| `webhook_client_state` | password | listen | — | **yes** | Graph `clientState`. |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | Correlation wait. |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout. |
| `max_html_bytes` | number | no | `1048576` | no | Cap outgoing HTML body. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets. Message files are `file` ref-ids. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens; do not log message HTML. |

FUNCTION ports: none.

## Input fields (by operation)

### Channel CRUD

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` | string | if config blank | Team id. |
| `channel_id` | string | get/update/delete | Channel id. |
| `display_name` | string | create | Channel name. |
| `description` | string | no | Description. |
| `membership_type` | string | create, no | `standard` \| `private` \| `shared` (v1.0 supported types). |

### `operation = create-channel-message` / `send-and-wait` (channel)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` / `channel_id` | string | yes | Target. |
| `content` | string | yes | Message body. |
| `content_type` | string | no | `text` (default) \| `html`. |
| `attachments` | file[] | no | Hosted content / file share — handler uploads per Graph hosted-contents rules; store as `file` ref-ids never bytes on the wire JSON from the user. |
| `reply_to_id` | string | no | Thread reply (`replyToId` via POST `.../messages/{id}/replies` if set — **use replies path** when `reply_to_id` present: `POST /teams/{team-id}/channels/{channel-id}/messages/{id}/replies`). |
| `wait_scope` | string | send-and-wait | `channel_reply` \| `chat`. |
| `correlation_timeout_seconds` | number | send-and-wait | Override. |

### `operation = get-channel-message` / `list-channel-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` / `channel_id` | string | yes | Target. |
| `message_id` | string | get | Message id. |
| `top` | number | list | 1–50; default 20. |
| `filter` | string | list, no | `$filter` on `lastModifiedDateTime` / `createdDateTime` (`gt`/`lt`). |
| `next_link` | string | list, no | `@odata.nextLink` (must match `graph_base_url`). |

### Chat messages

| Field | Type | Required | Purpose |
|---|---|---|---|
| `chat_id` | string | yes | Existing chat id (from Graph, not created here in v1). |
| `message_id` | string | get | Message id. |
| `content` / `content_type` | string | create | Same as channel. |
| `attachments` | file[] | no | File ref-ids. |
| `top` / `next_link` | number/string | list | Paging. |

### Planner tasks

| Field | Type | Required | Purpose |
|---|---|---|---|
| `plan_id` | string | create/list | Plan id. |
| `bucket_id` | string | create, no | Bucket. |
| `title` | string | create | Title. |
| `assignments` | object | no | `{ "{user-guid}": { "@odata.type": "#microsoft.graph.plannerAssignment", "orderHint": " !" } }`. |
| `due_datetime` | string | no | ISO-8601. |
| `percent_complete` | number | update, no | 0–100. |
| `task_id` | string | get/update/delete | Task id. |
| `etag` | string | update/delete | `@odata.etag` from GET. Missing → `{ error: "etag_required" }`. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` \| `renew` \| `stop` \| `event`. |
| `resource` | string | start | `channel_messages` \| `chat_messages` \| `channels`. Mapped to Graph resource URLs using `team_id`/`channel_id`/`chat_id`. |
| `include_resource_data` | boolean | no | Default false (ids + follow-up GET). Rich notifications need encryption certificates — **out of v1**. |
| `subscription_id` | string | renew/stop | Id. |

Teams message subscriptions: if `expirationDateTime` > 1 hour, Graph requires `lifecycleNotificationUrl`. Set both URLs to StudioQuack ingress.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Graph error. App-only send → `delegated_token_required`. |
| `http_status` | number | HTTP error | Status. |
| `channel_id` | string | channel ops | Channel id. |
| `channels` | array | list-channels | `{ id, displayName, membershipType }`. |
| `message_id` | string | messages | ChatMessage id. |
| `etag` | string | tasks | Planner etag for next PATCH. |
| `messages` | array | list * messages | `{ id, createdDateTime, from_user_id, hasAttachments }` — strip HTML from logs. |
| `next_link` | string | lists | OData next. |
| `task_id` | string | tasks | Planner id. |
| `tasks` | array | list-tasks | `{ id, title, percentComplete, planId }`. |
| `subscription_id` | string | listen start | Persist. |
| `expiration_datetime` | string | listen | Renew before this. |
| `event` | object | listen event | `{ changeType, resource, message_id }`. |
| `reply_message_id` | string | send-and-wait | Correlated reply. |

## Legal

- Microsoft APIs Terms of Use: https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use
- Do not poll for changes more than once per day; use subscriptions. Do not use Teams as a logfile.
- Throttling (examples): GET channel ~1 rps per channel; list channels ~4 rps per team; POST channel ~4 rps per team. Full tables: https://learn.microsoft.com/en-us/graph/throttling-limits
- Channel/chat content is personal data / possible PHI. RSC vs tenant-wide `*.Read.All` — prefer least privilege.
- Admin consent for application permissions. Personal Microsoft accounts are **not** supported for these APIs.

## Implementation notes

- JDK HTTP. Token same as Outlook node (shared helper OK). Paths always `/v1.0/…`.
- Send ops: if `auth_type=entra_app_only` → `{ error: "delegated_token_required" }` without calling Graph (except documented migration, which we do not build).
- Timeouts 30s. Retry 429 with `Retry-After`; Teams 4 rps resource limits are easy to hit — backoff.
- Planner PATCH/DELETE without matching etag → 412. Surface `{ error: "etag_mismatch", http_status: 412 }`.
- Message attachments: Graph hosted content APIs; bytes from file store; OUTPUT never includes raw HTML dumps in logs.
- `listen` validationToken handshake identical to Outlook.
- Errors → OUTPUT `{ error }`; never throw past dispatch.
- Never log client secrets, message bodies, or hosted-content bytes.
