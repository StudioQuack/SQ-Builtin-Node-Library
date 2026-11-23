# Disqus

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/disqus/`
- **Researched:** 2026-08-20
- **Official API:** Disqus Web API **3.0** (only published version)
- **Docs:** https://disqus.com/api/docs/ · https://disqus.com/api/docs/requests/ · https://disqus.com/api/docs/auth/
- **Auth:** api_key + oauth2 — secret bindings: `api_secret`, `access_token` (`api_key` is the public application key; still treat it as sensitive in logs)

## Scope

One node for Disqus forums, threads, and posts (comments). URI shape: `https://disqus.com/api/3.0/{resource}.json`. Server-side calls send `api_secret`. Authenticated-as-user calls also send `access_token`. Reads are GET; writes are POST. Comment bodies, author emails, and IP addresses are **PII**.

No scrape of disqus.com embed HTML. No unofficial real-time socket. Catalog has no `trigger`; Disqus API 3.0 does not document a first-party webhook subscribe comparable to EventSub — omit `trigger`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_list` | GET | `/api/3.0/posts/list.json` | List posts (filter by `forum`, `thread`, dates) |
| `post_create` | POST | `/api/3.0/posts/create.json` | Create a comment |
| `thread_get` | GET | `/api/3.0/threads/details.json` | One thread by id, `ident`, or `link` |
| `forum_list` | GET | `/api/3.0/users/listForums.json` | Forums the user owns |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `post_list` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Public application key (`api_key` query). Bind as secret; never log. |
| `api_secret` | password | yes | empty | yes | Server-side key (`api_secret`). Required for this JDK node. Never on INPUT. |
| `access_token` | password | no | empty | yes | OAuth user/owner token. Required for `post_create` as an authenticated user and for private PII fields. Owner non-expiring token is documented on the Disqus app details page. |
| `default_forum` | string | no | empty | no | Forum shortname |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Strip `email` and `ipAddress` from author objects unless `include_pii` is true **and** the token is authorized. Default **omit**. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `forum` | string | no | Shortname; else config `default_forum`. Moderators default to forums they moderate when omitted |
| `thread` | string | no | Thread id (repeatable) |
| `limit` | number | no | Default 25, max **100** |
| `cursor` | string | no | Pagination cursor |
| `order` | string | no | `asc` \| `desc` |
| `include` | array | no | `approved` (default), `unapproved`, `spam`, `deleted`, `flagged`, `highlighted` |
| `since` | string | no | Unix or ISO datetime |
| `related` | array | no | `forum`, `thread` |
| `include_pii` | boolean | no | Default `false`. If true, keep author email/IP in OUTPUT (still never log) |

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message` | string | yes | Comment body |
| `thread` | string | yes | Thread id |
| `parent` | string | no | Parent post id (reply) |
| `author_name` | string | no | Anonymous only — not with `access_token` |
| `author_email` | string | no | Anonymous only |
| `author_url` | string | no | Anonymous only |
| `include_pii` | boolean | no | Default `false` on the returned post |

Anonymous create is allowed with secret key (or public key + verified referrer — not applicable to daemon). Authenticated create: pass `access_token`, **do not** send `author_*`.

### `operation = thread_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `thread` | string | yes | Id, or use `thread:ident` / `thread:link` via `ident` / `link` |
| `forum` | string | if ident/link | Shortname |
| `ident` | string | no | Sets `thread:ident=` |
| `link` | string | no | Sets `thread:link=` |
| `related` | array | no | `forum`, `author`, `category` |

### `operation = forum_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `user` | string | no | User id or `user:username=` |
| `limit` | number | no | Default 25, max 100 |
| `cursor` | string | no | Cursor |
| `order` | string | no | `asc` \| `desc` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | API `code` 0 | `ok` |
| `disqus_code` | number | always on HTTP 200 | Provider `code` (0 = success) |
| `posts` | array | `post_list` | `{ id, thread, forum, message, created_at, author_id, author_name, is_approved, is_spam }` — no email/IP by default |
| `post` | object | `post_create` | Same shape as one post |
| `thread` | object | `thread_get` | `{ id, forum, title, slug, posts, is_closed, link }` |
| `forums` | array | `forum_list` | `{ id, name, founder }` |
| `cursor` | object | list | `{ next, prev, hasNext, hasPrev }` |
| `error` | string | failure | Actionable, no keys |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / code 13–14 / timeout | 60 on rate limit; 30 on 5xx/timeout |

Disqus often returns HTTP 200 with `"code": 13|14` for rate limits — treat non-zero `code` as `{ error }` even if HTTP is 200.

## Legal

- [API docs](https://disqus.com/api/docs/) · [Making requests](https://disqus.com/api/docs/requests/) · [Authentication](https://disqus.com/api/docs/auth/) · [Error codes](https://disqus.com/api/docs/errors/) · [Using the API](https://help.disqus.com/en/articles/1717212-using-the-api)
- Default rate limit **1000 calls per hour** per application. Additional quota is a Disqus Pro add-on. HTTP 400 code **13** (resource) / **14** (account).
- Comments are personal data (GDPR): names, emails, IPs, message text. Default OUTPUT redacts email and IP. Operator is the controller for any stored comments.
- OAuth: Draft 30; authorize `https://disqus.com/api/oauth/2.0/authorize/`; token `POST https://disqus.com/api/oauth/2.0/access_token/`. Do not put `api_secret` in browser-side workflows; this node is daemon-side only.
- Terms: Disqus API terms as linked from the developer portal (confirm live URL at bind time).

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Prefer HTTPS for every call.
- Query/body always includes `api_secret` (and `api_key`). Add `access_token` when bound. `Content-Type: application/x-www-form-urlencoded` for POST.
- Empty `api_secret` → `{ error: "Missing Disqus api_secret. Bind api_secret in node parameters." }` — no HTTP.
- Response cap **1_000_000** bytes. Timeout 25 s.
- Never log `message`, `raw_message`, `email`, `ipAddress`, or keys.
- No in-handler retry loop on code 13/14.
