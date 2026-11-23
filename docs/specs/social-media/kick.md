# Kick

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/kick/`
- **Researched:** 2026-08-20
- **Official API:** Kick Public API **v1** (`Kick Dev Public API` OpenAPI 1.0.0) plus livestream list **v2**
- **Docs:** https://docs.kick.com · https://dev.kick.com · OpenAPI https://api.kick.com/swagger/doc.yaml
- **Auth:** oauth2.1 — secret bindings: `client_secret`, `access_token`, `refresh_token` (`client_id` is **not** a secret)

## Scope

**Build this node.** Kick publishes an official Public API. The catalog row that said “no official API / do not ship” is **stale** (as of this research date).

Resource host: `https://api.kick.com` with paths under `/public/v1` (livestream catalog list: `/public/v2/livestreams`). OAuth host: `https://id.kick.com` (authorization code **+ PKCE S256 required**, client credentials for app tokens). Chat, channels, users, livestreams, moderation, and webhook Event subscriptions are documented. Do not scrape kick.com, do not use unofficial Pusher sockets as the node transport, do not reverse-engineer private app APIs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `user_get` | GET | `/public/v1/users` | Users by `id`; omit ids → token user (`user:read` for user tokens) |
| `channel_get` | GET | `/public/v1/channels` | By `broadcaster_user_id` **or** `slug` (not mixed; max 50). No params → current user (`channel:read`) |
| `channel_update` | PATCH | `/public/v1/channels` | Livestream metadata (`channel:write`) |
| `livestream_list` | GET | `/public/v2/livestreams` | Active livestreams (cursor pagination). **Not** deprecated `/public/v1/livestreams` |
| `livestream_get` | GET | `/public/v1/users/livestreams` | Active streams for up to 100 `user_id`s |
| `chat_send` | POST | `/public/v1/chat` | Send as `user` or `bot` (`chat:write`). Content max 500 |
| `chat_delete` | DELETE | `/public/v1/chat/{message_id}` | Delete a message (`moderation:chat_message:manage`) |
| `moderation_ban` | POST | `/public/v1/moderation/bans` | Ban or timeout (`moderation:ban`) |
| `moderation_unban` | DELETE | `/public/v1/moderation/bans` | Remove ban/timeout (`moderation:ban`) |
| `trigger` | POST / GET / DELETE | `/public/v1/events/subscriptions` | Webhook subscriptions (`events:subscribe`). Inbound `ingest` |

App vs user tokens: app (client credentials) can read public channel/livestream/user data and subscribe to events for a supplied `broadcaster_user_id`. Chat, moderation, and channel PATCH need a **user** token with the matching scopes.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `channel_get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | Kick app Client ID. Not a secret. |
| `client_secret` | password | yes | empty | yes | App secret. Never on INPUT. |
| `access_token` | password | yes | empty | yes | Bearer for API calls. Never on INPUT. |
| `refresh_token` | password | no | empty | yes | User-token refresh (`grant_type=refresh_token`). Rotate on exchange. |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |
| `default_broadcaster_user_id` | number | no | empty | no | Default broadcaster id |

PKCE (`code_verifier` / `code_challenge` S256) belongs in the OAuth bind flow, not on INPUT.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Do not accept stream keys. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never return `stream.key` / ingest URL from `channel_get` even if the token has `streamkey:read` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = user_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | array | no | Kick user ids (query `id`) |

### `operation = channel_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `broadcaster_user_id` | array | no | Up to 50. Cannot combine with `slug` |
| `slug` | array | no | Up to 50, each max 25 chars |

### `operation = channel_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `stream_title` | string | one of these required | Title (min length 1) |
| `category_id` | number | one of these required | Category id ≥ 1 |
| `custom_tags` | array | one of these required | Max 10 strings |

### `operation = livestream_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `category_id` | array | no | Max 25 |
| `language_code` | array | no | BCP 47, max 25 |
| `limit` | number | no | 1–1000, default 100 |
| `cursor` | string | no | `pagination.next_cursor` |

### `operation = livestream_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | array | yes | Max 100 broadcaster ids |

### `operation = chat_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `content` | string | yes | Max 500 chars |
| `type` | string | yes | `user` \| `bot` |
| `broadcaster_user_id` | number | if `type=user` | Required for user send; ignored for bot (bot always posts to the token channel) |
| `reply_to_message_id` | string | no | UUID of parent message |

### `operation = chat_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Path id |

### `operation = moderation_ban`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `broadcaster_user_id` | number | yes | Channel |
| `user_id` | number | yes | Target |
| `duration` | number | no | Timeout minutes 1–10080. Omit for permanent ban |
| `reason` | string | no | Max 100 chars |

### `operation = moderation_unban`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `broadcaster_user_id` | number | yes | Channel |
| `user_id` | number | yes | Target |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `subscribe` \| `unsubscribe` \| `list` \| `ingest` |
| `events` | array | `subscribe` | `{ name, version }` e.g. `chat.message.sent` v1, `livestream.status.updated` v1. `method` must be `webhook` |
| `broadcaster_user_id` | number | app token subscribe | Required for app tokens; ignored for user tokens (inferred) |
| `id` | array | `unsubscribe` | Subscription ids (query `id`) |
| `payload` | object | `ingest` | Raw JSON body |
| `headers` | object | `ingest` | `Kick-Event-*` headers |

Event names (v1): `chat.message.sent`, `channel.followed`, `channel.subscription.renewal`, `channel.subscription.gifts`, `channel.subscription.new`, `channel.reward.redemption.updated`, `livestream.status.updated`, `livestream.metadata.updated`, `moderation.banned`, `kicks.gifted`. Caps: 10,000 subscriptions per event type per app; `chat.message.sent` **1,000** until the app is verified (then 10,000).

Webhook URL is configured in Kick Developer settings (not the subscribe body). Subscribe tells Kick **which events**; delivery hits that app webhook URL. Point that URL at StudioQuack HTTPS ingress.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `users` | array | `user_get` | `{ user_id, name, profile_picture }` — **omit `email`** even if present |
| `channels` | array | `channel_get` | `{ broadcaster_user_id, slug, stream_title, is_live, viewer_count }` — strip `stream.key` / `stream.url` ingest |
| `livestreams` | array | livestream ops | `{ id, title, slug, viewer_count, started_at, tags }` |
| `message_id` | string | `chat_send` | UUID |
| `is_sent` | boolean | `chat_send` | Provider `is_sent` |
| `subscriptions` | array | `trigger` list/subscribe | `{ id, event, version, broadcaster_user_id }` |
| `event` | object | `trigger` ingest | `{ type, version, message_id, data }` after signature OK |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 / 30 |

## Legal

- [Kick Dev docs](https://docs.kick.com) · [OAuth 2.1](https://docs.kick.com/getting-started/generating-tokens-oauth2-flow) · [Scopes](https://docs.kick.com/getting-started/scopes) · [Webhook security](https://docs.kick.com/events/webhook-security) · [Access / app setup](https://dev.kick.com) — operator must accept Kick’s developer access policy when creating an app.
- Chat, follows, subs, and moderation payloads are PII. Ban reasons and chat text must not be logged.
- Stream keys (`streamkey:read`) are high-value secrets — this node does not expose them on OUTPUT.
- Published numeric REST rate limits were **not** on the FAQ page fetched 2026-08-20; honor HTTP **429**. Unverified apps have lower EventSub chat caps (above).
- Do not scrape kick.com HTML/WebSockets.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- API: `Authorization: Bearer {access_token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`. OAuth form posts: `application/x-www-form-urlencoded` to `https://id.kick.com/oauth/token`.
- PKCE: `code_challenge_method` **must** be `S256` (`plain` rejected).
- Empty token → `{ error: "Missing Kick access token. Bind access_token in node parameters." }` — no HTTP.
- Response cap **1_000_000** bytes. Timeout 25 s.
- Ingest signature: concatenate `Kick-Event-Message-Id` + `.` + `Kick-Event-Message-Timestamp` + `.` + **raw body**. Verify RSA PKCS#1 v1.5 SHA-256 against Kick’s public key (`GET https://api.kick.com/public/v1/public-key` or the PEM in webhook-security docs). Dedupe `Kick-Event-Message-Id`. Kick unsubscribes after ~1 day of delivery failure.
- 429 → `{ error, http_status: 429, retry_after_seconds: 60 }`. No tight retry loop.
- Logs: operation, status, ids — never tokens, chat `content`, emails, or stream keys.
- Do not call deprecated GET `/public/v1/livestreams` (OpenAPI `deprecated: true`). Do not call deprecated POST `/public/v1/token/introspect` on the API host; use `https://id.kick.com/oauth/token/introspect` if needed internally.
