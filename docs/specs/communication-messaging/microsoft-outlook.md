# Microsoft Outlook

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/microsoft-outlook/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Graph REST **v1.0** (mail, calendar, contacts)
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/mail-api-overview?view=graph-rest-1.0
- **Auth:** `oauth2` (delegated) or `entra_app_only` (client credentials) — secret binding: `oauth_refresh_token` / `client_secret`

## Scope

Calls **Microsoft Graph v1.0** (`https://graph.microsoft.com/v1.0` or a national-cloud base) for Outlook **mail**, **mail folders**, **calendars**, and **personal contacts**. Paths use `/me/…` when the token is delegated, or `/users/{user_id}/…` for app-only (application permissions + admin consent). JDK HTTP only — no Microsoft Graph SDK.

`listen` is Graph **change notifications**: `POST /v1.0/subscriptions` against message (and optionally calendar/contact) resources, with the StudioQuack webhook ingress as `notificationUrl`. `send-and-wait` is **StudioQuack correlation** (send, then wait for an inbound reply notification matching `conversationId` / `internetMessageId`) — **not** a Graph RPC.

**v1.0 only.** Graph **beta** mail/calendar/contact verbs are **DO NOT BUILD** for this node (including deprecated `outlookTask` / To-Do-on-Outlook preview listed as preview on the throttling page). In-place archive mailboxes are unsupported by Graph.

## Operations (`operation` config values)

Base: `{graph}/v1.0` + `/me` or `/users/{user_id}`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `/me/sendMail` | Send immediately. JSON `message` + optional `saveToSentItems`. Returns 202, empty body. |
| `send-and-wait` | POST + correlate | `/me/sendMail` then wait on `listen` | Send, then StudioQuack waits for an inbound reply event (same conversation). Not a vendor method. |
| `reply-message` | POST | `/me/messages/{id}/reply` | Reply (body `comment` + optional `message`). Or `createReply` + `send` if the workflow needs a draft first — v1 uses `/reply`. |
| `get-message` | GET | `/me/messages/{id}` | One message. `$select` to avoid huge HTML bodies when possible. |
| `list-messages` | GET | `/me/messages` or `/me/mailFolders/{id}/messages` | OData `$filter`, `$top` (≤ 1000, default 10), `$skip` / `@odata.nextLink`, `$orderby`. |
| `update-message` | PATCH | `/me/messages/{id}` | Categories, `isRead`, flag, subject (drafts). |
| `delete-message` | DELETE | `/me/messages/{id}` | Move to Deleted Items (soft). |
| `move-message` | POST | `/me/messages/{id}/move` | Body `{ destinationId }` (folder id or well-known name). |
| `create-draft` | POST | `/me/messages` | Draft in Drafts (JSON or MIME). |
| `send-draft` | POST | `/me/messages/{id}/send` | Send existing draft → 202. |
| `get-draft` | GET | `/me/messages/{id}` | Same as get; caller supplies a draft id. |
| `update-draft` | PATCH | `/me/messages/{id}` | Edit a draft. |
| `delete-draft` | DELETE | `/me/messages/{id}` | Delete a draft. |
| `add-attachment` | POST | `/me/messages/{id}/attachments` | `fileAttachment`: `name`, `contentType`, `contentBytes` built from a `file` ref-id. ≤ 3 MB this path; larger → upload session **DO NOT BUILD in v1 of this node** (use Graph upload session in a later revision). |
| `get-attachment` | GET | `/me/messages/{id}/attachments/{attId}` | Metadata. Prefer `download-attachment` for bytes. |
| `list-attachments` | GET | `/me/messages/{id}/attachments` | Attachment metadata list. |
| `download-attachment` | GET | `/me/messages/{id}/attachments/{attId}/$value` | Bytes → platform `file` ref-id. Never inline on OUTPUT. |
| `create-folder` | POST | `/me/mailFolders` or `/me/mailFolders/{id}/childFolders` | `{ displayName }`. |
| `get-folder` | GET | `/me/mailFolders/{id}` | Well-known names: `Inbox`, `Drafts`, `SentItems`, `DeletedItems`. |
| `list-folders` | GET | `/me/mailFolders` | `$top`, child folders via `/mailFolders/{id}/childFolders`. |
| `update-folder` | PATCH | `/me/mailFolders/{id}` | Rename. |
| `delete-folder` | DELETE | `/me/mailFolders/{id}` | Delete folder. |
| `create-calendar` | POST | `/me/calendars` | `{ name }`. v1.0. |
| `get-calendar` | GET | `/me/calendars/{id}` | One calendar. |
| `list-calendars` | GET | `/me/calendars` | List. |
| `update-calendar` | PATCH | `/me/calendars/{id}` | `{ name, color, … }`. |
| `delete-calendar` | DELETE | `/me/calendars/{id}` | Delete (not the default calendar). |
| `create-contact` | POST | `/me/contacts` | `contact` resource (`givenName`, `emailAddresses`, …). |
| `get-contact` | GET | `/me/contacts/{id}` | One contact. |
| `list-contacts` | GET | `/me/contacts` | `$filter`, paging. |
| `update-contact` | PATCH | `/me/contacts/{id}` | Patch contact. |
| `delete-contact` | DELETE | `/me/contacts/{id}` | Delete contact. |
| `listen` | POST | `/subscriptions` | Graph change notifications. Resource e.g. `/users/{id}/messages`. Client-state secret. |

Message ids are **not** stable across move/copy unless the app uses [immutable ids](https://learn.microsoft.com/en-us/graph/outlook-immutable-id) (`Prefer: IdType="ImmutableId"`). Default: Graph ids may change after `move-message`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2` | no | `oauth2` (delegated, `/me`) \| `entra_app_only` (client credentials). |
| `tenant_id` | string | yes | — | no | Entra tenant GUID or `common` / `consumers` / `organizations` (delegated only). App-only needs a real tenant. |
| `graph_base_url` | string | no | `https://graph.microsoft.com` | no | Commercial default. US Gov: `https://graph.microsoft.us`. China: `https://microsoftgraph.chinacloudapi.cn`. |
| `client_id` | string | yes | — | no | Entra app (client) id. |
| `client_secret` | password | yes | — | **yes** | Entra client secret. Never logged. |
| `oauth_refresh_token` | password | if `oauth2` | — | **yes** | Refresh token with `offline_access`. |
| `mailbox_user_id` | string | if `entra_app_only` | — | no | UPN or GUID for `/users/{id}`. Ignored for delegated (`/me`). |
| `scopes` | string | no | `Mail.ReadWrite Mail.Send Calendars.ReadWrite Contacts.ReadWrite offline_access` | no | Space-separated. App-only uses `.default` against the app’s Graph roles (`Mail.Send`, `Mail.ReadWrite`, …). |
| `prefer_immutable_ids` | boolean | no | `true` | no | Send `Prefer: IdType="ImmutableId"` on mail calls. |
| `webhook_client_state` | password | `listen` | — | **yes** | Graph `clientState`; echoed on notifications. Compare constant-time. |
| `subscription_resource` | string | `listen` | `messages` | no | `messages` \| `mailFolders('Inbox')/messages` \| `calendars` \| `contacts`. Mapped to a Graph resource path. |
| `subscription_expiration_minutes` | number | no | `4230` | no | Graph max for Outlook items is ~3 days. Renew before expiry. |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | StudioQuack wait budget for `send-and-wait`. Not a Graph timeout. |
| `timeout_seconds` | number | no | `30` | no | Per Graph HTTP call. Attachments download uses `download_timeout_seconds`. |
| `download_timeout_seconds` | number | no | `120` | no | `$value` download. |
| `max_attachment_bytes` | number | no | `3145728` | no | 3 MiB cap for `add-attachment` (simple POST). Over → `{ error }` (upload session out of v1). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. `file` ref-ids for attachments. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | No tokens, no HTML bodies in logs. |

FUNCTION ports: none.

## Input fields (by operation)

Shared JSON message body (`send-message`, `send-and-wait`, `create-draft`, `update-draft`):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subject` | string | send: yes | Subject. |
| `body` | string | send: yes | Body content. |
| `body_content_type` | string | no | `HTML` (default) \| `Text`. |
| `to` | string | send: yes | Comma-separated SMTP addresses → `toRecipients`. |
| `cc` | string | no | Cc. |
| `bcc` | string | no | Bcc. |
| `importance` | string | no | `low` \| `normal` \| `high`. |
| `save_to_sent_items` | boolean | no | Default `true` on `sendMail`. |
| `attachments` | file[] | no | File ref-ids. Each becomes a `fileAttachment` (≤ `max_attachment_bytes` each). |

### `operation = send-message`

Shared message table. Optional `reply_to` (SMTP).

### `operation = send-and-wait`

Same as `send-message`, plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `correlation_timeout_seconds` | number | no | Overrides config wait. StudioQuack holds the token until a matching inbound message arrives on `listen` (`conversationId` equals the sent message’s conversation, or `inReplyTo` / `internetMessageId`). |
| `correlation_mailbox` | string | no | Mailbox to watch if different from sender. |

On timeout: `{ error: "send_and_wait_timeout" }` (no throw).

### `operation = reply-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Graph message id. |
| `comment` | string | yes | Reply comment (Graph `reply` body). |
| `reply_all` | boolean | no | If true, POST `/replyAll` instead of `/reply`. |

### `operation = get-message` / `get-draft` / `update-message` / `update-draft` / `delete-message` / `delete-draft` / `send-draft`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Graph id. |
| `select` | string | get only | OData `$select` (e.g. `id,subject,from,isRead,conversationId`). |

`update-message` / `update-draft` also accept `is_read` (boolean), `categories` (comma-separated), `subject`, `body`, `body_content_type`.

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `folder_id` | string | no | Well-known or folder id. Empty → mailbox-wide `/messages`. |
| `filter` | string | no | OData `$filter` (e.g. `isRead eq false`). |
| `search` | string | no | `$search` (cannot mix with `$filter` on some resources — if both set, `{ error }`). |
| `top` | number | no | 1–1000; default 25. |
| `skip` | number | no | Offset. Prefer `next_link`. |
| `next_link` | string | no | Full `@odata.nextLink` from a previous page (must start with `graph_base_url`). |
| `order_by` | string | no | e.g. `receivedDateTime desc`. |

### `operation = move-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Message to move. |
| `destination_folder_id` | string | yes | Folder id or well-known name (`Archive`, `Inbox`, …). |

### `operation = add-attachment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Parent (usually a draft). |
| `file` | file | yes | Platform ref-id. |
| `content_type` | string | no | MIME type; default from store metadata. |
| `name` | string | no | Attachment filename. |

### `operation = get-attachment` / `download-attachment`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Parent. |
| `attachment_id` | string | yes | Attachment id. |
| `filename` | string | download, no | Stored name. |

### `operation = list-attachments`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Parent. |

### Folders

| Field | Type | Required | Purpose |
|---|---|---|---|
| `folder_id` | string | get/update/delete | Target folder. |
| `display_name` | string | create/update | Folder name. |
| `parent_folder_id` | string | create, no | If set, POST childFolders. |

### Calendars

| Field | Type | Required | Purpose |
|---|---|---|---|
| `calendar_id` | string | get/update/delete | Calendar id. |
| `name` | string | create | Calendar name. |
| `color` | string | update, no | Graph calendar color enum / auto. |

### Contacts

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | get/update/delete | Contact id. |
| `given_name` | string | create | Given name. |
| `surname` | string | no | Surname. |
| `email` | string | no | Primary SMTP → `emailAddresses[0]`. |
| `business_phones` | string | no | Comma-separated. |
| `company_name` | string | no | Company. |
| `filter` | string | list, no | OData `$filter`. |
| `top` | number | list, no | Page size. |
| `next_link` | string | list, no | Next page URL. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (create subscription) \| `renew` \| `stop` (DELETE `/subscriptions/{id}`) \| `event` (ingress already validated). |
| `subscription_id` | string | renew/stop | Graph subscription id. |
| `change_types` | string | no | Default `created,updated`. Graph: `created,updated,deleted`. |

Graph sends a validation POST with `validationToken` query param — ingress must respond **plain text** 200 with that token within ~10s.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | `Outlook Graph Error: HTTP {code}` or validation. No tokens, no message HTML. |
| `http_status` | number | HTTP error | Vendor status. |
| `message_id` | string | mail writes/gets | Graph id (immutable if Prefer set). |
| `conversation_id` | string | send/get | For `send-and-wait` correlation. |
| `internet_message_id` | string | get/send (if returned) | RFC Message-ID. |
| `subject` | string | get | Subject. Do not log. |
| `is_read` | boolean | get/update | Read flag. |
| `from` | string | get | Sender SMTP. |
| `web_link` | string | get | Outlook on the web link. |
| `messages` | array | list-messages | Slim rows: `id`, `subject`, `from`, `receivedDateTime`, `isRead`, `hasAttachments`, `conversationId`. |
| `next_link` | string | lists | `@odata.nextLink`. |
| `attachment_id` | string | add/get attachment | Graph attachment id. |
| `attachments` | array | list-attachments | `{ id, name, contentType, size, isInline }` — no `contentBytes`. |
| `file` | file | download-attachment | Platform ref-id. |
| `folder_id` | string | folder ops | Folder id. |
| `folders` | array | list-folders | `{ id, displayName, totalItemCount, unreadItemCount }`. |
| `calendar_id` | string | calendar ops | Calendar id. |
| `calendars` | array | list-calendars | `{ id, name, owner }`. |
| `contact_id` | string | contact ops | Contact id. |
| `contacts` | array | list-contacts | `{ id, displayName, email }`. |
| `subscription_id` | string | listen start/renew | Persist and renew. |
| `expiration_datetime` | string | listen start/renew | Graph `expirationDateTime`. |
| `event` | object | listen `event` | `{ change_type, resource, resource_data }` — `resourceData` is often ids only unless rich notifications (encryption) are configured. **v1: ids only; follow-up GET.** |
| `reply_message_id` | string | send-and-wait success | Inbound reply id. |

## Legal

- Microsoft APIs Terms of Use: https://learn.microsoft.com/en-us/legal/microsoft-apis/terms-of-use
- Graph permissions: `Mail.ReadWrite`, `Mail.Send`, `Calendars.ReadWrite`, `Contacts.ReadWrite`. Application permissions need **admin consent**. Application `Mail.Send` can send as any user in the tenant unless an Exchange application access policy restricts it — document that blast radius to operators.
- Throttling (Outlook): **10,000 requests / 10 minutes / app+mailbox**, **4 concurrent** requests per mailbox, **150 MB upload / 5 minutes**. https://learn.microsoft.com/en-us/graph/throttling-limits
- Mailbox data is personal data. Possible PHI. National clouds keep data in-region when `graph_base_url` is set correctly; the customer is the data controller.
- Do not use mailbox APIs as a log sink. Prohibited: scraping Outlook Web, accessing in-place archives.

## Implementation notes

- JDK HTTP. Token URL: `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` (or national login hosts). Delegated: `grant_type=refresh_token`. App-only: `grant_type=client_credentials&scope={graph_base_url}/.default`.
- Headers: `Authorization: Bearer`, `Content-Type: application/json`, optional `Prefer: IdType="ImmutableId"`, `Prefer: outlook.body-content-type="text"` when stripping HTML is desired.
- `sendMail` / `send` success = **202** with empty body → `{ status: "ok" }`. Treat that as success, not an error.
- Timeouts: 30s JSON, 120s attachment `$value`. Cap download to a configured max (default 25 MiB) or `{ error: "attachment_too_large" }`.
- Retry: 429/503 with `Retry-After` (seconds). Exponential backoff cap 32s, max 4 tries. No retry on 400/401/403/404. 401 → one token refresh then retry once.
- Errors → `{ error, http_status }` on OUTPUT; never throw past dispatch.
- Logs: Graph path template, status, sizes. Never log `client_secret`, refresh tokens, message `body.content`, or attachment bytes.
- `listen` renewal: Outlook message subscriptions max ~ **3 days**. A daemon/workflow timer must PATCH `expirationDateTime`. Lifecycle notifications: if Graph requires `lifecycleNotificationUrl` for long subscriptions, set it to the same ingress.
- `send-and-wait`: after 202, store `conversationId` from a follow-up GET of the Sent Item if sendMail does not return the message (it does not). Implementation: GET `/me/mailFolders/SentItems/messages?$filter=internetMessageId eq …` or include `internetMessageHeaders` — simplest path: `create-draft` + `send-draft` so the id is known before send. **Implementer: prefer draft-then-send for correlation.**
- Large attachments (>3 MB): out of v1 (`createUploadSession` exists on Graph v1.0 for messages — first-class but heavier). Flag for a later revision; v1 returns a clear error pointing at the cap.
- Graph **beta** URL (`/beta/`) must not be used.
