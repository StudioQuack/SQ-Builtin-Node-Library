# Mastodon

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/mastodon/`
- **Researched:** 2026-08-20
- **Official API:** Mastodon REST API (`/api/v1`, search `/api/v2`) + Streaming API
- **Docs:** https://docs.joinmastodon.org/methods/statuses/ — Timelines: https://docs.joinmastodon.org/methods/timelines/ — Streaming: https://docs.joinmastodon.org/methods/streaming/
- **Auth:** api_key (user access token) — secret binding: `access_token`

## Scope

One node for a **single Mastodon (or Mastodon-compatible) instance**. Config `base_url` + user `access_token`. Publish/read statuses, timelines, v2 search, and accounts. Media uses `POST /api/v2/media` (async) then `media_ids[]` on create. **`trigger`** uses the Streaming API (SSE `GET /api/v1/streaming/…`) when the instance is reachable from the daemon; **since Mastodon 4.2**, public/local/hashtag/list/direct streams require a **user token** (app/public tokens removed). There is no generic “new status” HTTP webhook; Web Push (`POST /api/v1/push/subscription`) is a different browser-push protocol — do not treat it as workflow ingress unless the operator already runs a Web Push receiver. Otherwise poll `timeline_get` / `GET /api/v1/timelines/home`.

Do not scrape instance HTML. Honor that instance’s ToS and published rate limits (`GET /api/v2/instance`). Direct timeline (`/api/v1/timelines/direct`) is **removed** — omit. Search v1 is **removed** — use v2 only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `status_create` | POST | `/api/v1/statuses` (+ `/api/v2/media`) | Publish a status; optional file attachments |
| `status_get` | GET | `/api/v1/statuses/{id}` | Fetch one status |
| `timeline_get` | GET | `/api/v1/timelines/{home\|public\|tag/:hashtag\|list/:id}` | Home / public / hashtag / list |
| `search` | GET | `/api/v2/search` | Accounts, statuses, hashtags |
| `account_get` | GET | `/api/v1/accounts/verify_credentials` or `/api/v1/accounts/{id}` | Self or by id |
| `trigger` | GET SSE or poll | `/api/v1/streaming/user` (default) | Real-time `update` events; else one poll of home timeline |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `status_create` | no | Chooses the row above |
| `base_url` | string | yes | empty | no | Instance origin, e.g. `https://mastodon.social`. HTTPS only. No path suffix. SSRF: private/link-local blocked |
| `access_token` | password | yes | empty | yes | User token. Header `Authorization: Bearer`. Never on INPUT. |
| `timeout_seconds` | number | no | `25` | no | REST timeout. Floor 1 s. Streaming read idle 60 s |
| `max_status_chars` | number | no | `500` | no | Clamp; prefer instance `configuration.statuses.max_characters` when fetched |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = status_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `status` | string | yes unless `media` | Status text |
| `media` | file | no | One or more files (type `file`) → `POST /api/v2/media` then `media_ids[]` |
| `in_reply_to_id` | string | no | Reply |
| `sensitive` | boolean | no | Sensitive flag |
| `spoiler_text` | string | no | CW |
| `visibility` | string | no | `public` \| `unlisted` \| `private` \| `direct` |
| `language` | string | no | ISO 639-1 |
| `scheduled_at` | string | no | ISO-8601; ≥5 minutes ahead → ScheduledStatus |
| `idempotency_key` | string | no | Header `Idempotency-Key` (≤1 h server-side) |

### `operation = status_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Status id |

### `operation = timeline_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `timeline` | string | yes | `home` \| `public` \| `tag` \| `list` |
| `hashtag` | string | if tag | Without `#` |
| `list_id` | string | if list | List id |
| `local` / `remote` / `only_media` | boolean | no | Public/tag filters |
| `max_id` / `since_id` / `min_id` | string | no | Link paging |
| `limit` | number | no | Default 20, max **40** |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | Query |
| `type` | string | no | `accounts` \| `hashtags` \| `statuses` |
| `resolve` | boolean | no | WebFinger / URL resolve (user token) |
| `limit` | number | no | Default 20, max 40 per type |
| `offset` | number | no | Requires `type` |

### `operation = account_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | no | Omit → `verify_credentials` (self) |

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `stream` (default) \| `poll` |
| `stream` | string | no | `user` (default) \| `public` \| `public:local` \| `hashtag` \| `list` |
| `tag` / `list` | string | if stream needs it | Query params |
| `limit` | number | poll | Home timeline page |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `toot` | object | create/get | Status or ScheduledStatus (capped HTML `content`) |
| `statuses` | array | timeline / poll trigger | Status list |
| `accounts` / `hashtags` | array | `search` | Search buckets |
| `account` | object | `account_get` | Account entity |
| `event` | object | stream trigger | Parsed SSE `event` + `payload` (`update`, `delete`, `notification`, …) |
| `error` | string | failure | Instance `error` string; no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

## Legal

- Statuses: https://docs.joinmastodon.org/methods/statuses/
- Media v2: https://docs.joinmastodon.org/methods/media/
- Timelines: https://docs.joinmastodon.org/methods/timelines/
- Search v2: https://docs.joinmastodon.org/methods/search/
- Accounts: https://docs.joinmastodon.org/methods/accounts/
- Streaming (user token since **4.2.0**): https://docs.joinmastodon.org/methods/streaming/
- Operator must comply with **that instance’s** terms, local laws, and copyright. Rate limits are **per instance** (often 300 req/5 min — verify `GET /api/v1/instance` / v2 configuration). Statuses, accounts, and media descriptions are personal data; do not log HTML bodies. `direct` visibility is a private mention — treat as PII. Do not federate-crawl via undocumented endpoints. Public timeline may be disabled (`401` without token).

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Allow only the configured `base_url` host (and its documented streaming host if separate). Block RFC1918/link-local/`base_url` tricks.
- Empty `base_url` or `access_token` → `{ error }` no HTTP. HTTPS required.
- Media: multipart `file` from daemon ref to `/api/v2/media`; poll `GET /api/v1/media/{id}` while `202`/`206`/`url=null`; then POST statuses. Cap attachment count/size from instance config.
- JSON/HTML body cap **1_000_000** bytes. REST timeout 25 s. Streaming: one event then complete the node (BPMN loop for continuous listen) — do not hold a workflow token unbounded; idle timeout 60 s → `{ error, retry_after_seconds }` or fall back to `poll`.
- 429 → honor `Retry-After`; **no unbounded retry**. 422 validation → `{ error }` no retry.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, instance host, status ids, sizes — never tokens or status HTML.
