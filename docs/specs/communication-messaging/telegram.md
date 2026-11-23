# Telegram

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md` (canonical). Discovery cross-ref in `docs/catalog/social-media.md` — **same node**. Social `trigger` = this `listen`.
- **Folder when built:** `nodes/telegram/`
- **Researched:** 2026-08-20
- **Official API:** Telegram **Bot API 10.2** (changelog 2026-07-14)
- **Docs:** https://core.telegram.org/bots/api · Changelog: https://core.telegram.org/bots/api-changelog
- **Auth:** `bot_token` — secret binding: `bot_token`. Webhook `secret_token` for `listen`.

## Scope

Calls the **official Telegram Bot API** at `https://api.telegram.org/bot{token}/{method}` (file download `https://api.telegram.org/file/bot{token}/{file_path}`). JDK HTTP only — no TDLib, no MTProto user client, no unofficial “Telegram web” scrape.

`listen` is **setWebhook** (HTTPS POST of `Update` JSON) or **getUpdates** long-poll. The two are **mutually exclusive**; switching to long-poll requires `deleteWebhook`.

Bots cannot start DMs with users who have never pressed Start.

**Do not build:** user-account MTProto clients, session-string grabbers, or competing “Telegram user API” wrappers.

First-class on current Bot API and added vs seed: `send-voice` (distinct from `send-audio`), `send-draft` maps to `sendMessageDraft` (streaming) — **optional**; v1 keeps seed send methods and does **not** require Rich Messages (`sendRichMessage`, Bot API 10.1+) for the first ship. Rich Messages may be added later on **this** node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `sendMessage` | Text. `chat_id` + `text` (1–4096). |
| `send-photo` | POST | `sendPhoto` | Photo. `photo` = `file` ref-id **or** Telegram `file_id` string. |
| `send-document` | POST | `sendDocument` | General file. |
| `send-audio` | POST | `sendAudio` | Music (tags, duration). |
| `send-voice` | POST | `sendVoice` | Voice note (OGG/Opus). First-class; not in catalog seed. |
| `send-video` | POST | `sendVideo` | Video. |
| `send-animation` | POST | `sendAnimation` | GIF/H.264 without sound. |
| `send-sticker` | POST | `sendSticker` | WebP/TGS/WEBM sticker. |
| `send-location` | POST | `sendLocation` | `latitude`, `longitude`. |
| `send-media-group` | POST | `sendMediaGroup` | 2–10 items (`InputMedia*`). Each media is `file` or `file_id`. |
| `send-chat-action` | POST | `sendChatAction` | Typing/upload indicators. |
| `edit-message` | POST | `editMessageText` (or `editMessageCaption` / `editMessageMedia` when those fields set) | Edit bot messages. |
| `delete-message` | POST | `deleteMessage` | Delete. |
| `pin-message` | POST | `pinChatMessage` | Pin. |
| `unpin-message` | POST | `unpinChatMessage` or `unpinAllChatMessages` | Unpin one / all. |
| `get-file` | POST | `getFile` then GET file URL | Metadata + optional download to platform `file`. |
| `get-chat` | POST | `getChat` | Chat object. |
| `get-chat-administrators` | POST | `getChatAdministrators` | Admins. |
| `get-chat-member` | POST | `getChatMember` | One member. |
| `leave-chat` | POST | `leaveChat` | Bot leaves. |
| `set-chat-title` | POST | `setChatTitle` | Title. |
| `set-chat-description` | POST | `setChatDescription` | Description. |
| `answer-callback-query` | POST | `answerCallbackQuery` | ACK inline button. |
| `answer-inline-query` | POST | `answerInlineQuery` | Inline mode results. |
| `listen` | POST / GET | `setWebhook` / `deleteWebhook` / `getUpdates` | Inbound updates. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `bot_token` | password | yes | — | **yes** | From `@BotFather`. Used in the URL path — never log the URL. |
| `api_base_url` | string | no | `https://api.telegram.org` | no | Official host. Custom Bot API servers allowed if the operator self-hosts Telegram’s Bot API. |
| `listen_mode` | select | `listen` | `webhook` | no | `webhook` \| `long_poll`. |
| `webhook_url` | string | webhook listen | — | no | Public HTTPS URL (StudioQuack ingress). Telegram requires HTTPS. |
| `webhook_secret_token` | password | webhook recommended | — | **yes** | 1–256 chars `[A-Za-z0-9_-]`. Telegram sends `X-Telegram-Bot-Api-Secret-Token`. Compare constant-time. |
| `allowed_updates` | string | no | — | no | JSON array string, e.g. `["message","callback_query"]`. Empty = Telegram defaults (excludes some member/reaction types). |
| `long_poll_timeout_seconds` | number | long_poll | `50` | no | `getUpdates` `timeout` (Telegram long-poll). Keep < daemon HTTP client max. |
| `default_chat_id` | string | no | — | no | Default chat (id or `@username`). |
| `timeout_seconds` | number | no | `30` | no | RPC timeout (not long-poll). |
| `upload_timeout_seconds` | number | no | `120` | no | `multipart/form-data` sends. |
| `max_upload_bytes` | number | no | `52428800` | no | Bot API typical 50 MB bot-upload cap (documents); photos smaller — honor Telegram errors. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No bot token. Media is `file` ref-id or existing `file_id`. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Telegram `ok:false` → `error` + `description`. |

FUNCTION ports: none. No send-and-wait in v1 (not in seed; DMs still need the user to Start).

## Input fields (by operation)

Shared chat targeting:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `chat_id` | string | if config blank | Integer id, `-100…` groups, or `@channelusername`. |
| `message_thread_id` | number | no | Forum topic / private topics (Bot API 9.5+). |
| `reply_to_message_id` | number | no | Reply. |
| `disable_notification` | boolean | no | Silent. |
| `protect_content` | boolean | no | Forbid forwarding/saving. |
| `parse_mode` | string | no | `HTML` \| `MarkdownV2` \| `Markdown` (legacy). |
| `reply_markup` | object | no | InlineKeyboard / ReplyKeyboard JSON. |

### `operation = send-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string | yes | 1–4096 chars. |
| `link_preview` | boolean | no | Maps to `link_preview_options.is_disabled` inverse. |
| `receiver_user_id` | string | no | Bot API 10.2 ephemeral/receiver targeting. Optional. |

### Media sends (`send-photo`, `send-document`, `send-audio`, `send-voice`, `send-video`, `send-animation`, `send-sticker`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | one of file / file_id / url | Platform ref-id → multipart field (`photo`, `document`, …). |
| `file_id` | string | one of | Reuse a Telegram file_id (no re-upload). |
| `url` | string | one of | Telegram fetches the URL (operator SSRF policy applies — prefer `file`). |
| `caption` | string | no | 0–1024. |
| `duration` / `width` / `height` / `performer` / `title` | number/string | no | Media metadata when applicable. |

Exactly one of `file`, `file_id`, `url`.

### `operation = send-location`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `latitude` | number | yes | Lat. |
| `longitude` | number | yes | Lon. |
| `horizontal_accuracy` | number | no | Meters. |
| `live_period` | number | no | Live location seconds. |

### `operation = send-media-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | object | yes | Array of 2–10 `{ type, file\|file_id\|url, caption? }`. `type` = `photo`\|`video`\|`document`\|`audio`. |

### `operation = send-chat-action`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `typing`, `upload_photo`, `record_video`, `upload_video`, `record_voice`, `upload_voice`, `upload_document`, `choose_sticker`, `find_location`, `record_video_note`, `upload_video_note`. |

### `operation = edit-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | number | yes (chat messages) | Message to edit. |
| `inline_message_id` | string | inline | Alternative to chat_id+message_id. |
| `text` | string | edit text | New text. |
| `caption` | string | edit caption | New caption. |

### `operation = delete-message` / `pin-message` / `unpin-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | number | yes (unless unpin_all) | Target. |
| `unpin_all` | boolean | unpin, no | If true, `unpinAllChatMessages`. |
| `disable_notification` | boolean | pin, no | Silent pin. |

### `operation = get-file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | From a Message. |
| `download` | boolean | no | Default true: GET file path → platform `file`. |

### Chat admin

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | number | get-chat-member | User. |
| `title` | string | set-chat-title | 1–128 chars. |
| `description` | string | set-chat-description | 0–255. |

### `operation = answer-callback-query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_query_id` | string | yes | From Update. |
| `text` | string | no | Toast. |
| `show_alert` | boolean | no | Modal alert. |
| `url` | string | no | Game/open URL. |
| `cache_time` | number | no | Seconds. |

### `operation = answer-inline-query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `inline_query_id` | string | yes | From Update. |
| `results` | object | yes | Array of `InlineQueryResult*` JSON. |
| `cache_time` | number | no | Default 300. |
| `is_personal` | boolean | no | Per-user cache. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (setWebhook / begin poll) \| `stop` (deleteWebhook) \| `event` (inbound Update). |
| `drop_pending_updates` | boolean | start/stop | Passed to setWebhook/deleteWebhook. |
| `offset` | number | long_poll | `getUpdates` offset (handler persists last `update_id+1`). |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Telegram `description` (`ok:false`) or HTTP error. Never the bot token. |
| `error_code` | number | Telegram error | Telegram `error_code`. |
| `message_id` | number | send/edit | Message id. |
| `chat_id` | number | send/get-chat | Chat id. |
| `date` | number | send | Unix date. |
| `file_id` | string | media send / get-file | Telegram file_id for reuse. |
| `file_unique_id` | string | get-file | Stable unique id. |
| `file_path` | string | get-file | Do **not** log (contains token when composed into URL). |
| `file` | file | get-file download / inbound media | Platform ref-id. |
| `file_size` | number | get-file | Bytes. |
| `chat` | object | get-chat | `{ id, type, title, username }`. |
| `administrators` | array | get-chat-administrators | `{ user_id, status }`. |
| `member` | object | get-chat-member | `{ user_id, status }`. |
| `update` | object | listen | `{ update_id, type, chat_id, message_id, from_id, callback_query_id, inline_query_id, text_present }` — **do not log `text`**. |
| `ok` | boolean | Telegram raw | Always map false to error OUTPUT. |

## Legal

- Telegram Bot Platform Developer Terms: https://telegram.org/tos/bot-developers
- Telegram Terms of Service (users): https://telegram.org/tos
- Rate limits (undocumented hard caps; commonly ~30 messages/s to different chats, tighter to one chat). HTTP 429 `retry_after`. Honor it.
- Bots must not spam, must honor `/stop` where applicable, and cannot message users who never started the bot (except in groups they share).
- Message content and phone numbers (if ever present) are personal data. Webhook must be HTTPS. `secret_token` required in production.
- Cross-ref social catalog: one Telegram node only.

## Implementation notes

- JDK HTTP. Methods are POST (application/json **or** multipart). Multipart when a `file` ref-id is present.
- **Never put the bot token in logs or OUTPUT.** The token appears in the URL path — log `{method}` only.
- `ok: false` → `{ error, error_code }` even on HTTP 200.
- Timeouts: 30s RPC; long-poll uses `long_poll_timeout_seconds` + 10s grace. Uploads 120s.
- Retry: 429 with `retry_after`; 5xx max 4; no retry on 400/401/403/404.
- Webhook: compare `X-Telegram-Bot-Api-Secret-Token`. Respond 2xx quickly.
- File download: `getFile` then GET `/file/bot{token}/{file_path}` — stream into file store; OUTPUT `file` ref-id.
- Errors never throw past dispatch.
