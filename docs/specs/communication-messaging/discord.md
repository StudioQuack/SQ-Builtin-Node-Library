# Discord

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md` (canonical). Also listed for discovery in `docs/catalog/social-media.md` — **do not fork a second node**. Social catalog `trigger` maps to this node’s `listen`.
- **Folder when built:** `nodes/discord/`
- **Researched:** 2026-08-20
- **Official API:** Discord HTTP API **v10** (`https://discord.com/api/v10`)
- **Docs:** https://discord.com/developers/docs/reference · Messages: https://discord.com/developers/docs/resources/message · Webhooks: https://discord.com/developers/docs/resources/webhook
- **Auth:** `bot_token` (Bot) \| `oauth2` (Bearer) \| `webhook_url` (Incoming Webhook, **send-only**) — secret binding: `bot_token` / `webhook_url`. `listen` needs `public_key` (Ed25519) for Interactions HTTP.

## Scope

Calls the **official Discord REST API v10** for messages, channels, members, and roles, plus **Incoming Webhooks** as a send-only auth mode (`send-webhook`). `listen` is **Interactions HTTP** (preferred) or the **Gateway** (WebSocket) for privileged events — never a sibling trigger node.

Incoming webhooks **cannot** receive events. They only POST messages (`send-webhook`).

`send-and-wait` is **StudioQuack correlation** (send a message, wait for a reply in the same channel/thread or a component interaction) — not a Discord RPC.

**Do not build:** user-token **selfbots** (`Authorization` of a user account), unofficial scrapers, or capturing user passwords. Discord Developer Terms and Developer Policy forbid automating user accounts.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `/channels/{channel.id}/messages` | Bot/OAuth send. Multipart if `file` attachments. Max request **25 MiB**. Content ≤ 2000 chars. |
| `send-and-wait` | POST + correlate | `/channels/{channel.id}/messages` then `listen` | Send, wait for reply/interaction. Not a Discord method. |
| `send-webhook` | POST | `/webhooks/{id}/{token}` (`?wait=true` recommended) | Incoming webhook URL. Optional `thread_id`, `username`, `avatar_url`. Send-only. |
| `get-message` | GET | `/channels/{channel.id}/messages/{message.id}` | One message. Needs `MESSAGE_CONTENT` privileged intent to read `content` of other users. |
| `list-messages` | GET | `/channels/{channel.id}/messages` | `around` / `before` / `after` / `limit` 1–100. |
| `delete-message` | DELETE | `/channels/{channel.id}/messages/{message.id}` | Delete own or Manage Messages. |
| `add-reaction` | PUT | `/channels/{channel.id}/messages/{message.id}/reactions/{emoji}/@me` | `{emoji}` URL-encoded (`%F0%9F%91%8D` or `name:id`). |
| `create-channel` | POST | `/guilds/{guild.id}/channels` | Guild channel (`name`, `type`, `parent_id`). |
| `get-channel` | GET | `/channels/{channel.id}` | Channel object. |
| `list-channels` | GET | `/guilds/{guild.id}/channels` | All guild channels. |
| `update-channel` | PATCH | `/channels/{channel.id}` | Name, topic, nsfw, parent, rate_limit. |
| `delete-channel` | DELETE | `/channels/{channel.id}` | Delete/close. |
| `list-members` | GET | `/guilds/{guild.id}/members` | Requires **Server Members Intent**. `limit` ≤ 1000, `after`. |
| `add-role` | PUT | `/guilds/{guild.id}/members/{user.id}/roles/{role.id}` | Assign role. |
| `remove-role` | DELETE | `/guilds/{guild.id}/members/{user.id}/roles/{role.id}` | Remove role. |
| `listen` | inbound HTTPS or Gateway | Interactions endpoint **or** Gateway `wss://gateway.discord.gg` | HTTP: verify Ed25519 `X-Signature-Ed25519` + timestamp. Gateway: identify with intents. Incoming webhooks are **not** listen. |

First-class but omitted from v1 (still official): pin/unpin, remove-reaction, bulk-delete, threads, scheduled events — add later on **this** node if needed. Do not create extra nodes.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `bot_token` | no | `bot_token` \| `oauth2` \| `webhook_url`. |
| `api_base_url` | string | no | `https://discord.com/api/v10` | no | Pin **v10**. Do not omit version (Discord default version may change). |
| `bot_token` | password | bot_token | — | **yes** | Bot token. Header `Authorization: Bot {token}`. Never a user token. |
| `oauth_access_token` | password | oauth2 | — | **yes** | `Authorization: Bearer {token}`. |
| `webhook_url` | password | webhook_url / `send-webhook` | — | **yes** | `https://discord.com/api/webhooks/{id}/{token}`. Treat as a password. |
| `public_key` | string | `listen` HTTP | — | no | Hex application public key for Ed25519 verify. |
| `application_id` | string | interactions | — | no | Application snowflake (follow-up webhooks). |
| `default_guild_id` | string | no | — | no | Default guild for channel/member/role ops. |
| `default_channel_id` | string | no | — | no | Default channel for message ops. |
| `privileged_intents` | string | `listen` gateway | — | no | Comma list: `GUILD_MEMBERS`, `MESSAGE_CONTENT`. Must be enabled in the Developer Portal. |
| `listen_mode` | select | `listen` | `interactions_http` | no | `interactions_http` \| `gateway`. Prefer HTTP. Gateway is a long-lived WebSocket — only if the daemon can host it. |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | Correlation wait. |
| `timeout_seconds` | number | no | `30` | no | REST timeout. |
| `upload_timeout_seconds` | number | no | `120` | no | Multipart send. |
| `max_upload_bytes` | number | no | `26214400` | no | 25 MiB Discord cap. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No tokens. Per-verb fields (`channel_id`, `content`, …). |
| `output` | OUTPUT | object `{ status, … }` or `{ error }` | Never log content or webhook tokens. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-message` / `send-and-wait`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel_id` | string | if config blank | Channel snowflake. |
| `content` | string | if no embeds/files | ≤ 2000 chars. |
| `embeds` | object | no | Up to 10 embed objects (JSON). |
| `tts` | boolean | no | Needs `SEND_TTS_MESSAGES`. |
| `message_reference_id` | string | no | Reply to this message id. |
| `allowed_mentions` | object | no | Discord allowed_mentions. Default parse none of `@everyone` if omitted — **implementer: default `{ parse: [] }`** to avoid accidental pings. |
| `attachments` | file[] | no | Platform ref-ids → multipart `files[n]`. |
| `components` | object | no | Buttons/selects (for send-and-wait). |
| `correlation_timeout_seconds` | number | send-and-wait | Override wait. |

### `operation = send-webhook`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `content` | string | if no embeds/files | ≤ 2000. |
| `username` | string | no | Override webhook name. |
| `avatar_url` | string | no | Override avatar. |
| `thread_id` | string | no | Query `thread_id`. |
| `thread_name` | string | no | Forum post name (mutually exclusive with `thread_id`). |
| `embeds` | object | no | Embeds. |
| `attachments` | file[] | no | Multipart. |
| `wait` | boolean | no | Default **true** so OUTPUT includes the message object. |

### `operation = get-message` / `delete-message` / `add-reaction`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel_id` | string | yes | Channel. |
| `message_id` | string | yes | Message. |
| `emoji` | string | add-reaction | Unicode or `name:id`. |

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel_id` | string | yes | Channel. |
| `limit` | number | no | 1–100; default 50. |
| `before` / `after` / `around` | string | no | Message snowflake cursors (only one). |

### Channel ops

| Field | Type | Required | Purpose |
|---|---|---|---|
| `guild_id` | string | create/list | Guild. |
| `channel_id` | string | get/update/delete | Channel. |
| `name` | string | create; update optional | 1–100 chars. |
| `type` | number | create, no | 0 GUILD_TEXT, 2 VOICE, 4 CATEGORY, 5 ANNOUNCEMENT, 13 STAGE, 15 FORUM (Discord channel types). |
| `parent_id` | string | no | Category. |
| `topic` | string | update, no | Topic. |
| `nsfw` | boolean | no | NSFW flag. |
| `rate_limit_per_user` | number | no | Slowmode seconds. |

### Members / roles

| Field | Type | Required | Purpose |
|---|---|---|---|
| `guild_id` | string | yes | Guild. |
| `user_id` | string | add/remove-role | Member. |
| `role_id` | string | add/remove-role | Role. |
| `limit` | number | list-members | 1–1000; default 1. |
| `after` | string | list-members | User snowflake cursor. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `event` (inbound already verified). |
| `interaction_respond` | boolean | no | Default true: ACK `PING` with `{ type: 1 }` (PONG) and ACK component/slash with type 5 (deferred) or 4 (message) as configured. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | `Discord API Error: HTTP {code}` or Discord JSON `message`. No tokens. |
| `http_status` | number | HTTP error | Including 429. |
| `retry_after` | number | 429 | Seconds from Discord body. |
| `message_id` | string | send/get | Snowflake. |
| `channel_id` | string | send/get/channel | Channel. |
| `guild_id` | string | guild ops | Guild. |
| `content` | string | get | Message text (PII — do not log). |
| `attachments` | array | get | `{ id, filename, size, url }` — if downloading, use `file` ref-ids, never put bytes on OUTPUT. |
| `file` | file | when downloading an attachment | Optional follow-up: GET Discord CDN with bot auth if required → file store. v1: metadata URLs only unless `download_attachments=true` on get-message. |
| `messages` | array | list-messages | Slim `{ id, author_id, timestamp, type }`. |
| `channels` | array | list-channels | `{ id, name, type, parent_id }`. |
| `members` | array | list-members | `{ user_id, nick, roles }`. |
| `channel` | object | create/get/update | Channel resource (ids/names). |
| `reply_message_id` | string | send-and-wait | Correlated reply. |
| `event` | object | listen | `{ type, channel_id, user_id, message_id, custom_id }` for interactions/gateway. |

## Legal

- Discord Developer Terms of Service: https://support-dev.discord.com/hc/en-us/articles/8562894815383-Discord-Developer-Terms-of-Service
- Discord Developer Policy (Developer Portal / policy pages linked from Terms). **Selfbots and user-account automation are forbidden.**
- Rate limits: per-route buckets; global 50 req/s typical. Honor `X-RateLimit-*` and 429 `retry_after`. https://discord.com/developers/docs/topics/rate-limits
- Message content of other users requires the privileged **MESSAGE CONTENT** intent and Discord approval over 100 guilds.
- Guild member lists require **SERVER MEMBERS INTENT**.
- Messages and member lists are personal data. Webhook URLs are secrets (channel write access).
- Cross-ref: discovery row in `docs/catalog/social-media.md` is the **same** node. Catalog leftover wording about a missing trigger means: use `listen` on this node (gateway or interactions), not a second Discord node.

## Implementation notes

- JDK HTTP. Path prefix `/api/v10`. `User-Agent` must identify the operator app (Discord asks for a proper UA).
- Attachments: multipart/form-data `payload_json` + `files[n]`; source bytes from file store.
- Timeouts: 30s JSON, 120s multipart. Size cap 25 MiB.
- Retry: 429/502/503 with `retry_after`; max 4; no retry on 401/403/404.
- Interactions HTTP: verify Ed25519 over `timestamp + raw_body` using `public_key`. Invalid signature → 401 and drop. PING (`type: 1`) → `{ "type": 1 }` within Discord’s timeout (~3s).
- Gateway `listen`: identify, heartbeat, resume. Privileged intents as configured. This is heavier than HTTP; document operator cost.
- Incoming webhook `send-webhook` must not require a bot token.
- HTTP 4xx/5xx → `{ error }` on OUTPUT; never throw past dispatch.
- Logs: route, status, sizes. Never log bot tokens, webhook tokens, message `content`, or file bytes.
