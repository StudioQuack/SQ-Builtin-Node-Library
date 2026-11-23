# Gotify

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/gotify/`
- **Researched:** 2026-08-20
- **Official API:** Gotify REST-API 2.1.0 (OpenAPI/Swagger on gotify/server)
- **Docs:** https://gotify.net/api-docs · push https://gotify.net/docs/pushmsg · extras https://gotify.net/docs/msgextras · spec https://github.com/gotify/server/blob/master/docs/spec.json
- **Auth:** api_key — secret bindings: `app_token` (send) and `client_token` (read / stream)

## Scope

One node for a **customer-hosted Gotify server**. There is no public multi-tenant SaaS comparable to ntfy.sh; `base_url` is always the operator’s origin.

Two token kinds (official):

- **`appToken`**: applications **send** messages (`POST /message`). Tokens typically start with `A`. Shown once on create/rotate (Gotify 3+).
- **`clientToken`**: clients **read**, delete, and **stream** (`GET /message`, `DELETE /message/{id}`, WebSocket `/stream`). Tokens typically start with `C`.

Send with `X-Gotify-Key: {app_token}` (or `Authorization: Bearer`, or `?token=` — prefer header). List/delete/listen require `client_token`. Using an app token on GET `/message` fails.

**`listen` is in scope.** Official `GET /stream` is a WebSocket (`operationId: streamMessages`) that returns newly created messages as JSON. Catalog seed omitted `listen`; add it because the stream is documented. Prefer `X-Gotify-Key` / `Authorization` on the handshake — **do not** put the client token in the WebSocket URL query if headers work (query `token=` is documented but leaks in logs).

Gotify does **not** upload binary attachments on `POST /message`. Images on notifications are extras: `client::notification.bigImageUrl` (HTTPS URL). Platform type `file` is accepted only when the runtime can supply a fetchable HTTPS URL for that ref; otherwise require `image_url`. Never POST file bytes as the message body.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-message` | POST | `/message` | Send (app token). JSON body; `message` required |
| `list-messages` | GET | `/message` | History (client token). `limit` 1–200, `since` |
| `delete-message` | DELETE | `/message/{id}` | Delete one message (client token) |
| `listen` | WebSocket | `/stream` | Real-time new messages (client token) |

Do not expose `DELETE /message` (delete **all**) as an operation.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `create-message` | no | Chooses the row above |
| `base_url` | string | yes | empty | no | Gotify origin, e.g. `https://push.example.com`. No trailing slash. HTTPS default |
| `app_token` | password | `create-message` | empty | yes | Application token. Header `X-Gotify-Key` |
| `client_token` | password | list / delete / listen | empty | yes | Client token. Header `X-Gotify-Key` |
| `allow_insecure_http` | boolean | no | `false` | no | Permit `http://` on a private host only |
| `timeout_seconds` | number | no | `25` | no | REST timeout |
| `listen_idle_seconds` | number | no | `300` | no | Stream idle cap. Floor 30, cap 3600 |

SSRF: same posture as other customer-URL nodes — https only unless opted in; block metadata IPs; `Redirect.NEVER`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No tokens |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message` | string | yes | Body. Markdown allowed by the API; default extras display `text/plain` to reduce injection |
| `title` | string | no | Title |
| `priority` | number | no | Client presentation (Android sound). Typical 0–10; omit → application default |
| `content_type` | string | no | `text/plain` (default) or `text/markdown` → extras `client::display.contentType` |
| `click_url` | string | no | extras `client::notification.click.url` |
| `image_url` | string | no | extras `client::notification.bigImageUrl` (HTTPS URL) |
| `image` | file | no | Only if the file ref resolves to a public HTTPS URL; mapped to `bigImageUrl`. Else `{ error }` instructing to use `image_url`. Never upload bytes to Gotify |
| `extras` | object | no | Merged last. Keys `namespace::action`. Reserved: `client::*`, `android::*`, `ios::*`, `server::*` |

POST `Content-Type: application/json` (extras are **rejected** on multipart/form).

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | 1–200, default 100 |
| `since` | number | no | Return messages with id **less than** this (paging) |

### `operation = delete-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Message id |

### `operation = listen`

No required input. Optional `listen_idle_seconds` override. Emit each WebSocket JSON message that is a Gotify `Message` (id, appid, message, title, priority, extras, date). Connection stays open until idle timeout or cancel.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `id` | number | create / listen | Message id |
| `appid` | number | when present | Application id |
| `title` | string | when present | |
| `message` | string | when present | |
| `priority` | number | when present | |
| `extras` | object | when present | |
| `date` | string | when present | |
| `messages` | array | `list-messages` | Page of messages |
| `paging` | object | list | Provider `PagedMessages.paging` (`limit`, `next`, `since`, `size`) |
| `error` | string | failure | `error` / `errorDescription` from Gotify JSON. Tokens redacted |
| `http_status` | number | HTTP error | 401 missing/wrong token |
| `retry_after_seconds` | number | 5xx / timeout | 30 |

## Legal

- Server license MIT. Operator hosts Gotify and is the controller for stored messages.
- No Gotify, Inc. ToS URL; comply with the operator’s deployment policy and any user-facing notice.
- Message bodies, titles, and extras URLs may be PII. Do not log them. Client tokens can read **all** of that user’s messages.
- Markdown extras can fetch remote images (`![](url)`); prefer `text/plain` for untrusted interpolation (official extras warning).

## Implementation notes

- JDK `HttpClient` only. Header `X-Gotify-Key` with the token for that operation. `Accept: application/json`. `User-Agent: StudioQuack/1.0`.
- Pick token by operation: create → `app_token`; else `client_token`. Wrong kind → clear `{ error }` before HTTP when the prefix is obviously wrong (`A` vs `C`) **or** after 401 from the server.
- Listen: WebSocket upgrade to `{base_url}/stream`. If the builder cannot set headers, `?token=` is the documented fallback — still redact the URL in logs.
- Body cap **1_000_000** on list. Timeout 25 s REST.
- 4xx `{ error }` from `{ "error", "errorCode", "errorDescription" }`. Do not retry 4xx.
- **Do not build:** application/client/user admin CRUD, plugins, `DELETE /message` (all), basic-auth user login for sending (tokens only).
