# FullStory

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/fullstory/`
- **Researched:** 2026-08-20
- **Official API:** Fullstory **Server API V2** (`api.fullstory.com/v2`)
- **Docs:** https://developer.fullstory.com/server/getting-started/ — Users https://developer.fullstory.com/server/users/introduction/ — List Sessions https://developer.fullstory.com/server/sessions/list-sessions/ — Get Session Events https://developer.fullstory.com/server/sessions/get-session-events/
- **Auth:** `api_key` — secret binding: `apiKey` (`Authorization: Basic {API_KEY}` per V2 Users intro)

## Scope

One node for Fullstory **Server API V2**. Users CRUD/list, list session replay URLs, and (where entitled) session event payloads. **Session replay and event streams are highly sensitive** (keystrokes, DOM, emails, support context). This node **does not download raw replay video files**; it returns provider **replay URLs and event JSON**. Get Session Events is part of **Anywhere: Activation** (paid). Segment listing/export remains on **V1** per getting-started — **omitted** (no guessed V2 path). **Customer owns the Fullstory org and DPA.** Never log session URLs, emails, `uid`, or event bodies.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `user.get` | GET | `/v2/users/{id}` | Fetch Fullstory user by FS id |
| `user.search` | GET | `/v2/users` | List users matching filter criteria |
| `session.search` | GET | `/v2/sessions` | Recent replay URLs by `uid`, `email`, or `session_uid` |
| `event.search` | GET | `/v2/sessions/{sessionId}/events` | Captured events for one session (Activation) |

Omitted: `segment.list` (V1 Segment Export only — do not invent a V2 route). V1 Set User Properties is retired; use V2 `POST /v2/users` only if a later catalog revision adds `user.upsert`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `user.get` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | Key from Fullstory app; Admin/Architect required for many reads |

`session.search` / `event.search` return replay URLs / event JSON. Select them only when the customer DPA covers recordings/events. OfficialApiNode does not enforce a product flag.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | No API key; replay URLs only if opted in |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = user.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Fullstory-generated user id |

### `operation = user.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | no | Filter (query names from live List Users OpenAPI at build) |
| `uid` | string | no | Customer user id |
| `limit` | number | no | Page size |
| `paginationToken` | string | no | Next page token if the 200 body includes one |

The List Users HTML page does not currently print query parameter names; copy them from the interactive “Request” panel / OpenAPI on https://developer.fullstory.com/server/users/list-users/ at implementation.

### `operation = session.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `uid` | string | one of uid/email/`session_uid` | Customer uid |
| `email` | string | one of | User email (URL-encode) |
| `session_uid` | string | one of | Mutually exclusive with uid/email |
| `limit` | number | no | Max sessions; default 20; single page only |

### `operation = event.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sessionId` | string | yes | Provider session id (`userId:sessionId` form; URL-encode `:`) |
| `limit` | number | no | If the events endpoint paginates |

Cap response size; do not dump full DOM. Select only when the customer DPA covers event payloads.

## Output fields (by operation)

### `user.get` / `user.search`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `id` | string | `user.get` | FS user id |
| `user` | object | `user.get` | Profile fields (`uid`, `email`, `display_name`, `app_url`) — PII |
| `users` | object[] | `user.search` | Matching users |
| `nextPaginationToken` | string | when present | Next page |
| `error` | object | failure | `{ status, code, message }` |

### `session.search`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `sessions` | object[] | success | Map from provider `results[]`: `{ id, created_time, app_url }` replay metadata |
| `error` | object | failure | `{ status, code, message }` or opt-in denied |

### `event.search`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `sessionId` | string | success | Echo id |
| `events` | object[] | success | Event list (highly sensitive) |
| `error` | object | failure | `{ status, code, message }` including Activation/plan 403 |

## Legal

- Terms: https://www.fullstory.com/legal/terms-of-service/
- Privacy / DPA: https://www.fullstory.com/legal/privacy-policy/ and customer DPA (operator must have one for session data)
- Data residency: requests to `api.fullstory.com` route to `na1` or `eu1` from the **key prefix / login host** (`app.fullstory.com` vs `app.eu1.fullstory.com`). Do not pin a wrong region.
- Server event quota: exceeding monthly server-event quota → `429` (getting-started).
- Session recordings may include health, financial, or authentication data if the customer’s privacy rules failed. StudioQuack must not retain replay URLs in logs or long-lived stores.
- Official-API-first: `api.fullstory.com` only. ToS forbids scraping the replay player.

## Implementation notes

- JDK `HttpClient` only. Host: `api.fullstory.com`.
- Auth: `Authorization: Basic <apiKey>` as documented on Users V2 (do not invent Bearer unless a later official page changes this).
- Timeouts: connect 10s, read 30s (`event.search` read 60s). Caps: 1 MiB users/sessions; **512 KiB** events (truncate with `{ error }` if larger — do not stream unbounded DOM).
- Retry: `429` honor `Retry-After`; `5xx` backoff + jitter, max 3.
- Redact `email`, `uid`, `fsUrl`, and event payloads from all logs.
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
