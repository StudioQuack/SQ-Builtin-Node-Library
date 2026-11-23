# Pinterest

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/pinterest/`
- **Researched:** 2026-08-20
- **Official API:** Pinterest API **v5** (`https://api.pinterest.com/v5`)
- **Docs:** https://developers.pinterest.com/docs/api/v5/introduction/ — Auth: https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/
- **Auth:** oauth2 — secret binding: `access_token` (`client_secret` only if the handler refreshes tokens)

## Scope

One node for **organic** Pinterest v5: pins, boards, and the authenticated user account. Pin create body is `board_id`, `title`, `description`, `link`. **Trial** access: created pins/boards are creator-only sandbox entities and **1000 requests/day** app-wide. **Standard** access is required for production-visible pins. Conversion/ads scopes stay off this v1 node.

Do not scrape pinterest.com. Do not collect user passwords or session cookies (Developer Guidelines).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `pin_create` | POST | `/v5/pins` | Create a Pin on a board the token owns |
| `pin_get` | GET | `/v5/pins/{pin_id}` | Fetch one Pin |
| `board_create` | POST | `/v5/boards` | Create a board (`boards:write`) |
| `board_list` | GET | `/v5/boards` | List boards (`boards:read`) |
| `user_get` | GET | `/v5/user_account` | Authenticated user account (`user_accounts:read`) |
| `trigger` | GET poll | `/v5/pins` | No official **organic** pin webhook. Poll recent pins (`bookmark`). Lead-ads subscriptions are ads-only — omit |

Scopes for pin create (official OpenAPI): `boards:read`, `boards:write`, `pins:read`, `pins:write`. Sandbox host `https://api-sandbox.pinterest.com/v5` is optional via config, not a second node.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `pin_create` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Bearer token (`pina…` user grant). Never on INPUT. |
| `api_host` | select | no | `https://api.pinterest.com` | no | Production vs `https://api-sandbox.pinterest.com` |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. Media upload 120 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = pin_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `board_id` | string | yes | Destination board |
| `title` | string | no | Pin title |
| `description` | string | no | Pin description |
| `link` | string | no | Destination URL |
| `alt_text` | string | no | Accessibility |

### `operation = pin_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pin_id` | string | yes | Pin id |

### `operation = board_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Board name |
| `description` | string | no | Board description |
| `privacy` | string | no | As documented (`PUBLIC` / `SECRET` …) |

### `operation = board_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bookmark` | string | no | Pagination cursor |
| `page_size` | number | no | Clamp to documented max |

### `operation = user_get`

No input fields.

### `operation = trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bookmark` | string | no | Continue poll |
| `page_size` | number | no | Default 25 |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `pin` | object | create/get | id, board_id, title, link, media (capped) |
| `board` | object | `board_create` | id, name, privacy |
| `boards` | array | `board_list` | items + `bookmark` |
| `user` | object | `user_get` | username, id, account_type |
| `pins` | array | `trigger` | Recent pins + `bookmark` for next poll |
| `error` | string | failure | Provider message; API error code `2` = invalid token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | From `x-ratelimit-reset` / `Retry-After` |

## Legal

- Developer & API Terms: https://developers.pinterest.com/terms/
- Developer Guidelines: https://policy.pinterest.com/developer-guidelines/
- Access tiers (Trial vs Standard): https://developers.pinterest.com/docs/key-concepts/access-tiers/
- Rate limits: https://developers.pinterest.com/docs/reference/rate-limits/
- Pin create: https://developers.pinterest.com/docs/api/v5/pins-create/
- OAuth: https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/
- Trial: **1000 req/day/app** overall; `org_write` **300/day**. Standard: **100 req/s/user/app** overall; `org_write` **100/min/user/app**. Honor `x-ratelimit-limit`, `x-ratelimit-remaining`, `x-ratelimit-reset`. Trial-created pins/boards are **not** publicly visible. Ads/conversion events and `ads:*` scopes are out of this node. Pin content may include PII in descriptions — do not log bodies. No cookie/session capture.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. Hosts: `api.pinterest.com`, `api-sandbox.pinterest.com`, plus Pinterest-issued media `upload_url` host for video.
- Header `Authorization: Bearer {access_token}`, `Content-Type: application/json` (multipart only for media upload as documented).
- Empty token → `{ error }` no HTTP. JSON cap **1_000_000** bytes. Timeout 25 s (upload 120 s).
- 401 + error code 2 → `{ error }` (expired token). 429 → `{ error, http_status: 429, retry_after_seconds }` from reset header; **no unbounded retry**.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch. Logs: operation, pin/board ids, status, rate-limit remaining — never tokens or image bytes.
- `trigger` is a single poll per invocation (BPMN timer for cadence), not a long-lived HTTP hold.
