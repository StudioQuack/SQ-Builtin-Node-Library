# Tumblr

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/tumblr/`
- **Researched:** 2026-08-20
- **Official API:** Tumblr API **v2** (`https://api.tumblr.com/v2`)
- **Docs:** https://www.tumblr.com/docs/api — source of truth https://github.com/tumblr/docs/blob/master/api.md — NPF https://www.tumblr.com/docs/npf
- **Auth:** oauth2 preferred — secret bindings: `client_id`, `client_secret`, `access_token`, `refresh_token`. OAuth 1.0a remains documented; new integrations use OAuth 2 (`basic`, `write`, `offline_access`).

## Scope

One node for the official Tumblr API v2. The designer picks `operation`; the handler calls `https://api.tumblr.com/v2/...`. Create/read posts in **Neue Post Format** (`POST /blog/{blog-identifier}/posts`, `GET /blog/{blog-identifier}/posts/{post-id}`). `blog_info` and `user_dashboard` are the matching catalog reads. Catalog `trigger` is **omitted**: no official webhook/event API was documented on the pages fetched 2026-08-20. Do not scrape tumblr.com HTML. Pass `npf=true` on post reads.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_create` | POST | `/v2/blog/{blog-identifier}/posts` | Create (or reblog) an NPF post. JSON body `content` blocks |
| `post_get` | GET | `/v2/blog/{blog-identifier}/posts/{post-id}` | Fetch one post (NPF) |
| `blog_info` | GET | `/v2/blog/{blog-identifier}/info` | Blog metadata |
| `user_dashboard` | GET | `/v2/user/dashboard` | Authenticated dashboard |

Legacy `POST /v2/blog/{id}/post` (typed legacy posts) is not the default; prefer NPF.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `blog_info` | no | Chooses the row above |
| `client_id` | password | yes | empty | yes | OAuth consumer key (also `api_key` query on some GETs) |
| `client_secret` | password | no | empty | yes | Token exchange / refresh |
| `access_token` | password | yes | empty | yes | Bearer. Header `Authorization: Bearer`. Never on INPUT. |
| `refresh_token` | password | no | empty | yes | OAuth2 refresh (`offline_access`) |
| `blog_identifier` | string | no | empty | no | Default `{blog-identifier}` (hostname or UUID) |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Uploads type `file`. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `blog_identifier` | string | no | Else config default |
| `content` | array | yes | NPF content blocks (`type` + block fields per https://www.tumblr.com/docs/npf) |
| `layout` | array | no | Optional NPF layout |
| `tags` | string | no | Comma-separated tags |
| `state` | string | no | `published` \| `draft` \| `queue` \| `private` as documented |

Missing `content` → `{ error }` no HTTP.

### `operation = post_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `blog_identifier` | string | no | Else config |
| `post_id` | string | yes | Post id |
| `npf` | boolean | no | Default `true` (query `npf=true`) |

### `operation = blog_info`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `blog_identifier` | string | no | Else config |

### `operation = user_dashboard`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Page size; clamp to Tumblr’s documented max |
| `offset` | number | no | Offset; max 1000 per docs — prefer `before`/`after` beyond that |
| `npf` | boolean | no | Default `true` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | HTTP 200 / meta.status 200 | `ok` |
| `meta` | object | always on 2xx | Tumblr `meta` (status, msg) |
| `blog` | object | `blog_info` | Blog resource |
| `post` | object | `post_get` / `post_create` | Post (id, blog, content when NPF) |
| `posts` | array | `user_dashboard` | Dashboard posts |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP / meta error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

Tumblr JSONP GETs can return HTTP 200 with a non-200 `meta.status` — map that to `{ error }`.

## Legal

- API: https://www.tumblr.com/docs/api
- GitHub API markdown: https://github.com/tumblr/docs/blob/master/api.md
- NPF: https://www.tumblr.com/docs/npf
- Terms: https://www.tumblr.com/policy/terms-of-service
- User Guidelines (NSFW / mature-content labels, prohibited categories): https://www.tumblr.com/docs/en/community_guidelines (fetched as `/policy/en/user-guidelines`; last modified 2026-01-09)
- **Rate limits (published):** 300 calls/min/IP; 18,000/hour/IP; 432,000/day/IP; **1,000 calls/hour per consumer key**; **5,000/day per consumer key**; 250 new published posts/day/user; 250 images/day/user; 20 videos/day/user; 60 minutes video/day/user. HTTP **429 Limit Exceeded**. Daily windows follow Tumblr’s clocks (calendar day).
- User Guidelines forbid spam, deceptive links, and **mass registration / programmatic abuse**. The documented API is the allowed integration path for a registered app; do not use this node to flood, scrape, or bypass Community Guidelines. Mature content must be labeled as Tumblr requires.
- Posts and dashboard feeds contain third-party copyrighted works and PII (usernames, avatars). Minimize retention.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No official SDK required.
- Base `https://api.tumblr.com`. Header `Authorization: Bearer {access_token}`. Authorize: `GET /oauth2/authorize`. Token: `POST https://api.tumblr.com/v2/oauth2/token` (`grant_type=authorization_code` or `refresh_token`). Optional OAuth1→OAuth2: `POST /v2/oauth2/exchange`.
- Empty `access_token` → `{ error: "Missing Tumblr access token. Bind access_token in node parameters." }` — no HTTP.
- Always add `npf=true` on post-returning GETs unless input `npf` is false. `post_create` body is JSON NPF (`content` array of typed blocks), not the legacy `/post` form fields.
- Some public GETs (`blog_info`, tagged posts) also accept `api_key={client_id}`; this node still sends Bearer for dashboard/write.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- 429 → `{ error, http_status: 429, retry_after_seconds: 60 }`. 5xx / timeout → `retry_after_seconds: 30`. Other 4xx → truncated body (240 chars), tokens redacted.
- Do not auto-retry inside the handler.
- Logs: operation, blog id, HTTP/meta status, sizes — never tokens, NPF media bytes, or post body PII beyond length.
