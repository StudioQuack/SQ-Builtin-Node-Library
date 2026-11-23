# X (Twitter) Connector

In-process StudioQuack builtin (`templateId` `f534a86d-ebd3-475b-a7e9-0d8b650751b1`, env `SQ_TEMPLATE_ID_TWITTER`) for the official **X (Twitter) API v2** at `https://api.x.com/2`. Handler: `TwitterNode`. App-only Bearer for public reads; user access token from **OAuth 2.0 Authorization Code with PKCE** for DMs and writes.

Provider docs: [X API v2](https://docs.x.com), [pricing / pay-per-usage](https://developer.x.com/#pricing), [OAuth 2.0](https://docs.x.com/fundamentals/authentication/oauth-2-0/overview), [recent search](https://docs.x.com/x-api/posts/search/integrate/recent-search).

## 1. Overview

Default `mode` is **`sandbox`**: canned stubs, **zero HTTP** to X. Set `mode=live` to call `api.x.com`. Live writes (`post_tweet`, `reply_tweet`, `send_dm`) **are implemented** as real POSTs; they are not code stubs. X product access, app review, and [automation rules](https://help.x.com/en/rules-and-policies/x-automation) still apply before those endpoints work on a production app — that is a platform gate, not a missing handler.

| Action | Aliases | Sandbox | Live |
|---|---|---|---|
| `get_user` | | Stub user `id=9999999` | Bearer or user. `GET /2/users/by/username/{handle}` (`user.fields=id,name,username,description,public_metrics,verified,created_at`). Handle URL-encoded. |
| `list_mentions` | | `min(limit, 3)` stub tweets | Bearer preferred, else user. `GET /2/users/{id}/mentions`. `max_results` clamped 5–100. `since` → `start_time`. |
| `list_dm_events` | | One `MessageCreate` stub; `conversation_id` **not** required | **User token.** `GET /2/dm_conversations/{id}/dm_events` or `GET /2/dm_events` if id blank. |
| `send_dm` | | Stub `dm_event_id` — **no send** | **User token.** `POST /2/dm_conversations/with/{id}/messages` `{"text"}`. |
| `search_tweets` | `search_recent` | One stub tweet | Bearer preferred, else user. `GET /2/tweets/search/recent`. `max_results` clamped 10–100. ~7-day window unless the X tier includes more. |
| `reply_tweet` | `reply` | Stub `tweet_id` — **no reply** | **User token.** `POST /2/tweets` with `reply.in_reply_to_tweet_id`. |
| `post_tweet` | | Stub `tweet_id` — **no post** | **User token.** `POST /2/tweets` `{"text"}`. |
| `stats` (default) | | Local snapshot | Same shape; `base_url` is `https://api.x.com/2`. Booleans only for tokens. |

Unknown action (after the live-token gate) returns the Python-shaped list including aliases:

`Unknown action '{a}'. Valid: ['get_user', 'list_dm_events', 'list_mentions', 'post_tweet', 'reply', 'reply_tweet', 'search_recent', 'search_tweets', 'send_dm', 'stats']`

## 2. Ports

Two ports. No error OUTPUT port. Failures set `output.error`. No helper-channel calls.

| Port | Direction | Type | Required | Data flow |
|---|---|---|---|---|
| `input` | INPUT | object | no (schema) | Previous node → `inputVariables.input`. Missing → `{}` → `action=stats`. |
| `output` | OUTPUT | object | no (schema) | Reply key `output`. Success: `{status, action, result}`. Failure: `{error}`. Tokens never appear; `stats` may set `has_bearer` / `has_user_token` booleans only. |

### INPUT `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `action` | string | schema yes; runtime default `stats` | `stats` | Case-insensitive: table above (including aliases). Echoed as the requested alias (`search_recent` stays `search_recent`). | Dispatch. |
| `mode` | string | no | instance config | `sandbox` \| `live`. Any other non-blank string is **not-live** (stubs), matching Python `mode != 'live'`. | Per-call override. |
| `handle` | string | `get_user` | — | Username without `@` preferred; a leading `@` is sent as-is. URL-encoded in the path. | User lookup. |
| `user_id` | string | `list_mentions` unless default | — | X snowflake decimal string. | Mentions target. |
| `conversation_id` | string | live optional | sandbox: `sandbox-conv` if blank | Conversation snowflake. | DM events: specific thread vs all events. |
| `recipient_id` | string | `send_dm` | — | User snowflake. | DM recipient. |
| `in_reply_to_tweet_id` | string | `reply_tweet` unless alias id | — | Tweet snowflake. Fallbacks (not separate schema fields): `tweet_id`, `parent_id` — first non-blank wins. | Reply parent. |
| `body` | string | `send_dm`, `post_tweet`, `reply_tweet` | — | UTF-8. **Whitespace preserved** (not trimmed). Empty/`null` fails. Handler does not truncate; X enforces length. | Post / DM / reply text. |
| `query` | string | `search_tweets` / `search_recent` | — | X query syntax (`from:`, `lang:`, `-is:retweet`, …). Read with `raw()` (not trimmed). | Recent search. |
| `since` | string | no | omitted | RFC 3339. Live `list_mentions` only → `start_time`. Ignored in sandbox and other actions. | Mentions lower bound. |
| `limit` | number | no | `25` | Integer. Sandbox mentions: `min(limit, 3)`. Live mentions: `min(max(n,5),100)`. Live search: `min(max(n,10),100)`. Live DM events: unclamped (X may 400). | Row cap. |

### OUTPUT `output` schema

| Field | Type | When present | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `action` | string | success | Echo of the requested alias. |
| `result` | object | success | Sandbox stub or raw X v2 JSON. |
| `error` | string | failure | Bare (missing live tokens, unknown action) or `Error: ValueError: …` / `Error: ApiException: HTTP {status}: …`. |

**Sandbox `result` shapes**

| Action | `result` |
|---|---|
| `get_user` | `{mode, data:{id:"9999999", username, name:"Sandbox User"}}` |
| `list_mentions` | `{mode, user_id, data:[{id, author_id, text}, …]}` |
| `list_dm_events` | `{mode, conversation_id, data:[{id, event_type:MessageCreate, text, sender_id}]}` |
| `send_dm` | `{mode, recipient_id, text, dm_event_id}` |
| `search_tweets` / `search_recent` | `{mode, query, data:[{id, author_id, text}]}` |
| `post_tweet` | `{mode, text, tweet_id:sandbox-tw-{epoch}}` |
| `reply_tweet` / `reply` | `{mode, text, in_reply_to_tweet_id, tweet_id:sandbox-reply-{epoch}}` |
| `stats` | `{mode, base_url:sandbox://x, has_bearer, has_user_token, default_user_id or null}` |

Live `result` is the X API JSON (user object, tweet list, DM events, create response). Live headers: `Authorization: Bearer {token}`, `Content-Type: application/json`.

## 3. Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `mode` | string | no | `sandbox` | `sandbox` \| `live`. Other non-blank → not-live. | Instance default. `input.mode` overrides when non-blank. |
| `bearer_token` | password | live reads unless user token | (empty) | X App-only Bearer. | `Authorization` for `get_user`, `list_mentions`, `search_tweets`. Cannot authorize DMs/writes. |
| `user_access_token` | password | live DMs/writes | (empty) | OAuth 2 PKCE access token. Scopes: `tweet.read` `users.read` `dm.read` `dm.write` `tweet.write` `offline.access`. | DMs and posts; also usable for public reads. |
| `timeout_seconds` | number | no | `25` | Positive integer. `≤0` / non-numeric → `25`. Unused in sandbox. | Live HTTP timeout. |
| `default_user_id` | string | no | (empty) | User snowflake. | Fallback for `list_mentions`. Echoed on `stats` or JSON `null`. |

Live + neither config token + `action != stats` → `bearer_token or user_access_token required for live mode.` This runs **before** the unknown-action check.

**Bearer:** [console.x.com](https://console.x.com) → Project + App → Keys and Tokens → Bearer Token. Pay-per-usage applies on v2.

**User token (PKCE):** enable OAuth 2.0 on the app (Read and write and Direct message), set callback + website, then authorize at `https://x.com/i/oauth2/authorize` with `code_challenge_method=S256` and exchange at `POST https://api.x.com/2/oauth2/token`. Refresh with `grant_type=refresh_token`. Bind the access token as a StudioQuack secret.

Token preference in live: `get_user` prefers **user** token then Bearer; `list_mentions` and `search_tweets` prefer **Bearer** then user.

## 4. How it works

1. **Dispatch.** `TwitterNode` under `SQ_TEMPLATE_ID_TWITTER` / `f534a86d-ebd3-475b-a7e9-0d8b650751b1`.
2. **Cfg.** Mode from config (default `sandbox`), then `input.mode`. Tokens **only** from config password fields.
3. **Live gate.** Missing both tokens and not `stats` → bare error. Then action must be in the alias set.
4. **Auth headers.** `Authorization: Bearer {chosen token}` + `Content-Type: application/json`. Path segments are URL-encoded.
5. **Pagination.** This node does not follow X `meta.next_token`. Re-invoke with `limit` / `since`; live list JSON may include `meta` for a downstream node.
6. **Errors.** `handle` never throws. `InterruptedException` restores the interrupt flag. Validation → `Error: ValueError: …`. HTTP → `Error: ApiException: HTTP {status}: {sanitized X message}`.
7. **Rate limits.** No retry/backoff in this handler. X 429 surfaces as `ApiException`. Bound bodies: 5 MiB. Bound hung calls with `timeout_seconds`.

## 5. Security & legal

- Tokens **only** from `configurationParameters`. Never put tokens on edges.
- Never logged or returned. `stats` exposes presence booleans only. `SocialHttp.sanitize` redacts bearer/token query fragments.
- Customer owns the X app and tokens on their daemon.
- **X Developer Agreement:** [developer.x.com/en/developer-terms/agreement](https://developer.x.com/en/developer-terms/agreement)
- **X Developer Policy:** [developer.x.com/en/developer-terms/policy](https://developer.x.com/en/developer-terms/policy)
- **Automation rules:** [help.x.com — X automation](https://help.x.com/en/rules-and-policies/x-automation) — no spam, no engagement bait, disclose bots where required, respect user blocks/mutes, do not evade rate limits.
- A sandbox stub is not a post or DM. Live writes count against the app’s policy and quota.
- **GDPR:** handles, user ids, tweet text, and DMs are personal data (DMs especially). Process only what the workflow needs; do not log DM bodies; the customer is the controller of that data on their daemon.

## 6. Examples

### `stats` / `get_user` (sandbox)

Input: `{"action":"get_user","handle":"studioquack"}`

```json
{
  "status": "ok",
  "action": "get_user",
  "result": {
    "mode": "sandbox",
    "data": {"id": "9999999", "username": "studioquack", "name": "Sandbox User"}
  }
}
```

### `search_tweets`

Input: `{"action":"search_recent","query":"@studioquack -is:retweet lang:en","limit":10}`

Sandbox `result.data` has one stub tweet. Live (Bearer on config, `mode=live`) returns X recent-search JSON.

### `list_mentions`

Input: `{"action":"list_mentions","user_id":"44196397","limit":25,"since":"2026-08-01T00:00:00Z"}`

Sandbox ignores `since` and returns at most 3 stubs.

### `post_tweet` / `reply_tweet` / `send_dm` (sandbox stubs)

```json
{"action": "post_tweet", "body": "Hello from StudioQuack"}
```

`result.tweet_id` is `sandbox-tw-{epoch}`. Reply: `{"action":"reply","body":"Thanks","in_reply_to_tweet_id":"1770000000000000000"}`. DM: `{"action":"send_dm","recipient_id":"1234567","body":"Hello"}`.

For live writes, bind `user_access_token`, set `mode=live`, and keep tokens off the payload.
