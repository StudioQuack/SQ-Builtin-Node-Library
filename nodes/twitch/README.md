# Twitch

Official Twitch **Helix** API plus EventSub ingress. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://dev.twitch.tv/docs/api/ · https://dev.twitch.tv/docs/eventsub/
- **Base:** `https://api.twitch.tv/helix`
- **Auth:** Bearer `access_token`. Never on INPUT.
- **Researched:** 2026-08-22

Helix requires **both** `Client-Id` and `Authorization: Bearer`. extraHeaders send `Client-Id: {client_id}` after Bearer (https://dev.twitch.tv/docs/api/).

PubSub was decommissioned **2025-04-14**. Do not implement IRC or PubSub.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `stream_get` | GET | `/streams` |
| `user_get` | GET | `/users` |
| `clip_create` | POST | `/clips` query `broadcaster_id` |
| `chat_send` | POST | `/chat/messages` (not IRC) |
| `trigger` | ingress | EventSub — BPMN message event, not a socket |

`chat_send` body: `broadcaster_id`, `sender_id`, `message`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`user_get`) |
| `access_token` | password | yes | Bearer user or app token |
| `client_id` | string | no | Twitch application Client-Id. Sent as header `Client-Id`. |
| `timeout_seconds` | number | no | Default 25 |

Ports: `input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Developer agreement: https://www.twitch.tv/p/en/legal/developer-agreement/
- Customer owns the Twitch app and token. Official Helix only — do not scrape twitch.tv.
