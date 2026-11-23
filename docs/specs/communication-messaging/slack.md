# Slack

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/slack/`
- **Researched:** 2026-08-20
- **Official API:** Slack Web API + Events API (HTTP). File upload **v2** (`files.getUploadURLExternal` + `files.completeUploadExternal`).
- **Docs:** https://docs.slack.dev/reference/methods/chat.postMessage · Events: https://docs.slack.dev/apis/events-api · Files v2: https://docs.slack.dev/messaging/working-with-files · Upload URL: https://docs.slack.dev/reference/methods/files.getUploadURLExternal
- **Auth:** `oauth2` (recommended) or `bot_token` — secret binding: `bot_token` / `oauth_refresh_token`. `listen` also needs `signing_secret`. Incoming webhook URL is **send-only**.

## Scope

Talks to Slack over HTTPS `https://slack.com/api/{method}` (Web API) and receives Events API HTTP POSTs on StudioQuack webhook ingress. JDK HTTP only — no Bolt/Java Slack SDK.

`listen` is the **Events API request URL** (signed with the app signing secret). Incoming Webhooks (`hooks.slack.com/services/…`) are an **auth mode for `send-message` only** — they cannot listen. Socket Mode is optional for operators without a public URL; v1 default is HTTP Events API.

`send-and-wait` is **StudioQuack correlation** (post a message, wait for a thread reply / `message` event with matching `thread_ts` or user DM) — not a Slack RPC.

**Do not call `files.upload`.** Slack deprecated it; newly created apps cannot use it; sunset **2025-11-12**. `upload-file` must implement the three-step v2 flow.

## Operations (`operation` config values)

All Web API methods: `POST https://slack.com/api/{method}` with `Authorization: Bearer {token}` (JSON or form). Success is HTTP 200 **and** JSON `ok: true`. `ok: false` → `{ error }` using Slack `error` string (even on HTTP 200).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `chat.postMessage` or Incoming Webhook URL | Post to a channel/DM. Webhook mode: JSON `{ text, blocks }` to the webhook URL only. |
| `send-and-wait` | POST + correlate | `chat.postMessage` then Events API | Send, then wait for a reply event. Not a Slack method. |
| `update-message` | POST | `chat.update` | Edit `channel` + `ts`. |
| `delete-message` | POST | `chat.delete` | Delete `channel` + `ts`. |
| `get-permalink` | POST/GET | `chat.getPermalink` | Permalink for `channel` + `message_ts`. |
| `search-messages` | POST/GET | `search.messages` | Requires `search:read`. Some workspaces/plans restrict this. |
| `archive-channel` | POST | `conversations.archive` | Archive a channel. |
| `close-channel` | POST | `conversations.close` | Close a DM/MPIM (not public channels). |
| `create-channel` | POST | `conversations.create` | `name`, `is_private`. |
| `get-channel` | POST/GET | `conversations.info` | Channel object. |
| `list-channels` | POST/GET | `conversations.list` | `types=public_channel,private_channel,mpim,im`. |
| `channel-history` | POST/GET | `conversations.history` | Messages in a channel. Tighter rate limits for some app classes (see Legal). |
| `invite-channel` | POST | `conversations.invite` | Invite user ids. |
| `join-channel` | POST | `conversations.join` | Bot joins a public channel. |
| `kick-channel` | POST | `conversations.kick` | Remove a user. |
| `leave-channel` | POST | `conversations.leave` | Bot leaves. |
| `list-channel-members` | POST/GET | `conversations.members` | Member ids. |
| `open-dm` | POST | `conversations.open` | Open IM/MPIM; returns `channel.id`. |
| `rename-channel` | POST | `conversations.rename` | Rename. |
| `get-replies` | POST/GET | `conversations.replies` | Thread replies for `channel` + `ts`. |
| `set-purpose` | POST | `conversations.setPurpose` | Channel purpose. |
| `set-topic` | POST | `conversations.setTopic` | Channel topic. |
| `unarchive-channel` | POST | `conversations.unarchive` | Unarchive. |
| `upload-file` | POST + POST + POST | `files.getUploadURLExternal` → POST `{upload_url}` → `files.completeUploadExternal` | Share a `file` ref-id into a channel/thread. |
| `get-file` | POST/GET | `files.info` | File metadata (not bytes). Download URL is short-lived — fetch to file store if the workflow needs a `file` port. |
| `list-files` | POST/GET | `files.list` | List files. |
| `add-reaction` | POST | `reactions.add` | `name` (emoji, no colons), `channel`, `timestamp`. |
| `remove-reaction` | POST | `reactions.remove` | Same keys. |
| `list-reactions` | POST/GET | `reactions.list` | Reactions by user. |
| `add-star` | POST | `stars.add` | Star a channel/message/file. |
| `remove-star` | POST | `stars.remove` | Unstar. |
| `list-stars` | POST/GET | `stars.list` | Starred items. |
| `get-user` | POST/GET | `users.info` | User object. |
| `list-users` | POST/GET | `users.list` | Workspace users (paginate `limit` ≤ 1000). |
| `update-user-profile` | POST | `users.profile.set` | Usually a **user** token; bot tokens are limited. Document scope `users.profile:write`. |
| `create-usergroup` | POST | `usergroups.create` | Admin. |
| `list-usergroups` | POST/GET | `usergroups.list` | List. |
| `update-usergroup` | POST | `usergroups.update` | Update. |
| `listen` | inbound POST | Events API URL | Verify `X-Slack-Signature` + `X-Slack-Request-Timestamp`. Handle `url_verification`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `bot_token` | no | `bot_token` \| `oauth2` \| `incoming_webhook` (`send-message` only). |
| `bot_token` | password | if bot/oauth access token stored | — | **yes** | `xoxb-…` (bot) or `xoxp-…` (user). Never log. Token rotation must be **off** if using a long-lived token; otherwise implement refresh. |
| `oauth_client_id` | string | `oauth2` | — | no | Slack app client id. |
| `oauth_client_secret` | password | `oauth2` | — | **yes** | Client secret. |
| `oauth_refresh_token` | password | `oauth2` with rotation | — | **yes** | Only if token rotation is on. |
| `incoming_webhook_url` | password | `incoming_webhook` | — | **yes** | `https://hooks.slack.com/services/…`. Send-only. Anyone with the URL can post. |
| `signing_secret` | password | `listen` | — | **yes** | HMAC key for Events API. |
| `socket_mode` | boolean | no | `false` | no | If true, `listen` uses Socket Mode (`apps.connections.open`) instead of HTTP. Requires `app_level_token` (`xapp-…`). v1 recommend HTTP. |
| `app_level_token` | password | if `socket_mode` | — | **yes** | `xapp-` token. |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | Correlation wait. |
| `timeout_seconds` | number | no | `30` | no | Web API timeout. |
| `upload_timeout_seconds` | number | no | `120` | no | Binary POST to `files.slack.com`. |
| `max_file_bytes` | number | no | `1048576000` | no | Reject uploads over this size (Slack workspace limits may be lower). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens. Files are `file` ref-ids. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Slack `ok:false` mapped to `error`. Never log message text. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-message` / `send-and-wait`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | if config blank | Channel/DM id. Incoming webhook ignores this (URL is channel-scoped). |
| `text` | string | if no `blocks` | Fallback text (required by Slack if no blocks). |
| `blocks` | object | no | Block Kit JSON array (as JSON string or array). Do not log. |
| `thread_ts` | string | no | Reply in thread. |
| `mrkdwn` | boolean | no | Default true. |
| `unfurl_links` | boolean | no | Default true. |
| `reply_broadcast` | boolean | no | Also post thread reply to channel. |

`send-and-wait` extra: `correlation_timeout_seconds` (number); wait for Events API `message` with `thread_ts` = sent `ts` (or next IM from the same `user` if `wait_mode=dm`).

### `operation = update-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | yes | Channel id. |
| `ts` | string | yes | Message timestamp. |
| `text` / `blocks` | string/object | one required | New content. |

### `operation = delete-message` / `get-permalink`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | yes | Channel id. |
| `ts` / `message_ts` | string | yes | Message ts (`get-permalink` uses `message_ts`). |

### `operation = search-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Slack search query. |
| `count` | number | no | Default 20. |
| `page` | number | no | Page number. |
| `sort` | string | no | `score` \| `timestamp`. |

### Channel ops

| Field | Type | Required | Operations | Purpose |
|---|---|---|---|---|
| `channel` | string | yes | archive, close, get, history, invite, join, kick, leave, members, rename, replies, purpose, topic, unarchive | Channel id. |
| `name` | string | create/rename | create, rename | Channel name (no `#`). |
| `is_private` | boolean | no | create | Default `false`. |
| `users` | string | invite / open-dm | invite, open-dm | Comma-separated user ids. |
| `user` | string | kick | kick-channel | User to remove. |
| `purpose` / `topic` | string | set-* | set-purpose, set-topic | Text. |
| `ts` | string | get-replies | get-replies | Parent `ts`. |
| `oldest` / `latest` | string | no | history, replies | Unix ts bounds. |
| `limit` | number | no | list/history/members | Page size (history typical max 1000; **unlisted commercial apps** may be capped at 15 — honor Slack’s response). |
| `cursor` | string | no | list/history/members/users | `next_cursor`. |
| `types` | string | no | list-channels | Default `public_channel,private_channel`. |
| `exclude_archived` | boolean | no | list-channels | Default `true`. |

### `operation = upload-file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id. Handler reads length + bytes. |
| `filename` | string | no | Default from store. |
| `channel` | string | no | Share-to channel id (`completeUploadExternal` `channel_id`). |
| `thread_ts` | string | no | Share into thread. |
| `initial_comment` | string | no | Caption. |
| `title` | string | no | File title. |
| `alt_txt` | string | no | Image alt (≤ 1000 chars). |

### `operation = get-file` / `list-files`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | get-file | Slack file id `F…`. |
| `channel` | string | list, no | Filter. |
| `user` | string | list, no | Filter. |
| `ts_from` / `ts_to` | string | list, no | Time range. |
| `count` / `page` | number | list, no | Paging. |
| `download` | boolean | get-file, no | If true, GET `url_private` with Bearer token → store as OUTPUT `file`. |

### Reactions / stars

| Field | Type | Required | Purpose |
|---|---|---|---|
| `channel` | string | add/remove reaction; star message | Channel. |
| `timestamp` | string | add/remove reaction | Message ts. |
| `name` | string | add/remove reaction | Emoji name (`thumbsup`, not `:thumbsup:`). |
| `file` | string | star file | Slack file id. |
| `item_channel` | string | add-star message | Alias of channel. |

### Users / usergroups

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user` | string | get-user | User id `U…`. |
| `profile` | object | update-user-profile | Slack profile JSON (`status_text`, `status_emoji`, `first_name`, …). User token. |
| `limit` / `cursor` | number/string | list-users | Pagination. |
| `name` | string | create-usergroup | Handle. |
| `usergroup` | string | update | Usergroup id. |
| `channels` | string | create/update | Default channels. |
| `description` | string | no | Description. |
| `include_disabled` | boolean | list-usergroups | Default false. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `event` (default inbound) \| `url_verification` handled automatically by ingress. |
| `event_types` | string | no | Documented filter for the Slack app config (e.g. `message.channels,reaction_added`). The node does not subscribe via API — the operator enables events in the Slack app. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Slack `error` (`invalid_auth`, `ratelimited`, `channel_not_found`, …) or HTTP mapping. |
| `warning` | string | Slack `warning` | Pass through. |
| `channel` | string | send/open-dm/create | Channel id. |
| `ts` | string | send/update | Message ts. |
| `permalink` | string | get-permalink | URL. |
| `message` | object | send/get history item | Slack message **ids + type**; strip `text`/`blocks` from **logs** (may remain on OUTPUT for the workflow). |
| `messages` | array | history, replies, search | Same redaction rule. |
| `channels` | array | list-channels | `{ id, name, is_private, is_archived }`. |
| `members` | array | list-channel-members | User ids. |
| `file_id` | string | upload-file / get-file | Slack file id. |
| `file` | file | get-file `download=true` | Platform ref-id. |
| `files` | array | list-files | Metadata (`id`, `name`, `size`, `mimetype`) — no private URLs in logs. |
| `users` | array | list-users | `{ id, name, deleted, is_bot }`. |
| `usergroup_id` | string | usergroup ops | Id. |
| `next_cursor` | string | cursored lists | Pagination. |
| `reply_ts` | string | send-and-wait | Correlated reply ts. |
| `event` | object | listen | `{ type, channel, user, ts, thread_ts, reaction }` — omit raw `text` from logs. |

## Legal

- Slack API Terms of Service: https://slack.com/terms-of-service/api
- Slack Application Developer Policy / Brand Guidelines (linked from API ToS).
- Rate limits: method-specific tiers (T2–T4). Honor `Retry-After`. `conversations.history` / `conversations.replies` for **commercially distributed unlisted** apps: as of Slack’s 2026 changelog, **1 request/minute** and **max 15 objects** for those app classes (existing unlisted installs: 2026-03-03). Marketplace / internal apps keep published limits — implementers must not assume 100+ rpm on history.
- Workspace messages are personal data. Do not log tokens (`xoxb-`, `xoxp-`, `xapp-`), signing secret, webhook URLs, or message bodies.
- Incoming webhooks cannot read history. Self-bot / stolen user-session cookies: **DO NOT BUILD**.
- One Events API URL per Slack app.

## Implementation notes

- JDK HTTP. JSON POST for modern methods; form for some older ones — both accepted by Slack. Always send `Authorization: Bearer`.
- Treat `ok: false` as failure even when HTTP is 200.
- **Upload v2:** (1) `files.getUploadURLExternal` with `filename` + `length` (bytes). (2) POST raw or multipart to `upload_url` (host `files.slack.com`) **without** logging the URL query. HTTP 200 required. (3) `files.completeUploadExternal` with `files: [{ id, title }]`, optional `channel_id`, `thread_ts`, `initial_comment`. Files are scanned asynchronously; `files.info` may lag — do not require `mimetype` immediately.
- Timeouts: 30s Web API, 120s upload. Retry 429/5xx with `Retry-After`; max 4 tries; no retry on `invalid_auth` / `channel_not_found`.
- Events API verify: `v0:{timestamp}:{raw_body}` HMAC-SHA256 with `signing_secret`, compare to `X-Slack-Signature`. Reject if `|now - timestamp| > 300s`. Respond 200 quickly. `url_verification` → return `challenge`.
- `listen` + `send-and-wait` are delivered through the StudioQuack engine's message-event ingress.
- Errors → `{ error }` on OUTPUT; never throw past dispatch.
- Egress: `slack.com`, `files.slack.com`, `hooks.slack.com` (webhook mode).
