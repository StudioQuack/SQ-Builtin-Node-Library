# Bluesky Search

In-process StudioQuack builtin (`templateId` `24a7c5c6-9070-4d0b-91c4-2e386f1c8dee`, env `SQ_TEMPLATE_ID_BLUESKY`) that searches Bluesky through the official **AT Protocol** XRPC AppView. Handler: `BlueskyNode`. Auth is optional app-password session (`com.atproto.server.createSession`); the `accessJwt` is used for that invoke only and never cached, logged, or returned.

This node is **read-only**. It does not post, like, follow, or reply. Those writes are not implemented (not sandbox stubs).

Provider docs: [Bluesky API](https://docs.bsky.app), [AT Protocol lexicons](https://atproto.com/specs/lexicon), HTTP XRPC: `app.bsky.feed.searchPosts`, `app.bsky.actor.searchActors`, `app.bsky.feed.getAuthorFeed`.

## 1. Overview

| Action | Status | XRPC | Required input |
|---|---|---|---|
| `search_posts` (default) | **Implemented** | `GET app.bsky.feed.searchPosts` | `query` or alias `q` |
| `search_actors` | **Implemented** | `GET app.bsky.actor.searchActors` | `query` or alias `q` |
| `get_author_feed` | **Implemented** | `GET app.bsky.feed.getAuthorFeed` | `actor` (handle or DID) |
| createPost / like / reply | **Not implemented** | — | Not a stub. |

There is no `sandbox` mode. Calls are live XRPC (public AppView or authenticated AppView).

| Auth | When | Base | Retry |
|---|---|---|---|
| Authenticated | both `bluesky_handle` and `bluesky_app_password` non-blank | `https://api.bsky.app/xrpc` after `POST https://bsky.social/xrpc/com.atproto.server.createSession` | Up to 3 GETs on HTTP **429** only (sleep 2s / 4s / 6s). **No** public fallback. |
| Public | either credential blank | `https://public.api.bsky.app/xrpc` | Up to 3 GETs on HTTP **429** or **403**, same sleep. |

Unauthenticated HTTP 403 often means Bluesky load-reduction: set an app password.

## 2. Ports

Two ports. No error OUTPUT port. Failures set `output.error`. No helper-channel calls.

| Port | Direction | Type | Required | Data flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Previous node → `inputVariables.input`. List unwraps to first element. |
| `output` | OUTPUT | object | yes | Reply key `output`. Success: `status=ok` plus posts or actors, a count, and `cursor`. Failure: `{error}` only. Never contains `accessJwt` or the app password. |

### INPUT `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `action` | string | no | `search_posts` | Case-sensitive: `search_posts`, `search_actors`, `get_author_feed`. | Dispatch. Other values → `Unknown action: {action}. Use search_posts, search_actors, or get_author_feed.` |
| `query` | string | search actions unless `q` | — | Keyword/hashtag text. | XRPC `q=`. |
| `q` | string | handler alias | — | Same as `query`. **Not a bundled schema field**; read when `query` is blank. | Alias of `query`. |
| `sort` | string | no | `latest` | `latest` (reverse-chronological) \| `top` (engagement). Other strings forwarded. | `search_posts` only. |
| `since` | string | no | omitted | Blank → omit. Date-only `YYYY-MM-DD` → UTC midnight that day. Other ISO-8601 → UTC milliseconds + `Z` (naive = UTC). | Inclusive start for `search_posts`. Bluesky rejects bare dates; this node expands them. |
| `until` | string | no | omitted | Same shapes. Date-only adds **one day** so that calendar day is inclusive (`2026-03-15` → `2026-03-16T00:00:00.000Z`). Full timestamps are not shifted. | End of `search_posts` window. |
| `lang` | string | no | omitted | ISO 639-1 (`en`, `es`, `ja`). | `search_posts` language filter. |
| `limit` | number | no | config `default_limit` (`50`) | Integer. Values `> 100` capped at 100. | Page size for all three actions. |
| `cursor` | string | no | omitted (first page) | Opaque token from previous `output.cursor`. Do not invent or reuse across actions. | Pagination. |
| `actor` | string | `get_author_feed` | — | Handle (`jay.bsky.team`) or DID (`did:plc:…`). | Whose feed to read. |

Invalid ISO `since`/`until` → `Error: IllegalArgumentException: Invalid ISO datetime filter: {value}`.

### OUTPUT `output` schema

| Field | Type | When present | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `posts_returned` | number | `search_posts`, `get_author_feed` | Length of `posts[]`. |
| `actors_returned` | number | `search_actors` | Length of `actors[]`. |
| `cursor` | string \| null | success | Next-page token. Null/absent = last page. |
| `posts` | array | post actions | Normalized posts. |
| `actors` | array | `search_actors` | Normalized profiles. |
| `error` | string | failure | See error prefixes below. |

**`posts[]` (`search_posts`):** `uri`, `text`, `created_at`, `like_count`, `repost_count`, `reply_count`, `author_handle`, `author_display_name`, `indexed_at`, `embed` (lexicon object or `null`).

**`posts[]` (`get_author_feed`):** `uri`, `text`, `created_at`, `like_count`, `repost_count`, `reply_count`, `author_handle` only (no display name / indexed_at / embed).

**`actors[]`:** `handle`, `display_name`, `description`, `followers_count`, `follows_count`, `posts_count`, `avatar`. Counts default to `0`.

## 3. Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `bluesky_handle` | string | with app password for auth | (empty) | Handle, custom-domain handle, or the email Bluesky accepts for login. | `createSession` `identifier`. Blank with blank password → public API. |
| `bluesky_app_password` | password | with handle for auth | (empty) | **App password**, not the account login password. | Session `password`. Blank → public API. |
| `timeout_seconds` | number | no | `25` | Positive integer. `≤0` / non-numeric → `25`. | Per-request HTTP timeout (session + each GET). Retry sleeps are extra. |
| `default_limit` | number | no | `50` | Integer. Effective limit still capped at 100. | Page size when `input.limit` is omitted. |

The bundled definition does **not** include `user_agent`. If an instance adds that shortCode, the handler uses it; otherwise `StudioQuack-BlueskySearch/1.0 (+https://studioquack.com)`.

App password setup: [bsky.app](https://bsky.app) → Settings → App Passwords → Add App Password. Copy once.

## 4. How it works

1. **Dispatch.** `BlueskyNode` under `SQ_TEMPLATE_ID_BLUESKY` / `24a7c5c6-9070-4d0b-91c4-2e386f1c8dee`.
2. **Auth.** If both credentials are set: `POST {identifier, password}` to `com.atproto.server.createSession`. HTTP 401 → `Bluesky Auth Error:` with the handle (never the password) and a link to app-password settings. Missing `accessJwt` → `Bluesky Auth Error: Bluesky session response missing accessJwt` (body not dumped). Subsequent XRPC GETs send `Authorization: Bearer {jwt}`, `Accept: application/json`, and the User-Agent.
3. **Action.** Case-sensitive switch. Search actions require a non-empty query; author feed requires `actor`.
4. **Date filters.** Date-only `YYYY-MM-DD` is expanded to UTC midnight; `until` adds one day. Other ISO values truncate to milliseconds with a `Z` suffix.
5. **Pagination.** Omit `cursor` on the first page. If `output.cursor` is a string, send it back unchanged for the **same** action. Null means last page.
6. **Errors.** `handle` never throws.
   - Validation: no prefix (`query is required…`, `actor (handle or DID) is required…`, unknown action).
   - Session: `Bluesky Auth Error: …`.
   - XRPC: `Bluesky API Error: …`. Unauthenticated HTTP 403 appends the public AppView load-reduction hint.
   - Other: `Error: {SimpleName}: {message}`.
7. **Rate limits.** Authenticated: retry **429** only, three attempts, sleep `2*(attempt+1)` seconds. Public: retry **429 and 403**. No fallback from auth failures to the public host.

## 5. Security & legal

- App password **only** from `configurationParameters.bluesky_app_password`. Never on INPUT. Never log handle+password together; never return `accessJwt`.
- Use an app password, not the account password. Revoke it at bsky.app if leaked.
- Customer owns the Bluesky account on their daemon. Session JWTs are not stored across invokes.
- **Bluesky Terms of Service:** [bsky.social/about/support/tos](https://bsky.social/about/support/tos)
- **Developer / rate-limit guidance:** [docs.bsky.app](https://docs.bsky.app/docs/advanced-guides/rate-limits)
- Do not harvest profiles for unsolicited contact; honor AppView rate limits; this node must not be used to spam (and cannot post).
- **GDPR:** handles, display names, post text, and avatars can identify a person. Process only what the workflow needs; do not keep a parallel user directory.

## 6. Examples

### `search_posts`

Input:

```json
{
  "action": "search_posts",
  "query": "workflow automation",
  "sort": "latest",
  "since": "2026-03-01",
  "until": "2026-03-15",
  "lang": "en",
  "limit": 25
}
```

(`until` `2026-03-15` is sent as `2026-03-16T00:00:00.000Z`.)

Output:

```json
{
  "status": "ok",
  "posts_returned": 1,
  "cursor": null,
  "posts": [
    {
      "uri": "at://did:plc:example/app.bsky.feed.post/abc",
      "text": "…",
      "created_at": "2026-03-02T12:00:00.000Z",
      "like_count": 4,
      "repost_count": 1,
      "reply_count": 0,
      "author_handle": "user.bsky.social",
      "author_display_name": "User",
      "indexed_at": "2026-03-02T12:00:01.000Z",
      "embed": null
    }
  ]
}
```

### `search_actors`

Input: `{"action":"search_actors","query":"studioquack","limit":10}` → `actors_returned`, `actors[]` (`handle`, `display_name`, counts, `avatar`), `cursor`.

### `get_author_feed`

Input: `{"action":"get_author_feed","actor":"jay.bsky.team","limit":25}`. Next page: add `"cursor":"<previous output.cursor>"`. Author-feed rows omit `author_display_name`, `indexed_at`, and `embed`.
