# Discourse

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/discourse/`
- **Researched:** 2026-08-20
- **Official API:** Discourse REST (OpenAPI “latest” on docs.discourse.org)
- **Docs:** https://docs.discourse.org/ · conceptual guides on https://meta.discourse.org
- **Auth:** api_key — secret bindings: `api_key` (`api_username` is an identity header, not a password, but treat it as sensitive in logs)

## Scope

One node for a **customer-hosted or Discourse-hosted** forum. Base URL is operator-configured (`url`). Authenticated calls send headers `Api-Key` and `Api-Username` together (missing username → 403). Operations are **only** documented OpenAPI paths from https://docs.discourse.org/ — do not reverse-engineer admin UI routes that are absent from that spec.

`url` is customer egress (SSRF guard + allowlist). `trigger` is **inbound only**: Discourse Admin webhooks (HMAC `X-Discourse-Event-Signature`) are documented on Meta and in Discourse core, but **creating** a webhook is not in the public OpenAPI snapshot fetched 2026-08-20. Operators create the webhook in Discourse Admin pointing at StudioQuack ingress; this node verifies and normalizes.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `topic_create` | POST | `/posts.json` | New topic: `title` + `raw` (no `topic_id`) |
| `topic_get` | GET | `/t/{id}.json` | One topic + post stream |
| `post_create` | POST | `/posts.json` | Reply: `topic_id` + `raw` |
| `user_get` | GET | `/u/{username}.json` | One user by username |
| `search` | GET | `/search.json` | Search (`q`, optional `page`) |
| `trigger` | — | inbound webhook | `ingest` (verify signature). No OpenAPI create. |

Undocumented Discourse endpoints are omitted, not listed as “later”.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `url` | string | yes | empty | no | Forum origin e.g. `https://forum.example.com` (no trailing path). HTTPS required. SSRF-checked. |
| `api_key` | password | yes | empty | yes | Admin/user API key from Discourse Admin → API. Header `Api-Key`. Never on INPUT. |
| `api_username` | string | yes | empty | no | Header `Api-Username` (acts as this user, including `system`). Required with the key. |
| `webhook_secret` | password | no | empty | yes | Discourse webhook “secret” for HMAC-SHA256. Required for `trigger` ingest. |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = topic_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Topic title |
| `raw` | string | yes | Markdown/plain body |
| `category` | number | no | Category id |
| `embed_url` | string | no | Associate an external URL (comments-on-blog) |
| `external_id` | string | no | Remote id |
| `auto_track` | boolean | no | Default true on Discourse |

### `operation = topic_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Topic id |

### `operation = post_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic_id` | number | yes | Existing topic |
| `raw` | string | yes | Body |
| `reply_to_post_number` | number | no | Reply to a post number inside the topic |

### `operation = user_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `username` | string | yes | Path `{username}` |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | Search query (filters `@user`, `#category`, `tags:`, `after:`, `in:`, `order:` as documented) |
| `page` | number | no | Page (example `page=1`) |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `action` | string | yes | `ingest` only |
| `payload` | object | yes | JSON body (`post` / `topic` / `user` / `ping` keys as Discourse sends) |
| `headers` | object | yes | `X-Discourse-Event-Signature`, `X-Discourse-Event-Type`, `X-Discourse-Event`, `X-Discourse-Event-Id`, `X-Discourse-Instance` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `topic_id` | number | topic / post create | Topic id |
| `post_id` | number | create | Post `id` |
| `topic` | object | `topic_get` | `{ id, title, slug, posts_count, closed, category_id }` plus capped `post_stream` (first N posts, max body cap) |
| `user` | object | `user_get` | Public profile fields; omit emails (`/u/{username}/emails.json` is a **different** OpenAPI op — do not call it from `user_get`) |
| `posts` | array | `search` | Search hits (ids, usernames, excerpts) |
| `users` | array | `search` | User hits |
| `event` | object | `trigger` | `{ event_type, event_name, instance, data }` after HMAC OK |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; Discourse defaults discussed on Meta: ~60 anon / ~200 authed per 60 s per user (site-configurable). 60 on 429; 30 on 5xx/timeout |

## Legal

- [Discourse API docs](https://docs.discourse.org/) · operator’s own forum Terms of Service and privacy policy
- Customer egress: the daemon calls **only** the configured `url` host. SSRF guard must reject link-local, metadata IPs, and off-allowlist hosts.
- Posts and usernames are personal data. Do not log `raw` / `cooked`. Do not fetch email endpoints unless a future spec adds an explicit `user_emails` op with a legal basis.
- Webhook HMAC: `X-Discourse-Event-Signature: sha256={hex}` over the **raw body** with `webhook_secret` ([Discourse emit_web_hook_event](https://github.com/discourse/discourse/blob/master/app/jobs/regular/emit_web_hook_event.rb)).
- Rate limits are **per site** (admin settings). Honor 429.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Join `url` + path with a single slash. Reject `url` that is not `https:` (or operator-allowed `http:` for lab instances — default deny http).
- Headers: `Api-Key`, `Api-Username`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`. POST JSON: `Content-Type: application/json`.
- Empty `api_key` or `api_username` → `{ error }` no HTTP.
- Response cap **1_000_000** bytes. Timeout 25 s.
- `trigger` ingest: if `webhook_secret` bound, require valid HMAC; mismatch → `{ error }` no engine correlate. `ping` event body `{ "ping": "OK" }` → `{ status: "ok", event: { event_type: "ping" } }`.
- No in-handler retry loop.
- Logs: operation, HTTP status, topic_id/post_id — never `api_key`, never post bodies.
