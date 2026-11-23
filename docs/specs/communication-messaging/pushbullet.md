# Pushbullet

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/pushbullet/`
- **Researched:** 2026-08-20
- **Official API:** Pushbullet HTTP API v2 + Realtime Event Stream
- **Docs:** https://docs.pushbullet.com/ · stream https://docs.pushbullet.com/#realtime-event-stream
- **Auth:** api_key — secret binding: `access_token` (header `Access-Token`)

## Scope

One node for the **official Pushbullet API** at `https://api.pushbullet.com/v2`. An account **access token** (Account Settings) authenticates every call. That token sees **all devices, pushes, and chats** on the account — treat it as full-account credential, not a per-device key.

`create-push` sends note / link / file pushes. File pushes are a two-step official flow: `POST /v2/upload-request` then multipart POST to the returned `upload_url`, then `POST /v2/pushes` with `type=file`. The designer still selects only `create-push`; upload is internal. File payloads use type `file` (platform ref-id), never inline bytes on ports.

`listen` is **this node**, not a sibling trigger. Connect to `wss://stream.pushbullet.com/websocket/{access_token}`. Handle `nop` (keepalive ~30 s), `tickle` (then `GET /v2/pushes?modified_after=`), and ephemeral `push` (mirror / dismissal / clip — these do **not** appear on `/v2/pushes`). Prefer sending the token as a header if the client allows; the documented URL path still contains the token — never log the WebSocket URL.

Do not implement devices CRUD, contacts, chats, channels admin, SMS, or ephemerals send.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-push` | POST | `/v2/pushes` | Send note, link, or file (upload-request first for files) |
| `list-pushes` | GET | `/v2/pushes` | History; cursor pagination |
| `update-push` | POST | `/v2/pushes/{iden}` | Dismiss a push (`dismissed: true`) |
| `delete-push` | DELETE | `/v2/pushes/{iden}` | Delete one push |
| `listen` | WebSocket | `wss://stream.pushbullet.com/websocket/{access_token}` | Tickles + ephemerals |

Base origin: `https://api.pushbullet.com`. POST bodies `Content-Type: application/json`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-push` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Account token. Header `Access-Token`. Full account access. Never on INPUT. |
| `timeout_seconds` | number | no | `25` | no | HTTP connect+read. Floor 1 s. Not the listen idle window |
| `listen_idle_seconds` | number | no | `300` | no | `listen` only: close after this idle (nop resets). Floor 30, cap 3600 |
| `max_upload_bytes` | number | no | `26214400` | no | Cap file uploads (API advertises 26 214 400). Reject larger before upload-request |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No token. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo `access_token` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-push`

Only **one** target: omit all → broadcast to every device on the token’s account. At most one of `device_iden`, `email`, `channel_tag`, `client_iden`.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `type` | string | yes | `note` \| `link` \| `file` |
| `title` | string | no | Title (all types) |
| `body` | string | no | Body / caption |
| `url` | string | `type=link` | Link URL |
| `file_name` | string | no | Override name for `type=file` (else the ref’s name) |
| `file_type` | string | no | MIME override (else detected / `application/octet-stream`) |
| `device_iden` | string | no | Single device target |
| `email` | string | no | Pushbullet user if registered, else email fallback |
| `channel_tag` | string | no | Channel the token owner must own |
| `client_iden` | string | no | OAuth client the token owner must own |
| `guid` | string | no | Client idempotency key |
| `source_device_iden` | string | no | Optional sending device |

### `operation = list-pushes`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `modified_after` | number | no | Unix timestamp (float ok); only pushes modified after |
| `active` | boolean | no | Default `true` (omit deleted) |
| `cursor` | string | no | Pagination cursor |
| `limit` | number | no | Page size (API default if omitted) |

### `operation = update-push`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `iden` | string | yes | Push iden |
| `dismissed` | boolean | yes | Only documented updatable field; typically `true` |

### `operation = delete-push`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `iden` | string | yes | Push iden |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `modified_after` | number | no | Seed for tickle refetch; default now minus a small skew |
| `emit_ephemerals` | boolean | no | Default `true`. When `false`, ignore stream `type=push` (mirrors/clips) |
| `emit_tickle_pushes` | boolean | no | Default `true`. On `tickle` subtype `push`, GET `/v2/pushes?modified_after=` and emit those pushes |

`listen` is long-running on the daemon. Keepalives (`nop`) never hit OUTPUT. Each emitted event is one OUTPUT object, then the socket stays open until idle timeout, cancel, or error.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `iden` | string | create/update | Push iden |
| `type` | string | create/update/listen push | `note` / `link` / `file` / ephemeral subtype |
| `title` | string | when present | |
| `body` | string | when present | |
| `url` | string | link | |
| `file_name` | string | file | |
| `file_type` | string | file | |
| `file_url` | string | file | Provider download URL (not a StudioQuack file ref unless the handler optionally copies — v1: pass through URL only, do not fetch into the sandbox by default) |
| `dismissed` | boolean | update / list items | |
| `pushes` | array | `list-pushes` | Push objects (most recently modified first) |
| `cursor` | string | list when present | Next page |
| `event` | string | `listen` | `tickle` \| `push` \| `pushes_sync` |
| `subtype` | string | tickle | `push` \| `device` |
| `push` | object | listen ephemeral | Provider `push` object |
| `error` | string | failure | No token. HTTP 401/403: token invalid or revoked |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` if present else 60 / 30 |

## Legal

- Terms: https://www.pushbullet.com/tos (page is JS-rendered; confirm live before ship). API docs: https://docs.pushbullet.com/
- The access token is equivalent to the user session. Leakage exposes every device and historical pushes (titles, bodies, file URLs).
- Push bodies, emails, and file names are personal data. Do not log them. File bytes stay in tmpfs for the upload hop only.
- Rate limiting is not published as a fixed RPM on the docs page fetched 2026-08-20; honor 429.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. JSON POST. Header `Access-Token`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty token → `{ error: "Missing Pushbullet access_token." }` — no HTTP.
- **File create:** `POST /v2/upload-request` JSON `{ file_name, file_type }` → `upload_url`, `file_url`, `file_name`, `file_type`. Multipart POST file bytes to `upload_url` (no Access-Token on the upload host unless the API says otherwise — follow the live upload-request response). Then `POST /v2/pushes` with `type=file` and those three file fields.
- `max_upload_size` from `GET /v2/users/me` may be used to clamp; default cap 26 214 400.
- WebSocket: JDK `HttpClient.newWebSocketBuilder()` (Java 11+). Path token must not appear in logs; redact. Reconnect is **not** inside a single invoke beyond one idle window — BPMN timers retry `listen` if the operator wants persistence.
- On tickle `subtype=push`, GET pushes with `modified_after`; update the watermark from the newest `modified`.
- Response cap **1_000_000** bytes on list. Timeout 25 s on REST.
- 4xx: `{ error }` truncated body, token redacted. Do not auto-retry 4xx.
- **Do not build:** `DELETE /v2/pushes` (delete all), `/v2/devices`, `/v2/chats`, `/v2/ephemerals` send, SMS, OAuth app flow (token-in-config is enough for v1), contacts.
