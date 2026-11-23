# Pushover

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/pushover/`
- **Researched:** 2026-08-20
- **Official API:** Pushover Message API v1 (`api.pushover.net/1`)
- **Docs:** https://pushover.net/api · receipts https://pushover.net/api/receipts
- **Auth:** api_key — secret bindings: `api_token` (application token) + `user_key` (user or group key)

## Scope

One node that sends push notifications through the **official Pushover Message API** and lists notification sounds for the application token. HTTPS POST to `https://api.pushover.net/1/messages.json` with application `token` + recipient `user` (user key or group key). Optional image attachments use platform type `file` (multipart `attachment`, max 5 242 880 bytes).

Emergency priority (`priority=2`) **requires** `retry` (minimum 30 seconds) and `expire` (maximum 10 800 seconds / 3 hours). Retries are capped at 50. Emergency traffic consumes the monthly message quota on each retry and is billed as extra capacity when the account exceeds the included 10 000 messages/month (teams 25 000). The API returns a `receipt` on priority 2; this node **returns** that receipt on OUTPUT and does **not** poll or cancel receipts (receipts API is out of scope).

Do not implement Glances, Groups admin, Subscription, Teams, Licensing, Open Client, or end-to-end `encrypted=1` payloads.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `push` | POST | `/1/messages.json` | Send a notification to a user or group |
| `list-sounds` | GET | `/1/sounds.json?token={api_token}` | Sounds the app token may send (built-in + custom uploads) |

Base origin: `https://api.pushover.net`. HTTPS required. Prefer `application/x-www-form-urlencoded` unless an attachment is present (`multipart/form-data`). JSON request bodies with `Content-Type: application/json` are also accepted by the API; form encoding is the documented default.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `push` | no | Chooses the row above |
| `api_token` | password | yes | empty | yes | Application API token (30 chars `[A-Za-z0-9]`). Form/query `token`. Never on INPUT. |
| `user_key` | password | yes for `push` | empty | yes | Recipient user **or** group key (30 chars `[A-Za-z0-9]`). Form `user`. Never on INPUT. |
| `device` | string | no | empty | no | Optional default device name(s), comma-separated, max 25 chars each `[A-Za-z0-9_-]` |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `api_token` / `user_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = push`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message` | string | yes | Body, max 1024 UTF-8 characters |
| `title` | string | no | Title, max 250 characters (else the application name) |
| `device` | string | no | Override config `device`. Ignored when `user_key` is a non-Team group or a comma-separated multi-user list |
| `html` | boolean | no | `true` → form `html=1`. Mutually exclusive with `monospace` |
| `monospace` | boolean | no | `true` → form `monospace=1`. Mutually exclusive with `html` |
| `priority` | number | no | `-2` lowest, `-1` low, `0` normal (default), `1` high, `2` emergency |
| `retry` | number | if `priority=2` | Seconds between emergency retries. Minimum **30** |
| `expire` | number | if `priority=2` | Seconds to keep retrying. Maximum **10800**. Cap 50 retries regardless |
| `callback` | string | no | Public HTTPS URL Pushover POSTs when an emergency is acknowledged (receipts/callbacks). Optional; this node does not host it |
| `sound` | string | no | Sound key from `list-sounds` (or blank for the user’s default) |
| `timestamp` | number | no | Unix timestamp to display instead of receive time |
| `ttl` | number | no | Seconds until the device deletes the message. Ignored when `priority=2`. Must be > 0 |
| `url` | string | no | Supplementary URL, max 512 characters |
| `url_title` | string | no | Label for `url`, max 100 characters |
| `attachment` | file | no | One image, platform file ref. Max 5 242 880 bytes. Multipart field `attachment` with filename + MIME. Do not send `attachment_base64` when multipart works |

Validation before HTTP:

- Missing `message` / empty `api_token` / empty `user_key` → `{ error }` no HTTP.
- `html` and `monospace` both true → `{ error }`.
- `priority=2` without `retry`/`expire`, `retry < 30`, or `expire > 10800` → `{ error }` naming the rule (emergency extra quota cost).
- `attachment` over 5 242 880 bytes → `{ error }` no HTTP.

### `operation = list-sounds`

No input fields. Token from config only.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx and provider `status=1` | `ok` |
| `request` | string | success | Pushover request id (support correlation) |
| `receipt` | string | `push` and `priority=2` | 30-char receipt `[A-Za-z0-9]` for the receipts API (not called here) |
| `sounds` | object | `list-sounds` | Map of sound key → description (built-in + custom) |
| `limit` | number | `push` when header present | `X-Limit-App-Limit` (account monthly cap) |
| `remaining` | number | `push` when header present | `X-Limit-App-Remaining` |
| `reset` | number | `push` when header present | `X-Limit-App-Reset` Unix timestamp (Central Time month boundary) |
| `error` | string | failure | Validation or `Pushover API Error: HTTP …` plus truncated `errors[]`. Secrets redacted |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; **5** on 5xx (Pushover: do not retry sooner than 5 s) |

Provider JSON `status: 0` with HTTP 4xx is a failure even if a body is returned. Do not retry 4xx.

## Legal

- Terms of Use: https://pushover.net/terms (updated 2026-05-01). Users must be 16+. No unsolicited push / spam. API tokens must not be shared or used to circumvent monthly limits.
- Privacy: https://pushover.net/privacy
- Quota: 10 000 messages/user/month included; 25 000 for Teams; extra capacity is purchased. HTTP **429** when exhausted. Reset 00:00:00 Central Time on the 1st.
- Friendly-use: at most **2 concurrent** connections to the API; sequential keep-alive is preferred. IPs that flood 4xx are temporarily blocked.
- Message bodies and image attachments may contain personal data. They leave the customer sandbox toward `api.pushover.net`. Do not log `message`, titles, or file bytes.
- PHI: do not send electronic protected health information unless the operator’s contract with Pushover covers it. This node adds TLS only.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Pushover SDK.
- Form fields: `token`, `user`, `message`, plus optional documented keys. Header `User-Agent: StudioQuack/1.0`.
- Empty secrets → `{ error: "Missing Pushover api_token (and user_key for push)." }` — no HTTP.
- Attachment: resolve `file` ref to bytes in-process; `multipart/form-data`; do not fetch operator-supplied remote URLs (Pushover forbids URL-based attachments).
- Response body cap **64_000** bytes. Timeout default 25 s.
- Map `errors` array into `error` (join, 240 chars). Redact `api_token` / `user_key` to `***`.
- Do not auto-retry 4xx. 5xx: surface `retry_after_seconds: 5`; do not tight-loop inside the handler.
- Logs: operation, HTTP status, attachment byte length, `request` id — never tokens, never message text.
- **Do not build:** `/1/receipts/{receipt}.json`, cancel receipt, cancel-by-tag, `/1/users/validate.json`, `/1/apps/limits.json` as operations, Glances, Groups CRUD, Subscription API, Teams, Licensing, Open Client, XML variants.
