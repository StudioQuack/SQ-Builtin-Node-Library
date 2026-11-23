# Twitch

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/twitch/`
- **Researched:** 2026-08-20
- **Official API:** Twitch Helix (Twitch API) + EventSub
- **Docs:** https://dev.twitch.tv/docs/api/ · https://dev.twitch.tv/docs/api/reference · https://dev.twitch.tv/docs/eventsub/
- **Auth:** oauth2 — secret bindings: `client_secret`, `access_token` (`client_id` is **not** a secret)

## Scope

One node for Twitch. All calls go to Helix `https://api.twitch.tv/helix` with **both** `Client-Id: {client_id}` and `Authorization: Bearer {access_token}`. Chat send uses **POST `/helix/chat/messages`** (Helix Send Chat Message). Real-time `trigger` is **EventSub** (webhook, WebSocket, or Conduits).

**PubSub was decommissioned 2025-04-14.** Do not implement PubSub, IRC, or unofficial web chat scrapers. IRC is not required for `chat_send` because Helix can send.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `stream_get` | GET | `/helix/streams` | Live streams (filter by `user_id` / `user_login` / `game_id`) |
| `user_get` | GET | `/helix/users` | Users by `id` and/or `login` (max 100 combined) |
| `clip_create` | POST | `/helix/clips?broadcaster_id=` | Create a clip of the live broadcast |
| `chat_send` | POST | `/helix/chat/messages` | Send chat (max 500 characters) |
| `trigger` | POST / GET / DELETE | `/helix/eventsub/subscriptions` | EventSub subscribe / list / delete; `ingest` for inbound webhook |

OAuth: `https://id.twitch.tv/oauth2/authorize` and `https://id.twitch.tv/oauth2/token`. App access token (client credentials) vs user access token — EventSub **webhook** transport requires an **app** access token; user tokens are rejected for that transport.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `user_get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | Twitch application Client-Id header. **Not a secret.** |
| `client_secret` | password | yes | empty | yes | Used only to mint/refresh tokens. Never on INPUT, never logged, never on OUTPUT. |
| `access_token` | password | yes | empty | yes | User or app Bearer as required by the op. Never on INPUT. |
| `eventsub_secret` | password | no | empty | yes | 10–100 ASCII chars. HMAC key for EventSub webhook signatures. Never on INPUT. |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |
| `default_broadcaster_id` | string | no | empty | no | Default Helix `broadcaster_id` / `user_id` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or `client_secret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = stream_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | array | no | Helix `user_id` (repeatable) |
| `user_login` | array | no | Helix `user_login` |
| `game_id` | string | no | Category |
| `type` | string | no | `all` \| `live` as documented |
| `first` | number | no | Page size (Helix max 100) |
| `after` | string | no | Cursor |

At least one filter is recommended; empty list returns a Helix page of current streams (rate-costly). Cap `first` at 100.

### `operation = user_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | array | no | User ids (max 100 combined with `login`) |
| `login` | array | no | Login names |

If both omitted, Helix returns the token’s user (user access token required).

### `operation = clip_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `broadcaster_id` | string | yes if config default empty | Channel to clip (must be live) |
| `has_delay` | boolean | no | Helix `has_delay` |

### `operation = chat_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `broadcaster_id` | string | yes if config default empty | Destination chat |
| `sender_id` | string | yes | Must match the user id in a **user** access token |
| `message` | string | yes | Max **500** characters. Emotes by name without colons |
| `reply_parent_message_id` | string | no | Reply thread |
| `for_source_only` | boolean | no | App token + Shared Chat only. Default after 2025-05-19 is source-channel-only for app tokens |
| `pin` | boolean | no | Pin 20 minutes. Requires `moderator:manage:chat_messages`. Cannot combine with reply / `for_source_only` |

Scopes: `user:write:chat` (user token). App token also needs `user:bot` on `sender_id` and `channel:bot` (or moderator) on the broadcaster.

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `subscribe` \| `unsubscribe` \| `list` \| `ingest` |
| `type` | string | `subscribe` | EventSub type e.g. `stream.online`, `channel.chat.message` (version as documented) |
| `version` | string | `subscribe` | Usually `"1"` or `"2"` per type |
| `condition` | object | `subscribe` | Type-specific (e.g. `broadcaster_user_id`) |
| `callback_url` | string | `subscribe` webhook | HTTPS :443 StudioQuack ingress |
| `transport` | string | no | `webhook` (default) \| `websocket` \| `conduit`. This node’s inbound `ingest` is **webhook** only. WebSocket/conduits are out of band. |
| `subscription_id` | string | `unsubscribe` | Helix subscription id |
| `payload` | object | `ingest` | Raw JSON body |
| `headers` | object | `ingest` | Incoming EventSub headers (message id, timestamp, signature, message type) |

Subscribe body (webhook): `transport.method=webhook`, `transport.callback`, `transport.secret` = config `eventsub_secret`. Challenge: if `Twitch-Eventsub-Message-Type: webhook_callback_verification`, respond HTTP 200 with the `challenge` string as the body (ingress adapter), not `{ error }`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `users` | array | `user_get` | `{ id, login, display_name, broadcaster_type, created_at }` — omit `email` unless the operator explicitly requested it and the token has `user:read:email`; still never log email |
| `streams` | array | `stream_get` | `{ id, user_id, user_login, title, viewer_count, started_at, type }` |
| `edit_url` | string | `clip_create` | Clip editor URL (short-lived) |
| `clip_id` | string | `clip_create` | When Helix returns it |
| `message_id` | string | `chat_send` | Sent id |
| `is_sent` | boolean | `chat_send` | Helix `is_sent` |
| `drop_reason` | object | `chat_send` | If dropped |
| `subscription_id` | string | `trigger` subscribe | EventSub id |
| `subscriptions` | array | `trigger` list | Subscription rows (no secret) |
| `event` | object | `trigger` ingest | `{ message_type, subscription_type, event }` after signature OK |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 60 / 30 |

## Legal

- [Twitch Developer Agreement](https://www.twitch.tv/p/en/legal/developer-agreement/) (confirm live) · [Helix](https://dev.twitch.tv/docs/api/) · [EventSub](https://dev.twitch.tv/docs/eventsub/) · [Send Chat Message](https://dev.twitch.tv/docs/api/reference#send-chat-message)
- Helix rate limit: **800 points per minute per access token** (Twitch API Concepts). EventSub has a separate cost model (`max_total_cost`, typically 10,000). Chat send also has chatter-based 429s.
- `client_id` is public. `client_secret`, user tokens, refresh tokens, and `eventsub_secret` are secrets.
- Chat and EventSub payloads are communications PII (display names, user ids, message text). Minimize logs to ids and lengths.
- No PubSub. No scraping twitch.tv. No unofficial GQL.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Every Helix call: `Client-Id`, `Authorization: Bearer`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`. POST JSON: `Content-Type: application/json`.
- Missing `client_id` or `access_token` → `{ error }` no HTTP.
- Response cap **1_000_000** bytes. Timeout 25 s.
- EventSub ingest: HMAC-SHA256 over `message-id + timestamp + raw body`, header `Twitch-Eventsub-Message-Signature` (`sha256=` + hex). Reject timestamp older than **10 minutes**. Dedupe `Twitch-Eventsub-Message-Id`. Verify **raw bytes**, never re-serialized JSON.
- 429 → `{ error, http_status: 429, retry_after_seconds }`. No in-handler retry loop.
- Logs: operation, Helix status, counts — never tokens, chat text, or emails.
