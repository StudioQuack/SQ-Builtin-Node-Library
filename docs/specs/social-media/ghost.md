# Ghost

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/ghost/`
- **Researched:** 2026-08-20
- **Official API:** Ghost Admin API + Content API. Version negotiation via `Accept-Version` (not a URL prefix). Current docs examples and webhook `api_version` use **v6** / **`Accept-Version: v6.0`**.
- **Docs:** https://docs.ghost.org/admin-api — https://docs.ghost.org/content-api — versioning: https://docs.ghost.org/faq/api-versioning — webhooks: https://docs.ghost.org/webhooks — images: https://docs.ghost.org/admin-api/images/uploading-an-image
- **Auth:** Admin = JWT from `admin_api_key` (`id:hex_secret`). Content = query `key`. Secret bindings: `admin_api_key`, optional `content_api_key`.

## Scope

One node for a Ghost site (self-host or Ghost(Pro)). Admin base: `https://{admin_domain}/ghost/api/admin/`. Content base: `https://{admin_domain}/ghost/api/content/`. `{admin_domain}` is the operator URL (may differ from the public site; Ghost(Pro) admin is `*.ghost.io` over HTTPS). Customer egress allow-list that origin only.

Admin operations: post CRUD. Content operations: public published reads. Image upload: Admin `POST /images/upload/` with type `file`. `trigger`: official Admin webhooks (`POST /webhooks/`).

Do not use session cookies or the staff UI. Do not use a Ghost JavaScript client library. Do not put API version in the URL (`/ghost/api/v4/admin/` is obsolete).

Always send `Accept-Version: v6.0` (config-overridable). Ghost returns `Content-Version` with the install’s version.

## Operations (`operation` config values)

Paths below are relative to Admin or Content base as noted.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_create` | POST | Admin `/posts/` (`?source=html` when sending HTML) | Create draft or published post |
| `post_get` | GET | Admin `/posts/{id}/` | One post including drafts |
| `post_list` | GET | Admin `/posts/` | Browse posts (paginated) |
| `post_update` | PUT | Admin `/posts/{id}/` | Edit; send latest `updated_at` |
| `post_delete` | DELETE | Admin `/posts/{id}/` | Delete post |
| `content_get` | GET | Content `/posts/{id}/?key=` or `/posts/slug/{slug}/?key=` | Public published post |
| `content_list` | GET | Content `/posts/?key=` | Public published list |
| `image_upload` | POST | Admin `/images/upload/` | Multipart image; type `file` |
| `trigger` | POST register + inbound | Admin `/webhooks/` | Official webhooks |

`image_upload` and `trigger` are not catalog verbs but are required for featured images and listen; ship them on this node.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `post_create` | no | Chooses the row above |
| `url` | string | yes | | no | Admin origin, e.g. `https://example.com` or `https://site.ghost.io`. No trailing `/ghost`. HTTPS required. |
| `admin_api_key` | password | yes for Admin ops, upload, trigger register | | yes | `id:secret` from Custom Integration |
| `content_api_key` | password | yes for `content_*` | | yes | Content API key query param |
| `accept_version` | string | no | `v6.0` | no | `Accept-Version` header |
| `webhook_event` | select | no | `post.published` | no | Event for `trigger` register |
| `timeout_seconds` | number | no | `30` | no | HTTP timeout |

Staff session email/password auth is **out of scope** (2FA, cookies, CSRF). Integration token auth only.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Images: type `file` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never return the Admin secret or JWT |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Post title |
| `html` | string | no | HTML body; send `?source=html` (lossy → Lexical) |
| `lexical` | string | no | Lexical JSON string if not using `html` |
| `status` | string | no | `draft` (default) \| `published` \| `scheduled` |
| `slug` | string | no | Slug |
| `custom_excerpt` | string | no | Excerpt |
| `feature_image` | string | no | Already-hosted image URL |
| `feature_image_file` | file | no | Upload via Admin images then set `feature_image` |
| `tags` | array | no | Tag names (strings) or `{ name }` objects |
| `authors` | array | no | Author emails or `{ id }` |
| `published_at` | string | no | ISO-8601; required conceptually for `scheduled` |
| `featured` | boolean | no | Featured flag |

Empty `title` → `{ error }`, no HTTP. Prefer `html` for operators; omit both html and lexical for title-only drafts.

### `operation = post_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Post id |
| `formats` | string | no | e.g. `html,lexical` query |

### `operation = post_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page (default 1) |
| `limit` | number | no | Page size (Ghost default 15; cap 100) |
| `filter` | string | no | Ghost filter string, e.g. `status:published` |
| `order` | string | no | e.g. `published_at desc` |
| `include` | string | no | e.g. `tags,authors` |

### `operation = post_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Post id |
| `updated_at` | string | yes | Optimistic lock — last `updated_at` from GET |
| `title` | string | no | New title |
| `html` | string | no | HTML (`?source=html`) |
| `lexical` | string | no | Lexical JSON |
| `status` | string | no | Status |
| `slug` | string | no | Slug |
| `custom_excerpt` | string | no | Excerpt |
| `feature_image` | string | no | Image URL |
| `feature_image_file` | file | no | Upload then set URL |
| `tags` | array | no | Tags |
| `published_at` | string | no | Publish/schedule time |

PUT body wraps `{ "posts": [ { ... } ] }`. Missing `updated_at` → `{ error }` (Ghost will 409 otherwise).

### `operation = post_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Post id |

### `operation = content_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | no | Content `/posts/{id}/` |
| `slug` | string | no | Content `/posts/slug/{slug}/` |
| `include` | string | no | e.g. `tags,authors` |

One of `id` or `slug` required.

### `operation = content_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Page |
| `limit` | number | no | Limit |
| `filter` | string | no | Filter |
| `include` | string | no | Includes |

### `operation = image_upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Daemon file ref. Form field `file` |
| `purpose` | string | no | `image` (default) \| `profile_image` \| `icon` |
| `ref` | string | no | Echoed filename/path |

Formats: WEBP, JPEG, GIF, PNG, SVG (`icon` also ICO). `profile_image` / `icon` must be square.

### `operation = trigger`

Register: Admin `POST /webhooks/` with `event` (config `webhook_event`) and `target_url` (env inbound webhook URL). Optional `secret` generated and stored as a daemon secret for payload verification if Ghost sends one.

Inbound: map JSON body (`event` + resource) to output. Ghost treats 2xx as success.

Supported events include `post.published`, `post.added`, `post.edited`, `post.deleted`, `post.unpublished`, `page.published`, `member.added` (full list: https://docs.ghost.org/webhooks). Default `post.published`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | item ops | Ghost id |
| `uuid` | string | item ops | UUID |
| `title` | string | post | Title |
| `slug` | string | post | Slug |
| `url` | string | post | Public URL |
| `html` | string | get | HTML if requested |
| `ghost_status` | string | post | `draft` / `published` / `scheduled` |
| `updated_at` | string | post | Echo for the next PUT |
| `feature_image` | string | post / upload | Image URL |
| `items` | array | lists | `{ id, title, slug, url, ghost_status, updated_at }` |
| `page` | number | lists | `meta.pagination.page` |
| `pages` | number | lists | `meta.pagination.pages` |
| `total` | number | lists | `meta.pagination.total` |
| `event` | string | trigger inbound | Ghost event name |
| `webhook_id` | string | trigger register | Webhook id |
| `error` | string | failure | `errors[].message` / HTTP status; no secrets |

## Legal

- Ghost is MIT-licensed software; the operator’s site ToS apply. Ghost.org Website + Hosted Service terms (fetched 2026-08-20): https://ghost.org/terms/
- Admin keys are **server-side only** (docs). Never send them to browsers or logs.
- Content API keys only expose public data; still treat member PII on Admin/member webhooks as personal data (GDPR).
- Content API is described as cacheable without a published numeric cap; Admin: honor 429. Default browse page size 15.
- Official-API-first: documented Admin/Content REST only.

## Implementation notes

- JDK `HttpClient` only. **No** `@tryghost/admin-api` SDK. Sign JWT with JDK crypto (`HS256`):
  1. Split `admin_api_key` on `: ` into `id` and hex `secret`.
  2. Decode secret as hex bytes.
  3. Header `{ "alg": "HS256", "kid": id, "typ": "JWT" }`; payload `{ "iat": now, "exp": now+300, "aud": "/admin/" }` (`iat`/`exp` in **seconds**). Max lifetime 5 minutes.
  4. `Authorization: Ghost {token}` on every Admin request. Mint a fresh JWT per request (single-use, short-lived).
- Content: `GET ...?key={content_api_key}` plus any `filter`/`page`. Never put the Admin key in a query string.
- Headers: `Accept-Version: {accept_version}`, `Accept: application/json`, JSON bodies `Content-Type: application/json`. Image upload: multipart; let the client set the boundary.
- JSON envelope: `{ "posts": [ { ... } ] }` on write. Responses wrap arrays the same way.
- Timeout 30 s; upload 60 s. JSON body cap 2 MiB. Image cap 10 MiB. No inner retry.
- HTTP 400/401/403/404/409/422/429/5xx → `{ error }` from `errors[0].message` + `errorType`. 409 on update usually means stale `updated_at`. Never throw past dispatch.
- SSRF: only the configured `url` host. Ghost(Pro) requires HTTPS.
