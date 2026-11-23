# Reddit Search

In-process StudioQuack builtin (`templateId` `309005be-14d0-4b62-8b0c-ac65d43df7d0`, env `SQ_TEMPLATE_ID_REDDIT`) that calls the official **Reddit API** (public JSON or OAuth 2.0). Handler: `RedditNode`. Purpose: pull community posts into a workflow. Tokens are fetched **per invoke** and never cached, logged, or returned.

This node is **read-only**. It does not submit posts, vote, or comment. Those writes are not implemented (not sandbox stubs).

Provider docs: [Reddit Data API Wiki](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki), [Data API Terms](https://redditinc.com/policies/data-api-terms), [OAuth 2](https://github.com/reddit-archive/reddit/wiki/OAuth2), app registration at [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps). Unauthenticated JSON is **deprecated** (official wiki: traffic without OAuth is blocked).

## 1. Overview

| Action | Status | Path | Notes |
|---|---|---|---|
| `search` (default) | **Implemented** | `/search` or `/r/{subreddit}/search` | `type=link`. `restrict_sr=true` when `subreddit` is set. |
| `listing` | **Implemented** | `/r/{subreddit}/{listing}` | `hot` \| `new` \| `top` \| `rising` (anything else → `hot`). |
| Submit / vote / comment | **Not implemented** | — | Not a stub. |

Two transports:

| Mode | When | Base | Typical cap |
|---|---|---|---|
| **Public (deprecated)** | `client_id` or `client_secret` blank | `https://old.reddit.com{path}.json` then `https://www.reddit.com{path}.json` | Official wiki: unauthenticated traffic is blocked. Often 403. |
| **OAuth2** | both `client_id` and `client_secret` set | Token `POST https://www.reddit.com/api/v1/access_token`, then `GET https://oauth.reddit.com{path}` (no `.json`) | ~100 QPM per OAuth client id (10-minute average). |

OAuth grant: `grant_type=password` when **both** username and password are set; otherwise `grant_type=client_credentials`. Register a **script** app. Reddit may require [Responsible Builder Policy](https://support.reddithelp.com/hc/en-us/articles/24352026345108-Responsible-Builder-Policy) approval.

## 2. Ports

Two ports. Failures raise a BPMN error. No helper-channel calls.

| Port | Direction | Type | Required | Data flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Previous node → `inputVariables.input`. List unwraps to first element. Missing/non-object → `{}` then required-field check. |
| `output` | OUTPUT | object | yes | Reply key `output`. Success: `{status, auth_mode, posts_returned, after, posts}`. Failures raise a BPMN error. Never contains `access_token`, `password`, or `client_secret`. |

### INPUT `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `action` | string | no | `search` | Case-sensitive: `search`, `listing`. | Dispatch. Other strings → `Unknown action: {action}. Use 'search' or 'listing'.` |
| `query` | string | `search` unless `q` | — | Reddit search syntax allowed (`title:"workflow" self:yes`). | Sent as `q=`. |
| `q` | string | handler alias | — | Same as `query`. **Not a bundled schema field**; the handler still reads it when `query` is blank. | Alias of `query`. |
| `subreddit` | string | `listing`; optional for search | — | Name **without** `r/`. | Scopes search (`restrict_sr=true`) or selects the listing. |
| `sort` | string | no | `relevance` | `relevance`, `hot`, `top`, `new`, `comments`. Other strings forwarded. | Search `sort=` only. Ignored for listing. |
| `time` | string | no | `day` | `hour`, `day`, `week`, `month`, `year`, `all`. Other strings forwarded. | Search and listing `t=` (meaningful for `top`). |
| `limit` | number | no | `25` | Integer. Capped `min(n, 100)`. No floor. | Page size. |
| `listing` | string | no | `hot` | `hot`, `new`, `top`, `rising`. Any other value → `hot`. | Listing path segment. Ignored for search. |
| `after` | string | no | omitted (first page) | Reddit fullname, e.g. `t3_abc123`. | Next-page cursor. |

### OUTPUT `output` schema

| Field | Type | When present | Purpose |
|---|---|---|---|
| `status` | string | success | Exact `ok`. |
| `auth_mode` | string | success | `oauth` or `public`. |
| `posts_returned` | number | success | Length of this page’s `posts[]`. |
| `after` | string \| null | success | Next-page fullname (`data.after`). Null when Reddit reports no further page. |
| `posts` | array | success | Normalized rows from `children[].data`. |

**`posts[]` row**

| Field | Type | Notes |
|---|---|---|
| `title` | string | Headline. Empty when omitted. |
| `selftext` | string | Capped at 500 characters. Empty for link posts. |
| `url` | string | `data.url` (outbound or Reddit). |
| `permalink` | string | `https://www.reddit.com` + `data.permalink`. |
| `score` | number | Net upvotes (long). Missing → `0`. |
| `num_comments` | number | Long. Missing → `0`. |
| `created_utc` | number | Unix seconds (may be fractional). Missing → `0`. |
| `subreddit` | string | Without `r/`. |
| `author` | string | May be `[deleted]`. |
| `is_self` | boolean | Text post vs link. Missing → `false`. |
| `link_flair_text` | string \| null | May be JSON `null`. |

## 3. Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `client_id` | string | no (needed with secret for OAuth) | (empty) | Script-app client id from `/prefs/apps`. | Together with `client_secret`, switches to OAuth. |
| `client_secret` | password | no (needed with id for OAuth) | (empty) | Script-app secret. | HTTP Basic against the token URL. |
| `username` | string | no | (empty) | Reddit username without `u/`. | Password grant when `password` is also set. |
| `password` | password | no | (empty) | Account password. If 2FA is on, append `:TOTP` (`password:123456`). | Password grant only. |
| `user_agent` | string | no | `StudioQuack:v1.0 (by /u/studioquack)` | Reddit unique UA: `platform:appid:version (by /u/yourusername)`. | Required on every call. Generic UAs are 429/blocked. **Set this to your own username in production.** |
| `timeout_seconds` | number | no | `25` | Positive integer. `≤0` → `25`. | Token POST and listing GET timeout. |

OAuth2 is used only when **both** `client_id` and `client_secret` are non-blank. Username/password alone do not enable OAuth.

## 4. How it works

1. **Dispatch.** `RedditNode` under `SQ_TEMPLATE_ID_REDDIT` / `309005be-14d0-4b62-8b0c-ac65d43df7d0`.
2. **Auth construction.**
   - OAuth: `SocialHttp.basicAuthHeader(client_id, client_secret)` + `User-Agent` → `POST` form to the token URL. Bearer on `GET oauth.reddit.com{path}`. Missing `access_token` in the JSON → `Reddit OAuth returned no access_token` (body is never echoed).
   - Public: `User-Agent`, `Accept: application/json`, `Accept-Language: en-US,en;q=0.9`. Two attempts per domain (`old` then `www`).
3. **Action.** Builds `path` + query (`q`, `sort`, `t`, `limit`, `type=link`, optional `restrict_sr`, optional `after`).
4. **Pagination.** Success `after` is Reddit’s listing fullname. Pass it back as `input.after` for the same action. Omit on the first page.
5. **Errors.** Failures raise a BPMN error.
   - Validation: exact strings `query is required for search action.`, `subreddit is required for listing action.`, unknown-action, `Reddit OAuth returned no access_token`.
   - HTTP: `Reddit API Error: {message}`. Status 403 / body containing `403` or `Blocked` appends ` | Reddit blocks unauthenticated access. Configure OAuth2 (client_id, client_secret) for reliable results.`
   - Both public domains exhausted: `All Reddit public endpoints returned errors. Configure OAuth2 credentials for reliable access.`
6. **Rate limits.** Public HTTP **429** sleeps `2*(attempt+1)` seconds and retries (max 2 attempts per domain). HTTP **403** or transport status `0` moves to the next domain. OAuth GETs do not retry in this handler. Bound bodies: 5 MiB (`SocialHttp`); `selftext` capped at 500 chars.

## 5. Security & legal

- `client_secret`, `password`, and access tokens live only in `configurationParameters` (or the per-invoke token response). Never on INPUT. Never logged or returned.
- `SocialHttp.sanitize` redacts token/password query fragments in error strings.
- Customer owns the Reddit app and account on their daemon. Tokens are not cached across invokes.
- **Reddit Data API Terms:** [reddit.com/wiki/api-terms](https://www.reddit.com/wiki/api-terms) / [redditinc.com/policies/data-api-terms](https://www.redditinc.com/policies/data-api-terms)
- **Reddit User Agreement** and automation: do not scrape around the API, do not spam, honor robots/rate limits, unique User-Agent. Commercial/high-volume use needs Reddit’s developer program approval (Responsible Builder Policy).
- **GDPR:** usernames, selftext, and permalinks can be personal data. Process only what the workflow needs; do not build a secondary archive of Reddit users.

## 6. Examples

### `search`

Input:

```json
{
  "action": "search",
  "query": "workflow automation",
  "subreddit": "artificial",
  "sort": "relevance",
  "time": "week",
  "limit": 25
}
```

Output:

```json
{
  "status": "ok",
  "auth_mode": "public",
  "posts_returned": 1,
  "after": "t3_xyz",
  "posts": [
    {
      "title": "We replaced our cron jobs",
      "selftext": "…",
      "url": "https://www.reddit.com/r/artificial/comments/…",
      "permalink": "https://www.reddit.com/r/artificial/comments/…",
      "score": 120,
      "num_comments": 18,
      "created_utc": 1710000000,
      "subreddit": "artificial",
      "author": "example",
      "is_self": true,
      "link_flair_text": "Discussion"
    }
  ]
}
```

`auth_mode` is `oauth` when both client id and secret are set.

### `listing`

First page: `{"action":"listing","subreddit":"artificial","listing":"hot","limit":25}`.

Next page: add `"after":"t3_1abcdef"` from the previous `output.after`.
