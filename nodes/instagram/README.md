# Instagram Connector

In-process StudioQuack builtin (`templateId` `a733c45d-a5b1-42e6-ac5c-2dc7a7770b65`, env `SQ_TEMPLATE_ID_INSTAGRAM`) for the official **Instagram Graph API** (Meta Graph, Instagram Professional account linked to a Facebook Page). Handler: `InstagramNode`. Live traffic is `https://graph.facebook.com/{version}/...` with the Page Access Token as query `access_token=` — never an `Authorization` header.

This is not the legacy hashtag-search node.

Provider docs: [Instagram Platform](https://developers.facebook.com/docs/instagram-platform), [Graph API](https://developers.facebook.com/docs/graph-api), [Instagram Messaging](https://developers.facebook.com/docs/messenger-platform/instagram).

## 1. Overview

Default `mode` is **`sandbox`**: canned stubs, **zero HTTP** to Meta. Set `mode=live` (config or per-call override) to call Graph. Live writes (`send_message`, `reply_comment`) **are implemented** as real POSTs; they are not code stubs. Meta **App Review** is still required before those permissions work on assets you do not admin — that is a platform gate, not a missing handler.

| Action | Sandbox (default) | Live Graph |
|---|---|---|
| `get_account_info` | Stub profile (`studioquack_ig`) | `GET /{ig_id}` fields `id,username,name,followers_count,media_count,profile_picture_url,biography` |
| `list_conversations` | `min(limit, 3)` stub threads | `GET /{page_id}/conversations?platform=instagram` |
| `list_messages` | One inbound stub DM (`conversation_id` still required) | `GET /{conversation_id}` fields `messages{id,message,from,to,created_time}` |
| `send_message` | Stub `{message_id, text}` — **no send** | `POST /{page_id}/messages` `{recipient:{id}, message:{text}}` |
| `list_comments` | One stub comment (`media_id` optional) | `GET /{media_id}/comments` |
| `reply_comment` | Stub `{reply_id, text}` — **no reply** | `POST /{comment_id}/replies` `{message}` |
| `stats` (default) | Local snapshot, no HTTP | Same shape; `base_url` is Graph. Never the PAT. |

Unknown `action` → `INVALID_INPUT` listing the seven names (alphabetically). No HTTP.

## 2. Ports

Two ports. Failures raise a BPMN error. No helper-channel calls.

| Port | Direction | Type | Required | Data flow |
|---|---|---|---|---|
| `input` | INPUT | object | no (schema) | Previous node → `inputVariables.input`. List-wrapped values unwrap to the first element. Missing → `{}` → default `action=stats`. |
| `output` | OUTPUT | object | no (schema) | Reply key `output`. Success: `{status, action, result}`. Failures raise a BPMN error. Never contains the PAT. |

### INPUT `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `action` | string | schema yes; runtime default `stats` | `stats` | Case-insensitive: `get_account_info`, `list_conversations`, `list_messages`, `send_message`, `list_comments`, `reply_comment`, `stats`. | Dispatch. Stored lower-case on the success envelope. |
| `mode` | string | no | instance config | `sandbox` \| `live` (case-insensitive). Any other non-blank string is applied as-is; **only exact `live`** triggers HTTP. | Per-call override of config `mode`. |
| `ig_business_id` | string | live `get_account_info` unless default | sandbox: `stub-ig-id` | Numeric IG Professional id. | Account lookup. |
| `page_id` | string | live conversations / send unless default | sandbox: `stub-page` | Facebook Page id. | Messaging is Page-scoped. |
| `conversation_id` | string | `list_messages` (sandbox **and** live) | — | Graph conversation id (`t_…`). | DM thread. Missing → `Error: ValueError: conversation_id required.` |
| `recipient_igsid` | string | `send_message` | — | Instagram-scoped user id. | Graph `recipient.id`. From `participants.data[].id`. |
| `media_id` | string | live `list_comments` | sandbox: `stub-media` | IG media id. | Comment listing. |
| `comment_id` | string | `reply_comment` | — | Comment id from `list_comments`. | Reply target. |
| `body` | string | `send_message`, `reply_comment` | — | UTF-8. **Not trimmed** — whitespace-only counts as present. Empty/`null` fails validation. | DM text or comment reply. Meta may reject oversize in live. |
| `limit` | number | no | `25` | Integer. Not clamped by this node. Sandbox conversations: `min(limit, 3)` (negative → `0`). | Graph `limit=` for conversations/comments. |

### OUTPUT `output` schema

| Field | Type | When present | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `action` | string | success | Echo of the dispatched (lower-case) action. |
| `result` | object | success | Sandbox stub or raw Graph JSON. `stats` never includes the PAT. |
| `error` | string | failure | Unwrapped (unknown action, missing live PAT) or `Error: ValueError: …` / `Error: ApiException: HTTP {status}: …`. |

**Sandbox `result` shapes**

| Action | `result` |
|---|---|
| `get_account_info` | `{mode, id, username:studioquack_ig, name:StudioQuack, followers_count:1234, media_count:42}` |
| `list_conversations` | `{mode, page_id, data:[{id, participants, updated_time}, …]}` |
| `list_messages` | `{mode, conversation_id, data:[{id, message, from, created_time}]}` |
| `send_message` | `{mode, page_id, recipient_igsid, message_id, text}` |
| `list_comments` | `{mode, media_id, data:[{id, text, username}]}` |
| `reply_comment` | `{mode, comment_id, text, reply_id}` |
| `stats` | `{mode, base_url:sandbox://instagram, default_page_id, default_ig_business_id}` (ids JSON `null` when unset) |

Live `result` is the Graph JSON for that endpoint. Live `stats` uses `base_url=https://graph.facebook.com/{version}`.

## 3. Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `mode` | string | no | `sandbox` | `sandbox` \| `live` (case-insensitive). | Instance default. `input.mode` overrides when non-blank. |
| `page_access_token` | password | live except `stats` | (empty) | Page Access Token from `/me/accounts`. | Query `access_token=` on live GET/POST. **Only** source of the PAT. |
| `version` | string | no | `v26.0` | `v` + digits/dots only (`v26.0`, `v25.0`, `v23.0`). Anything else (including `../`) is replaced with `v26.0` (handler fallback). Latest Graph: v26.0 (2026-07-29). v23.0 supported until 2027-10-08. | Graph path segment. Path-injection guard. |
| `timeout_seconds` | number | no | `25` | Positive integer. `≤0` → `25`. | Live HTTP timeout. |
| `default_page_id` | string | no | (empty) | Facebook Page id. | Fallback for conversations / send. Echoed on `stats`. |
| `default_ig_business_id` | string | no | (empty) | IG Professional id. | Fallback for account info. Echoed on `stats`. |

### Page Access Token

1. Create a Business-type app at [developers.facebook.com](https://developers.facebook.com/).
2. Add Messenger and/or Instagram; connect the Facebook Page linked to the Instagram Professional account.
3. A Page admin grants scopes. Facebook Login: `instagram_basic`, `instagram_manage_messages`, `pages_manage_metadata`. Instagram Login: `instagram_business_basic` + `instagram_business_manage_messages`.
4. Exchange the **User** token for a **Page** token:

```text
GET https://graph.facebook.com/v26.0/me/accounts?access_token=USER_TOKEN
```

Each Page row’s `access_token` is the PAT this node needs.

5. Optional long-lived PAT: `grant_type=fb_exchange_token` on the User token, then `/me/accounts` again.

App Review is required for production assets you do not admin.

## 4. How it works

1. **Dispatch.** Registered as `InstagramNode` under `SQ_TEMPLATE_ID_INSTAGRAM` / `a733c45d-a5b1-42e6-ac5c-2dc7a7770b65`.
2. **Mode.** Config `mode` (default `sandbox`), then `input.mode` if non-blank. Live + non-`stats` + blank config PAT → `page_access_token required for live mode (derive from /me/accounts with a User Token holding instagram_manage_messages scope).` — this check runs **before** the unknown-action check.
3. **Auth.** Live GET/POST append `access_token` last on the query string so extra keys cannot replace it. No `Authorization` header. Path ids are URL-encoded (`seg`) so they cannot inject extra Graph path components.
4. **Action.** Lower-cased; must be one of the seven names. `dispatch` switches to the matching method. `stats` never calls HTTP and never returns the PAT.
5. **Pagination.** This node does not follow Graph cursors. Pass `limit` and re-invoke; live list responses may include Graph paging fields inside `result` for a downstream node to use.
6. **Errors.** Failures raise a BPMN error.
   - Unknown action / missing live PAT: `INVALID_INPUT` (no `Error:` prefix).
   - Validation: `Error: ValueError: {message}`.
   - Transport / Graph: `Error: ApiException: HTTP {status}: {sanitized message}` (or other `SimpleName`).
7. **Rate limits.** No retry loop. Meta Graph rate-limit / policy HTTP statuses surface as `ApiException`. Bound hung calls with `timeout_seconds`. `SocialHttp` caps response bodies at 5 MiB.

## 5. Security & legal

- PAT **only** from `configurationParameters.page_access_token`. Never put a PAT on an edge.
- Never logged or returned. `SocialHttp.sanitize` redacts `access_token=` in error strings.
- Customer owns the Meta app, the Page, and the PAT on their daemon. StudioQuack does not proxy Graph for this node.
- **Meta Platform Terms:** [developers.facebook.com/terms](https://developers.facebook.com/terms)
- **Meta Platform Terms (Developer):** [developers.facebook.com/dev/terms](https://developers.facebook.com/dev/terms) and [Instagram Terms](https://help.instagram.com/581066165581870)
- Messaging and comment automation must follow Meta’s Platform Policy (no spam, consent for DMs, App Review for unowned assets). A sandbox stub is not a send.
- **GDPR:** IGSIDs, usernames, DM bodies, and comments are personal data. Process only what the workflow needs; do not log message text; retain the minimum; the customer is the controller of that data on their daemon.

## 6. Examples

### `stats` (sandbox)

Input: `{"action":"stats"}`

```json
{
  "status": "ok",
  "action": "stats",
  "result": {
    "mode": "sandbox",
    "base_url": "sandbox://instagram",
    "default_page_id": null,
    "default_ig_business_id": null
  }
}
```

### `get_account_info` (sandbox)

Input: `{"action":"get_account_info"}`

`result`: `{mode:sandbox, id:stub-ig-id, username:studioquack_ig, name:StudioQuack, followers_count:1234, media_count:42}`.

### `list_conversations` / `list_messages`

Input: `{"action":"list_conversations","page_id":"123456789012345","limit":10}`

Sandbox `result.data` has at most 3 stub rows. Then:

```json
{"action": "list_messages", "conversation_id": "t_1234567890"}
```

### `send_message` (sandbox stub — no Graph send)

```json
{
  "action": "send_message",
  "page_id": "123456789012345",
  "recipient_igsid": "12345678901234567",
  "body": "Thanks for reaching out — we will follow up shortly."
}
```

`result.message_id` is `sandbox-{epoch}`. For a real send: config `mode=live` and a PAT; same input (still **no** token on the payload).

### `list_comments` / `reply_comment`

Input: `{"action":"list_comments","media_id":"17912345678901234","limit":25}`

Reply (sandbox stub): `{"action":"reply_comment","comment_id":"17891234567890123","body":"Thanks for the comment."}` → `result.reply_id` = `sandbox-reply-{epoch}`. Live POSTs `/replies`.
