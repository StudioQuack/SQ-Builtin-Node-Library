# Gmail

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/gmail/`
- **Researched:** 2026-08-20
- **Official API:** Gmail API v1 (`gmail.googleapis.com`)
- **Docs:** https://developers.google.com/workspace/gmail/api/reference/rest
- **Auth:** `oauth2` (user refresh) or `service_account` + domain-wide delegation (discouraged) — secret binding: `oauth_refresh_token` / `service_account_json`

## Scope

Calls the **official Gmail API v1** over HTTPS (`https://gmail.googleapis.com/gmail/v1/…`, upload host `https://gmail.googleapis.com/upload/gmail/v1/…`) to send, read, label, draft, and thread mail for one mailbox. Auth is a Google OAuth 2.0 access token minted from a stored refresh token (delegated user) or, only when a Workspace admin has enabled it, a service-account JWT with domain-wide delegation impersonating `user_id`. There is no Google client library — JDK HTTP + RS256 JWT (same pattern as Google Sheets).

`listen` is **users.watch** plus **Google Cloud Pub/Sub**. The node does not scrape IMAP/POP, does not poll `users.messages.list` as a fake trigger, and does not use Gmail MCP.

**Do not build:** IMAP/POP scrape; `users.messages.import` / `insert` (mailbox-injection, not first-class workflow send); Gmail settings/CSE/S/MIME/delegates/filters; batch admin dumps.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `/gmail/v1/users/{userId}/messages/send` (multipart upload: `/upload/gmail/v1/users/{userId}/messages/send`) | Send a new RFC 2822 message. Body is `raw` base64url MIME. Quota 100 units. |
| `reply-message` | POST | `/gmail/v1/users/{userId}/messages/send` | Reply to `message_id`. Handler loads the source, sets `threadId`, `In-Reply-To`, `References`, matching `Subject`. |
| `get-message` | GET | `/gmail/v1/users/{userId}/messages/{id}` | Fetch one message. `format=metadata\|full\|raw\|minimal`. |
| `list-messages` | GET | `/gmail/v1/users/{userId}/messages` | List ids (`q`, `labelIds`, `pageToken`, `maxResults` ≤ 500). |
| `delete-message` | DELETE | `/gmail/v1/users/{userId}/messages/{id}` | **Permanent** delete. Needs `https://mail.google.com/` (restricted). Prefer `trash-message`. |
| `trash-message` | POST | `/gmail/v1/users/{userId}/messages/{id}/trash` | Move to Trash (recoverable). First-class; not in catalog seed. |
| `untrash-message` | POST | `/gmail/v1/users/{userId}/messages/{id}/untrash` | Restore from Trash. First-class; not in catalog seed. |
| `mark-read` | POST | `/gmail/v1/users/{userId}/messages/{id}/modify` | `removeLabelIds: ["UNREAD"]`. |
| `mark-unread` | POST | `/gmail/v1/users/{userId}/messages/{id}/modify` | `addLabelIds: ["UNREAD"]`. |
| `add-label-message` | POST | `/gmail/v1/users/{userId}/messages/{id}/modify` | `addLabelIds`. |
| `remove-label-message` | POST | `/gmail/v1/users/{userId}/messages/{id}/modify` | `removeLabelIds`. |
| `get-attachment` | GET | `/gmail/v1/users/{userId}/messages/{messageId}/attachments/{id}` | Download one MIME part. OUTPUT `file` is a platform ref-id (never inline bytes). First-class; not in catalog seed. |
| `create-draft` | POST | `/gmail/v1/users/{userId}/drafts` | Create a `DRAFT`. Same MIME rules as send. |
| `send-draft` | POST | `/gmail/v1/users/{userId}/drafts/send` | Send an existing draft. Quota 100 units. First-class; not in catalog seed. |
| `get-draft` | GET | `/gmail/v1/users/{userId}/drafts/{id}` | Fetch one draft. |
| `list-drafts` | GET | `/gmail/v1/users/{userId}/drafts` | Page drafts. |
| `update-draft` | PUT | `/gmail/v1/users/{userId}/drafts/{id}` | Replace draft MIME. First-class; not in catalog seed. |
| `delete-draft` | DELETE | `/gmail/v1/users/{userId}/drafts/{id}` | Permanently delete a draft. |
| `create-label` | POST | `/gmail/v1/users/{userId}/labels` | Create a user label. |
| `get-label` | GET | `/gmail/v1/users/{userId}/labels/{id}` | Fetch one label. |
| `list-labels` | GET | `/gmail/v1/users/{userId}/labels` | List system + user labels. |
| `delete-label` | DELETE | `/gmail/v1/users/{userId}/labels/{id}` | Delete a user label (not `INBOX`/`SENT`/…). |
| `get-thread` | GET | `/gmail/v1/users/{userId}/threads/{id}` | Fetch a thread (quota 40). |
| `list-threads` | GET | `/gmail/v1/users/{userId}/threads` | List threads (`q`, `labelIds`, paging). |
| `reply-thread` | POST | `/gmail/v1/users/{userId}/messages/send` | Reply using `thread_id` (latest message headers). |
| `trash-thread` | POST | `/gmail/v1/users/{userId}/threads/{id}/trash` | Trash every message in the thread. |
| `untrash-thread` | POST | `/gmail/v1/users/{userId}/threads/{id}/untrash` | Restore the thread. |
| `delete-thread` | DELETE | `/gmail/v1/users/{userId}/threads/{id}` | Permanent thread delete (restricted scope). |
| `add-label-thread` | POST | `/gmail/v1/users/{userId}/threads/{id}/modify` | `addLabelIds` on the thread. |
| `remove-label-thread` | POST | `/gmail/v1/users/{userId}/threads/{id}/modify` | `removeLabelIds` on the thread. |
| `listen` | POST + inbound | `/gmail/v1/users/{userId}/watch` then Pub/Sub push; deltas via `GET /gmail/v1/users/{userId}/history` | Mailbox change trigger. Renew watch ≤ 7 days (daily recommended). |

`listen` is an operation on **this** node. StudioQuack correlation (send-and-wait) is **not** a Gmail RPC and is omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2` | no | `oauth2` (user refresh) \| `service_account` (Workspace DWD only). |
| `oauth_client_id` | string | if `oauth2` | — | no | Google Cloud OAuth client id. |
| `oauth_client_secret` | password | if `oauth2` | — | **yes** | OAuth client secret. Never logged. |
| `oauth_refresh_token` | password | if `oauth2` | — | **yes** | Long-lived refresh token. Token POST to `https://oauth2.googleapis.com/token`. Never on INPUT. |
| `service_account_json` | password | if `service_account` | — | **yes** | Downloaded key JSON (`client_email` + PKCS8 `private_key`). Same shape as Google Sheets. |
| `user_id` | string | no | `me` | no | Gmail `{userId}`. Use `me` for the OAuth subject. For DWD, the impersonated mailbox (email). |
| `gmail_scope` | string | no | `https://www.googleapis.com/auth/gmail.modify` | no | Space-separated scopes. Prefer `gmail.modify` (no permanent delete). Use `https://mail.google.com/` only for `delete-message` / `delete-thread`. Restricted scopes need CASA/verification. |
| `pubsub_topic` | string | `listen` | — | no | `projects/{project}/topics/{topic}`. Grant publish to `gmail-api-push@system.gserviceaccount.com`. |
| `watch_label_ids` | string | no | `INBOX` | no | Comma-separated label ids for `users.watch` (`labelIds`). |
| `watch_label_filter_behavior` | select | no | `INCLUDE` | no | `INCLUDE` \| `EXCLUDE` (`labelFilterBehavior`). |
| `timeout_seconds` | number | no | `30` | no | Connect+read timeout per Gmail/token call. 0/negative → 30. Send/upload may use `upload_timeout_seconds`. |
| `upload_timeout_seconds` | number | no | `120` | no | Timeout for `/upload/gmail/v1/…` (MIME with attachments). |
| `max_message_bytes` | number | no | `26214400` | no | Cap on assembled MIME before send (25 MiB Gmail limit). Over → `{ error }` without calling Google. |

File payloads use type `file` (platform ref-id), never inline bytes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Optional `user_id` override. Attachments are `file` ref-ids. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens, MIME `raw`, or message bodies. |

FUNCTION ports: none.

## Input fields (by operation)

Shared send/draft MIME fields (`send-message`, `reply-message`, `reply-thread`, `create-draft`, `update-draft`):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes (except `update-draft` if MIME already has To) | Comma-separated RFC 5322 addresses. Max 500 recipients across To/Cc/Bcc. |
| `cc` | string | no | Cc addresses. |
| `bcc` | string | no | Bcc addresses. |
| `from` | string | no | From header. Must be an authorized send-as alias. |
| `reply_to` | string | no | Reply-To header. |
| `subject` | string | no | Subject. Replies default to `Re: ` + source subject. |
| `body_text` | string | no | `text/plain` part. At least one of `body_text` / `body_html` required to send. |
| `body_html` | string | no | `text/html` part. |
| `attachments` | file[] | no | Platform file ref-ids. Handler reads bytes from the file store, never from INPUT JSON. Each part `Content-Disposition: attachment`. |
| `thread_id` | string | `reply-thread`; optional on send | Gmail `threadId`. Required to keep the conversation. |

### `operation = send-message`

Uses the shared MIME table. Optional `thread_id` to append to an existing thread (must also set RFC 2822 reply headers — handler does this if `in_reply_to_message_id` is set).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `in_reply_to_message_id` | string | no | If set, load that message and set `In-Reply-To` / `References` / `threadId`. |

### `operation = reply-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Source Gmail message id to reply to. |
| `body_text` / `body_html` | string | one required | Reply body. |
| `attachments` | file[] | no | Extra parts. |
| `reply_all` | boolean | no | Default `false`. If true, To = original From, Cc = original To+Cc minus self. |

### `operation = get-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Gmail message id. |
| `format` | string | no | `full` (default) \| `metadata` \| `minimal` \| `raw`. `raw` returns the MIME on OUTPUT as metadata only — do not log it. |
| `metadata_headers` | string | no | Comma-separated headers when `format=metadata` (e.g. `From,To,Subject,Date`). |

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | no | Gmail search (`from:`, `newer_than:`, `label:`). Same syntax as the Gmail UI. |
| `label_ids` | string | no | Comma-separated label ids to AND. |
| `include_spam_trash` | boolean | no | Default `false`. |
| `max_results` | number | no | 1–500; default 100. |
| `page_token` | string | no | From previous `next_page_token`. |

### `operation = delete-message` / `trash-message` / `untrash-message` / `mark-read` / `mark-unread`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Target message. |

### `operation = add-label-message` / `remove-label-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Target message. |
| `label_ids` | string | yes | Comma-separated label ids (not display names). |

### `operation = get-attachment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Parent message. |
| `attachment_id` | string | yes | Gmail attachment id from `payload.parts[].body.attachmentId`. |
| `filename` | string | no | Name stored on the platform file record. Default `attachment-{id}`. |

Handler: GET attachment → decode `data` (base64url) → write to file store → OUTPUT `file` ref-id. Never put bytes on the port.

### `operation = create-draft` / `update-draft`

Shared MIME table. `update-draft` also requires `draft_id`.

### `operation = send-draft` / `get-draft` / `delete-draft`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `draft_id` | string | yes | Draft id. |

### `operation = list-drafts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `max_results` | number | no | 1–500; default 100. |
| `page_token` | string | no | Pagination. |
| `q` | string | no | Optional draft search. |

### `operation = create-label`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Label name (may include `/` for nesting). |
| `label_list_visibility` | string | no | `labelShow` \| `labelShowIfUnread` \| `labelHide`. |
| `message_list_visibility` | string | no | `show` \| `hide`. |

### `operation = get-label` / `delete-label`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `label_id` | string | yes | e.g. `Label_12` or `INBOX` (delete of system labels → HTTP 400). |

### `operation = list-labels`

No extra fields.

### `operation = get-thread` / `trash-thread` / `untrash-thread` / `delete-thread`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `thread_id` | string | yes | Thread id. |
| `format` | string | `get-thread` only | Same as `get-message`. |

### `operation = list-threads`

Same paging/search fields as `list-messages`.

### `operation = reply-thread`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `thread_id` | string | yes | Thread to continue. |
| `body_text` / `body_html` | string | one required | Reply body. |
| `attachments` | file[] | no | File ref-ids. |

### `operation = add-label-thread` / `remove-label-thread`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `thread_id` | string | yes | Thread id. |
| `label_ids` | string | yes | Comma-separated label ids. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (default: call `users.watch`) \| `stop` (`POST …/stop`) \| `event` (inbound Pub/Sub already delivered to StudioQuack webhook ingress). |
| `history_id` | string | `event` | Previous mailbox `historyId` persisted by the workflow. Passed as `startHistoryId` to `history.list`. Older than ~7 days → 404; fall back to `list-messages`. |

Inbound Pub/Sub JSON is **not** a Gmail message body. `message.data` is base64url of `{"emailAddress","historyId"}` only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Validation or `Gmail API Error: HTTP {code}`. Never contains refresh tokens, JWT, `raw` MIME, or message bodies. |
| `http_status` | number | HTTP error | Vendor status (404, 429, …). |
| `message_id` | string | send/reply/get/trash/modify | Gmail message id. |
| `thread_id` | string | send/reply/get/thread ops | Gmail thread id. |
| `label_ids` | string[] | get-message / modify | Labels on the message. |
| `snippet` | string | get-message / get-thread | Short snippet (may be PII — do not log). Present on OUTPUT for the workflow; redacted in daemon logs. |
| `headers` | object | get-message (`metadata`/`full`) | Selected headers (`From`, `To`, `Subject`, `Date`, `Message-ID`). |
| `payload_summary` | object | get-message `full` | `{ mime_type, filename, attachment_id, size }` per part. No `data` bytes. |
| `file` | file | `get-attachment` | Platform ref-id of the stored attachment. |
| `messages` | array | list-messages | `{ id, thread_id }` only (Gmail list is ids, not bodies). |
| `threads` | array | list-threads | `{ id, snippet, history_id }`. |
| `draft_id` | string | draft ops | Draft id. |
| `label_id` | string | label create/get | Label id. |
| `labels` | array | list-labels | `{ id, name, type, messages_total, messages_unread }`. |
| `next_page_token` | string | list-* | Pass back as `page_token`. |
| `result_size_estimate` | number | list-messages / list-threads | Gmail estimate. |
| `history_id` | string | get/watch/history | Mailbox cursor. Persist for `listen`. |
| `watch_expiration` | string | `listen` `start` | Unix ms from `users.watch`. Renew before this. |
| `history` | array | `listen` `event` | `history.list` records (`messagesAdded` / `labelsAdded` / …) **ids only**. Fetch bodies with `get-message` if the workflow needs them. |

## Legal

- Google APIs Terms of Service: https://developers.google.com/terms
- Gmail API authorizing / scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Google Workspace user data & developer policy (restricted Gmail scopes, Limited Use, CASA): https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Restricted-scope verification: https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification
- Quota: https://developers.google.com/workspace/gmail/api/reference/quota — **1,200,000 units/min/project**, **6,000 units/min/user/project**. `messages.send` / `drafts.send` / `watch` = 100 units; `threads.get` = 40; `messages.get` = 20; `messages.list` = 5. Daily project threshold 80,000,000 units (billing for overage planned later in 2026 — confirm live quota page).
- 500 recipients per message. Typical Gmail size cap **25 MB**.
- Mailbox content is personal data (GDPR). Treat as possible PHI; do not put ePHI in Gmail unless the customer’s Google Workspace / BAA covers that mailbox. This node adds TLS only.
- Prohibited: spam, scraping Gmail HTML, using multiple accounts to bypass limits, storing non-mail data in Gmail as a backup store (Workspace user-data policy).
- Official-API-first: no IMAP scrape, no unofficial Gmail web wrappers.

## Implementation notes

- JDK HTTP only. Token: POST `application/x-www-form-urlencoded` to `https://oauth2.googleapis.com/token`. Cache access tokens in memory until `expires_in` minus 60s; never persist access tokens. Service-account JWT: `iss=client_email`, `sub=user_id` (DWD), `scope=gmail_scope`, `aud=https://oauth2.googleapis.com/token`, RS256 PKCS8 (Sheets pattern).
- Build MIME in the handler (`multipart/mixed` + `multipart/alternative`). `raw` is URL-safe base64 without padding. Attachments: resolve `file` ref-ids from the platform file store; cap total size with `max_message_bytes`.
- `Authorization: Bearer {access_token}`. `userId` path-encoded; default `me`.
- Timeouts: `timeout_seconds` (default 30) for token + JSON calls; `upload_timeout_seconds` (default 120) for media upload.
- Retry: HTTP 429 / 503 → truncated exponential backoff (Gmail docs: 1s, 2s, 4s, … cap 32s, jitter ≤ 1s), honor `Retry-After`. Do **not** retry 400/401/403/404. Max 4 attempts on 5xx/429.
- HTTP 4xx/5xx → `{ error, http_status }` on OUTPUT. Never throw past dispatch.
- Logs: method, path template (no ids if they embed email), status, byte sizes, quota units. **Never log** tokens, `raw`, `body_text`, `body_html`, snippets, headers, or file bytes.
- `listen`: call `watch` at workflow start and on a daily timer; persist `historyId` + `expiration`. Pub/Sub push is delivered through the StudioQuack engine's message-event ingress. Verify the push is from the operator’s Pub/Sub subscription (OIDC or shared token on the ingress). Decode `message.data`, then `history.list?startHistoryId=`. Acknowledge Pub/Sub with HTTP 2xx quickly; do heavy `get-message` asynchronously in the workflow. Notification rate cap: 1 event/second/user (extras dropped) — design for missed events via periodic `history.list`.
- Egress: `oauth2.googleapis.com`, `gmail.googleapis.com`, and (listen) Pub/Sub push host only as configured.
