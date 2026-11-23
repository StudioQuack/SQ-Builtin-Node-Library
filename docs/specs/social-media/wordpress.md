# WordPress

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/wordpress/`
- **Researched:** 2026-08-20
- **Official API:** WordPress REST API namespace `wp/v2` (core since 4.7). Self-host: `https://{site}/wp-json/wp/v2/`. WordPress.com: `https://public-api.wordpress.com/wp/v2/sites/{site_id}/`. Same node; `host` = `self` \| `wpcom`.
- **Docs:** https://developer.wordpress.org/rest-api/ — Application Passwords: https://developer.wordpress.org/advanced-administration/security/application-passwords/ — WP.com REST: https://developer.wordpress.com/docs/api/ — OAuth2: https://developer.wordpress.com/docs/api/oauth2/ — namespaces: https://developer.wordpress.com/docs/api/namespaces/
- **Auth:** self = HTTP Basic (username + application password). wpcom = OAuth2 Bearer on `public-api.wordpress.com`. Secrets: `application_password` / `client_secret` / `access_token` as bound below.

## Scope

One node for WordPress content CRUD. `host=self` talks to the operator’s site over customer egress (SSRF allow-list that origin only). `host=wpcom` talks to Automattic’s public API with a numeric site id (preferred) or site slug.

Use **`/wp/v2/`** for posts, pages, users, and media on both hosts so field names stay aligned. Do not use XML-RPC. Do not scrape wp-admin. Do not send cookie/nonce auth from the daemon (that is in-dashboard only).

**Trigger:** WordPress core REST has **no official outbound webhook subscribe**. `trigger` is a **poll** of `GET .../posts?modified_after={cursor}&orderby=modified`. Do not ship a third-party webhook plugin as if it were core.

Media: type `file` (daemon ref-id) on `media_upload` and optional featured-image file on create/update.

## Operations (`operation` config values)

Self-host path is under `{site_url}/wp-json`. WP.com path is `https://public-api.wordpress.com/wp/v2/sites/{site_id}` (same resource suffix).

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_create` | POST | `/wp/v2/posts` | Create post |
| `post_get` | GET | `/wp/v2/posts/{id}` | Fetch one post |
| `post_list` | GET | `/wp/v2/posts` | List/filter posts |
| `post_update` | POST | `/wp/v2/posts/{id}` | Update post (WP uses POST, not PUT) |
| `page_create` | POST | `/wp/v2/pages` | Create page |
| `page_get` | GET | `/wp/v2/pages/{id}` | Fetch one page |
| `page_list` | GET | `/wp/v2/pages` | List pages |
| `page_update` | POST | `/wp/v2/pages/{id}` | Update page |
| `user_create` | POST | `/wp/v2/users` | Create user (needs `create_users`) |
| `user_get` | GET | `/wp/v2/users/{id}` or `/wp/v2/users/me` | Fetch user |
| `user_list` | GET | `/wp/v2/users` | List users |
| `user_update` | POST | `/wp/v2/users/{id}` | Update user |
| `media_upload` | POST | `/wp/v2/media` | Multipart upload; type `file` |
| `trigger` | GET poll | `/wp/v2/posts` (`modified_after`) | No official webhooks — poll |

Do not add XML-RPC `metaWeblog.newPost`. Catalog has no delete verbs — omit `DELETE` unless product adds them later.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `post_create` | no | Chooses the row above |
| `host` | select | yes | `self` | no | `self` or `wpcom` |
| `site_url` | string | yes if `host=self` | | no | Origin + optional subdir, e.g. `https://blog.example.com`. HTTPS required. No path except WordPress subdirectory. |
| `site_id` | string | yes if `host=wpcom` | | no | Numeric WP.com site id (preferred) or `example.wordpress.com` |
| `username` | string | yes if `host=self` | | no | WP login for Basic auth |
| `application_password` | password | yes if `host=self` | | yes | Application Password (spaces optional) |
| `access_token` | password | yes if `host=wpcom` | | yes | OAuth2 Bearer from `https://public-api.wordpress.com/oauth2/token` |
| `client_id` | string | no | | no | WP.com app id — token refresh / password grant only; not sent on resource calls |
| `client_secret` | password | no | | yes | WP.com app secret — token endpoint only |
| `poll_seconds` | number | no | `60` | no | `trigger` interval (min 15) |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout |
| `max_upload_bytes` | number | no | `10485760` | no | Media cap (10 MiB default) |

Obtain WP.com tokens via Authorization Code (production) or password grant + Application Password (owner scripts only). Do not implement implicit flow. Do not send Application Passwords directly to `public-api.wordpress.com` resource URLs.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Media: type `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo passwords or tokens |

FUNCTION ports: none.

## Input fields (by operation)

### Shared post/page write fields (`post_create`, `post_update`, `page_create`, `page_update`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes on update | Resource id |
| `title` | string | yes on create | `title` (string; WP wraps as rendered/raw) |
| `content` | string | no | HTML/`content` raw |
| `excerpt` | string | no | Excerpt |
| `status` | string | no | `publish` \| `future` \| `draft` \| `pending` \| `private` |
| `slug` | string | no | Slug |
| `author` | number | no | Author user id |
| `featured_media` | number | no | Media id |
| `categories` | array | no | Category ids (posts) |
| `tags` | array | no | Tag ids (posts) |
| `parent` | number | no | Parent page id (pages) |
| `comment_status` | string | no | `open` \| `closed` |

Create with empty `title` → `{ error }`, no HTTP.

### `post_get` / `page_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |
| `context` | string | no | `view` (default) \| `edit` \| `embed` |
| `password` | password | no | Password-protected content; never log |

### `post_list` / `page_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page of collection (default 1) |
| `per_page` | number | no | Default 10; cap 100 (core max) |
| `search` | string | no | Search string |
| `status` | string | no | Default `publish`; `edit` context needed for drafts |
| `after` | string | no | ISO-8601 published after |
| `modified_after` | string | no | ISO-8601 modified after |
| `author` | number | no | Author id |
| `order` | string | no | `asc` \| `desc` |
| `orderby` | string | no | e.g. `date`, `modified` |
| `slug` | string | no | Filter by slug |

### `user_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user_login` | string | yes | WP `username` |
| `email` | string | yes | Email |
| `password` | password | yes | New user’s password — secret; never log or return |
| `name` | string | no | Display name |
| `roles` | array | no | Role slugs; default subscriber |

### `user_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | no | Omit to use `/users/me` |
| `context` | string | no | `edit` required for email |

### `user_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Pagination |
| `per_page` | number | no | Cap 100 |
| `search` | string | no | Search |
| `roles` | string | no | Role filter |

### `user_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | User id |
| `email` | string | no | Email |
| `name` | string | no | Display name |
| `password` | password | no | New password — secret |
| `roles` | array | no | Roles |

### `media_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Daemon file ref; multipart field |
| `title` | string | no | Attachment title |
| `alt_text` | string | no | Alt text |
| `caption` | string | no | Caption |
| `post` | number | no | Parent post id |

### `trigger`

No per-fire input. Poll uses last `modified` cursor stored by the trigger runtime. Output one item per new/updated post.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | number | item ops | WP resource id |
| `link` | string | item ops | Permalink |
| `title` | string | item ops | `title.rendered` or raw |
| `content` | string | get | `content.rendered` |
| `slug` | string | item ops | Slug |
| `wp_status` | string | item ops | `publish` / `draft` / … |
| `modified` | string | item / trigger | `modified_gmt` |
| `author` | number | item ops | Author id |
| `featured_media` | number | item ops | Media id |
| `source_url` | string | media | File URL |
| `mime_type` | string | media | MIME |
| `items` | array | list | `{ id, link, title, modified, wp_status }` |
| `total` | number | list | `X-WP-Total` |
| `total_pages` | number | list | `X-WP-TotalPages` |
| `event` | string | trigger | `post.modified` |
| `error` | string | failure | Message + HTTP status; no secrets |

## Legal

- Self-host: operator’s site ToS plus WordPress GPL. Application Passwords: https://developer.wordpress.org/advanced-administration/security/application-passwords/ (HTTPS; not for wp-login).
- REST handbook: https://developer.wordpress.org/rest-api/ — authentication: https://developer.wordpress.org/rest-api/using-the-rest-api/authentication/
- WP.com: https://developer.wordpress.com/docs/api/guidelines-for-responsible-use-of-automattics-apis/ — no excessive calls; privacy policy on apps; do not share tokens; WordPress.com Terms of Service for published content.
- WP.com OAuth scopes: request `posts`, `media`, `users` only as needed — not `global`.
- No published numeric WP.com rate limit; back off on 429. Self-host: honor `Retry-After`; `per_page` max 100.
- Users/email are PII (GDPR). Do not log `password` or Application Passwords.
- Official-API-first: no admin-ajax scrape, no cookie jar.

## Implementation notes

- JDK `HttpClient` only. No wordpress-java SDK.
- **self:** `Authorization: Basic base64(username:application_password)`. Strip spaces in the password. Reject non-HTTPS `site_url`. Resolve host once; SSRF-deny link-local, metadata, and off-allowlist hosts.
- **wpcom:** `Authorization: Bearer {access_token}`. Base `https://public-api.wordpress.com/wp/v2/sites/{site_id}`. Prefer numeric `site_id`.
- JSON `Content-Type: application/json` except media: `multipart/form-data` with file part (filename + MIME). Do not set a boundary-less Content-Type by hand incorrectly.
- WP update = **POST** to `/posts/{id}` (not PUT).
- Timeout 30 s. Upload timeout 60 s. Body cap 2 MiB JSON. No inner retry; 429/5xx → `{ error }`.
- HTTP 4xx/5xx: parse WP `{ code, message, data.status }` → `{ error }`. Never throw past dispatch.
- `trigger`: GET `per_page=10&orderby=modified&order=desc&modified_after={cursor}&status=publish`. Advance cursor to max `modified_gmt` seen. Min interval 15 s.
