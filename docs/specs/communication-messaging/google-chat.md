# Google Chat

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/google-chat/`
- **Researched:** 2026-08-20
- **Official API:** Google Chat API **v1** (`chat.googleapis.com`) + Google Workspace Events API for push `listen`
- **Docs:** https://developers.google.com/workspace/chat/api/reference/rest · Events: https://developers.google.com/workspace/events/guides/events-chat
- **Auth:** `oauth2` (user) or `service_account` (Chat app / app auth) — secret binding: `oauth_refresh_token` / `service_account_json`

## Scope

Manages Google Chat **spaces**, **memberships**, and **messages** via Chat API v1 (`https://chat.googleapis.com/v1/…`). JDK HTTP; JWT for service accounts (Sheets/Gmail pattern); **no** Google client library.

`listen` is **Google Workspace Events API** subscriptions (Pub/Sub) for `google.workspace.chat.message.v1.created` (and related types). Chat **incoming webhooks** (space webhook URLs) are send-only and are **not** `listen`. Catalog leftover “no Chat trigger” means: use **`listen` on this node** (Workspace Events / Chat app HTTP), not a second node.

`send-and-wait` is **StudioQuack correlation** after `spaces.messages.create` — wait for a following message event in the same space/thread.

**Developer Preview** Chat features (`spaces.messages.replaceCards`, customer-level Workspace Events subscriptions) are **DO NOT BUILD** for v1.

The Chat app must be installed in the target space (Workspace admin allow-list often required).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-message` | POST | `/v1/{parent=spaces/*}/messages` | Post to a space. Optional `thread` / `messageReplyOption`. |
| `get-message` | GET | `/v1/{name=spaces/*/messages/*}` | One message. |
| `update-message` | PATCH | `/v1/{message.name=spaces/*/messages/*}` | `updateMask` required. |
| `delete-message` | DELETE | `/v1/{name=spaces/*/messages/*}` | Delete (app or user auth rules apply). |
| `send-and-wait` | POST + correlate | create-message then Events | StudioQuack wait for reply in space/thread. |
| `list-messages` | GET | `/v1/{parent=spaces/*}/messages` | First-class; not in catalog seed. Caller must be a member. |
| `get-membership` | GET | `/v1/{name=spaces/*/members/*}` | One membership. |
| `list-memberships` | GET | `/v1/{parent=spaces/*}/members` | Members of a space. |
| `get-space` | GET | `/v1/{name=spaces/*}` | Space details. |
| `list-spaces` | GET | `/v1/spaces` | Spaces the caller belongs to. |
| `listen` | POST | Workspace Events `subscriptions.create` → Pub/Sub | Push events. Alternative catch-up: `GET /v1/{parent=spaces/*}/spaceEvents` (28-day history, user auth). |

Optional later on **this** node (official v1, not preview): `spaces.messages.attachments.get`, reactions create/delete, `spaces.members.create`. Not required for v1 ship.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2` | no | `oauth2` \| `service_account`. |
| `oauth_client_id` | string | oauth2 | — | no | OAuth client. |
| `oauth_client_secret` | password | oauth2 | — | **yes** | Client secret. |
| `oauth_refresh_token` | password | oauth2 | — | **yes** | Refresh token. Scopes e.g. `https://www.googleapis.com/auth/chat.messages` `chat.spaces` `chat.memberships`. |
| `service_account_json` | password | service_account | — | **yes** | Chat app service account JSON. |
| `default_space` | string | no | — | no | Resource name `spaces/{space}` or space id. |
| `pubsub_topic` | string | listen | — | no | `projects/{p}/topics/{t}` for Workspace Events. |
| `event_types` | string | listen | `google.workspace.chat.message.v1.created` | no | Comma-separated CloudEvent types. |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | Correlation wait. |
| `timeout_seconds` | number | no | `30` | no | RPC timeout. |
| `upload_timeout_seconds` | number | no | `120` | no | Attachment upload. |
| `max_upload_bytes` | number | no | `209715200` | no | Chat attachment cap (confirm live docs; fail on 400). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No secrets. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Do not log message text. |

FUNCTION ports: none.

## Input fields (by operation)

Space name: accept `spaces/AAA…` or bare id; handler prefixes `spaces/`.

### `operation = create-message` / `send-and-wait`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `space` | string | if config blank | Space name. |
| `text` | string | if no cards/attachment | Plain text. |
| `thread` | string | no | `spaces/{s}/threads/{t}` to reply. |
| `message_reply_option` | string | no | `REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD` etc. as Chat enum. |
| `attachment_resource` | string | no | Existing `spaces/…/messages/…/attachments/…`. |
| `correlation_timeout_seconds` | number | send-and-wait | Override wait. |

Cards/v2 JSON: optional `cards_v2` object for app messages — do not log.

### `operation = get-message` / `delete-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | `spaces/{space}/messages/{message}`. |

### `operation = update-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Message resource name. |
| `text` | string | no | New text. |
| `update_mask` | string | yes | e.g. `text` or `text,cards`. |

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `space` | string | yes | Parent space. |
| `page_size` | number | no | Page size. |
| `page_token` | string | no | Pagination. |
| `filter` | string | no | Chat list filter if documented for the method. |
| `order_by` | string | no | Order. |

### Membership / spaces

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | get-membership / get-space | Resource name `spaces/…/members/…` or `spaces/…`. |
| `space` | string | list-memberships | Parent. |
| `page_size` / `page_token` | number/string | list-* | Pagination. |
| `filter` | string | list-spaces, no | e.g. `spaceType = "SPACE"`. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (create Workspace Events subscription) \| `stop` \| `event` \| `list_space_events` (catch-up GET spaceEvents). |
| `target_space` | string | start | `//chat.googleapis.com/spaces/{SPACE}`. |
| `filter` | string | list_space_events | Required by Chat: at least one `eventType:` (see spaceEvents.list). History **28 days**. |

Customer (`//admin.googleapis.com/customers/…`) subscriptions are **Developer Preview** — DO NOT BUILD.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Chat/Events API error. No tokens. |
| `http_status` | number | HTTP error | Status. |
| `name` | string | message/space/member | Resource name. |
| `text` | string | get-message | Body — do not log. |
| `thread` | string | create/get | Thread name. |
| `messages` | array | list-messages | `{ name, createTime, sender_type }`. |
| `memberships` | array | list-memberships | `{ name, role, state }`. |
| `spaces` | array | list-spaces | `{ name, displayName, spaceType }`. |
| `space` | object | get-space | Space resource (ids/names). |
| `attachment` | object | upload | Attachment resource name. |
| `file` | file | download path | If implementing attachment download later. |
| `next_page_token` | string | lists | Pagination. |
| `subscription_name` | string | listen start | Workspace Events subscription. |
| `event` | object | listen | `{ event_type, space, message_name }` — ids, not full text in logs. |
| `reply_name` | string | send-and-wait | Correlated message name. |

## Legal

- Google APIs Terms: https://developers.google.com/terms
- Google Workspace user data / Chat app policies; Workspace admin must allow the Chat app.
- Chat quotas: per-minute per-project (see Cloud Console Chat API quotas). 429 → backoff.
- Space messages are personal data. Pub/Sub payloads may include message text if resource data is requested — treat as PII; do not log.
- Incoming Chat webhooks (the URL a space admin copies) are **not** a substitute for Events API `listen`.

## Implementation notes

- JDK HTTP. Service account: JWT to `https://oauth2.googleapis.com/token` with Chat scopes (app auth). User OAuth: refresh grant.
- Message create JSON: `{ text, thread, cardsV2, attachment }`.
- Timeouts 30s / 120s upload. Retry 429/503 exponential; no retry 400/401/403/404.
- Workspace Events: create subscription with Pub/Sub topic; grant publisher to Google’s Events service account as documented. Ingress verifies OIDC/push token. Decode CloudEvent; then optional `messages.get` if payload is name-only.
- `spaceEvents.list` is user-auth only and not real-time — use for backfill, not as the primary `listen`.
- HTTP errors → `{ error }` on OUTPUT; never throw past dispatch.
- Never log OAuth/service-account material or message text.
