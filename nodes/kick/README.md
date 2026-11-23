# Kick

Official Kick **Public API** (v1 + v2 livestream catalog). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.kick.com · OpenAPI https://api.kick.com/swagger/doc.yaml
- **Base:** `https://api.kick.com`
- **OAuth:** `https://id.kick.com` (authorization code + PKCE S256; client credentials for app tokens)
- **Auth:** Bearer `access_token`. Never on INPUT.
- **Researched:** 2026-08-22

This package ships the OfficialApiNode subset: users, channels, livestreams, chat send, EventSub-style `trigger`. Channel PATCH, chat delete, and moderation are documented on Kick but not mapped here (single-secret HTTP engine).

`livestream_get` (`GET /public/v1/livestreams`) is **deprecated** in Kick's OpenAPI. Prefer `livestream_user` (`GET /public/v1/users/livestreams?user_id=`) or `livestream_list` (`GET /public/v2/livestreams`).

`chat_send` path `POST /public/v1/chat` verified from [docs.kick.com/apis/chat.md](https://docs.kick.com/apis/chat.md). Kick requires JSON `content` and `type` (`user` \| `bot`); `broadcaster_user_id` is required when `type=user`.

Never return `stream.key` or ingest URLs on OUTPUT even if the token has `streamkey:read`. `trigger` is `events:subscribe` ingress.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `user_get` | GET | `/public/v1/users` |
| `channel_get` | GET | `/public/v1/channels` |
| `livestream_get` | GET | `/public/v1/livestreams` (**deprecated**) |
| `livestream_user` | GET | `/public/v1/users/livestreams` |
| `livestream_list` | GET | `/public/v2/livestreams` |
| `chat_send` | POST | `/public/v1/chat` |
| `trigger` | ingress | Webhook Event subscriptions — BPMN message event, not a socket |

`chat_send` body: `content` (max 500), optional `broadcaster_user_id`, `type` (`user` \| `bot`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`channel_get`) |
| `access_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 25 |

Ports: `input` / `output` only. Failures: `{status,http_status,data}` — never includes `access_token`.

Numeric REST QPS was unpublished at fetch time; honor 429.

## Legal

- Docs / developer programme: https://docs.kick.com · App setup / access: https://dev.kick.com
- Customer owns the Kick app and token. Official Public API only — do not scrape kick.com or use unofficial sockets.
