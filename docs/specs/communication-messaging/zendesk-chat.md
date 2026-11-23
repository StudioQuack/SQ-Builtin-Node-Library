# Zendesk Messaging (Sunshine Conversations)

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md` (row title may still say “Zendesk Chat”)
- **Folder when built:** `nodes/zendesk-chat/` (keep catalog key; product is **Messaging**)
- **Researched:** 2026-08-20
- **Official API:** **Sunshine Conversations API v2** (Zendesk Messaging platform). Legacy **Zendesk Chat** / Chat Conversations API is maintenance-only; **no new Chat Conversations API integrations since 2025-04-30**. Zendesk Chat product removal announced for **2028-01-31**.
- **Docs:** [Messaging platform](https://developer.zendesk.com/documentation/conversations/) · [API authentication](https://developer.zendesk.com/documentation/conversations/getting-started/api-authentication/) · [API versioning](https://developer.zendesk.com/documentation/conversations/references/api-versioning/) · [Sending messages](https://developer.zendesk.com/documentation/conversations/messaging-platform/programmable-conversations/sending-messages/) · [Receiving messages](https://developer.zendesk.com/documentation/conversations/messaging-platform/programmable-conversations/receiving-messages/) · [OpenAPI](https://developer.zendesk.com/documentation/conversations/references/openapi-specification/) · spec: [github.com/zendesk/sunshine-conversations-api-spec](https://github.com/zendesk/sunshine-conversations-api-spec)
- **Auth:** HTTP Basic (`key_id`:`key_secret`) or Bearer JWT signed with the same key — secret bindings: `key_id`, `key_secret`. Webhook shared secret: `webhook_secret` (password).

## Scope

Programmable **Zendesk Messaging** via Sunshine Conversations v2:

`https://{subdomain}.zendesk.com/sc/v2/apps/{app_id}/…`

(Legacy direct Sunshine accounts may still use `https://api.smooch.io` — **v1 of this node targets Zendesk-hosted `/sc/v2` only**.)

This is **not** the Zendesk Support ticketing node (tickets, users-as-agents, macros). Chat REST `/visitors` and `/agents` are **legacy Chat** and are **not** called.

**Product names (2026):** customer-facing product = **Zendesk Messaging**. Developer API = **Sunshine Conversations API v2**. “Zendesk Chat” (Zopim) is the old session widget.

Seed `update-visitor` → **`update-user`** (Users API). Seed `list-agents` is **dropped** (no Conversations-API agent roster; Support agents belong on the ticketing node).

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `send-message` | POST | `/sc/v2/apps/{app_id}/conversations/{conversation_id}/messages` | Business message into a conversation |
| `get-conversation` | GET | `/sc/v2/apps/{app_id}/conversations/{conversation_id}` | Fetch one conversation |
| `list-conversations` | GET | `/sc/v2/apps/{app_id}/conversations` | List; typically `filter[userId]=` |
| `get-user` | GET | `/sc/v2/apps/{app_id}/users/{user_id}` | Messaging user (replaces Chat “visitor” get) |
| `update-user` | PATCH | `/sc/v2/apps/{app_id}/users/{user_id}` | Profile / givenName / surname / properties (replaces `update-visitor`) |
| `listen` | POST (inbound) | webhook `target` on a custom integration | `conversation:message` and related triggers; verify `X-API-Key` shared secret |

Dropped: `update-visitor`, `list-agents`. Do not call Chat Conversations API (`wss://` / Chat REST) for new work.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Row above |
| `subdomain` | string | yes | — | no | `{subdomain}.zendesk.com`. No scheme. SSRF: hostname must be `*.zendesk.com` |
| `app_id` | string | yes | — | no | Sunshine Conversations app id |
| `key_id` | password | yes (outbound) | — | **yes** | Basic auth username (API key id) |
| `key_secret` | password | yes (outbound) | — | **yes** | Basic auth password |
| `webhook_secret` | password | `listen` | — | **yes** | Integration webhook shared secret. Compared to inbound `X-API-Key` header (Sunshine Conversations OpenAPI: secret sent as `X-API-Key`) |
| `listen_triggers` | string | no | `conversation:message` | no | Comma-separated triggers to accept |
| `timeout_seconds` | number | no | `25` | no | Connect+read. `<= 0` → 25 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversation_id` | string | yes | Target conversation |
| `text` | string | if `content_type=text` | Message text |
| `content_type` | string | no | `text` (default) \| `image` \| `file` |
| `media_url` | string | image/file without upload | Public HTTPS URL Sunshine can fetch |
| `author_type` | string | no | `business` (default) |
| `author_display_name` | string | no | Optional display name |
| `destination_integration_type` | string | no | Optional channel target (`whatsapp`, etc.) |

JSON body (text): `{ "author": { "type": "business" }, "content": { "type": "text", "text": "…" } }`.

### `operation = get-conversation`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversation_id` | string | yes | Conversation id |

### `operation = list-conversations`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | string | recommended | Query `filter[userId]` |
| `page_after` | string | no | Cursor `page[after]` |
| `page_size` | number | no | Page size |

### `operation = get-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | string | yes | User id or `externalId` (path `userIdOrExternalId`) |

### `operation = update-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_id` | string | yes | User id or external id |
| `given_name` | string | no | givenName |
| `surname` | string | no | surname |
| `email` | string | no | Profile email |
| `external_id` | string | no | Link to operator’s user id |
| `properties` | object | no | Custom properties map |

### `operation = listen`

Inbound. Operator creates a Conversations integration (Admin Center) with webhook URL = StudioQuack message-event ingress, trigger at least `conversation:message`. Optional: `POST /sc/v2/apps/{app_id}/integrations` with `type: custom` and `webhooks[]` (same node, not a sibling).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `raw_body` | string | receive | Raw JSON |
| `x_api_key` | string | receive | Header `X-API-Key` |

HEAD on the target host must succeed or Zendesk rejects webhook creation.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `conversation` | object | get / send | Conversation resource |
| `conversation_id` | string | send-message | Id |
| `message` | object | send-message | Posted message |
| `conversations` | array | list-conversations | Page |
| `user` | object | get-user / update-user | User resource |
| `trigger` | string | listen | e.g. `conversation:message` |
| `payload` | object | listen | Event envelope (do not log message text) |
| `error` | string | failure | HTTP or validation; no key material |

Conversations are capped at **30,000 messages**; POST then returns **423 Locked**.

## Legal

- [Zendesk Master Subscription Agreement / Terms](https://www.zendesk.com/company/agreements-and-terms/) (confirm live URL)
- [API Terms](https://developer.zendesk.com/documentation/) via Zendesk developer programme
- Messaging traffic is **personal data** (end-user messages, names, channel ids, phone numbers on WhatsApp/SMS). Customer’s Zendesk DPA covers the workspace; this node only forwards what the workflow sends.
- Not Chat-only plans: Chat→Messaging migration is required before Chat removal (2028-01-31) for eligible Suite accounts ([Chat removal](https://support.zendesk.com/hc/en-us/articles/10837219795226-About-the-removal-of-Zendesk-Chat-and-migration-to-messaging)).
- Official-API-first: `/sc/v2` only. No Chat widget scrape.

## Implementation notes

- JDK HTTP. Basic: `Authorization: Basic base64(key_id + ":" + key_secret)`. `Accept` / `Content-Type`: `application/json`.
- Timeouts 25s default. 8 MiB body cap. No Sunshine SDK.
- 4xx/5xx → `{ error }`. Never throw past dispatch.
- `listen`: compare `X-API-Key` to `webhook_secret` with constant-time equality (Sunshine Conversations documented shared secret as `X-API-Key`). Do **not** use Support’s `X-Zendesk-Webhook-Signature` scheme unless the delivery is a **Support** webhook (wrong product).
- `subdomain` allowlist `^[a-zA-Z0-9-]+$` then host `{subdomain}.zendesk.com` only.

### Examples

Send text:

```json
{ "conversation_id": "c1", "text": "Live long and prosper" }
```

Update user (was “visitor”):

```json
{ "user_id": "u1", "given_name": "Ada", "email": "ada@example.com" }
```
