# Mailgun

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/mailgun/`
- **Researched:** 2026-08-20
- **Official API:** Mailgun Email API (URI-versioned; send `v3`, logs `v1`, domain webhooks `v3`/`v4`)
- **Docs:** [API overview / regions](https://documentation.mailgun.com/docs/mailgun/api-reference/api-overview) · [Send messages](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/POST_v3--name--messages) · [Logs (current)](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/logs/post-v1-analytics-logs) · [Securing webhooks](https://documentation.mailgun.com/docs/mailgun/user-manual/webhooks/securing-webhooks) · [Authentication](https://documentation.mailgun.com/docs/mailgun/api-reference/mg-auth)
- **Auth:** `api_key` — secret binding: `api_key` (HTTP Basic username `api`)

## Scope

Sends mail and reads delivery events for one Mailgun **sending domain**. Base URL is **region-bound**: US `https://api.mailgun.net`, EU `https://api.eu.mailgun.net`. Message data never leaves the region; API keys are global. `listen` is Mailgun Event Webhooks (delivered / permanent_fail / temporary_fail / complained / unsubscribed / opened / clicked), verified with the **HTTP webhook signing key** (not the API key). Same node, never a sibling.

**Do not build:** GET `/v3/{domain}/events` as the primary event surface (official docs: **deprecated** in favor of Logs API `POST /v1/analytics/logs`); Validate / Optimize products; mailing-list CRUD; inbound Routes MIME store; SMTP relay (use the Email node); Alerts webhook signing keys (different product).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/v3/{domain}/messages` | `multipart/form-data` send. Need `text`, `html`, `amp-html`, or `template`. |
| `get-event` | POST | `/v1/analytics/logs` | One event: Logs query filtered by `message-id` (or event id) + tight time window. |
| `list-events` | POST | `/v1/analytics/logs` | Page of events for the domain / filters. |
| `listen` | POST (inbound) | Domain or account webhook → ingress | HMAC-SHA256(`timestamp` + `token`) with webhook signing key. |

Seed names `get-event` / `list-events` are kept; they map to the **Logs API**, not the deprecated Events GET.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `api_key` | password | yes | — | **yes** | Private API key. Basic auth password; username is literal `api`. Domain Sending Keys may only call `/messages`. Events/logs/webhooks need an account key. |
| `domain` | string | yes | — | no | Sending domain (e.g. `mg.example.com`) |
| `base_url` | select | yes | `https://api.mailgun.net` | no | `https://api.mailgun.net` (US) or `https://api.eu.mailgun.net` (EU). Must match where the domain was created. |
| `webhook_signing_key` | password | `listen` | — | **yes** | HTTP webhook signing key from API Keys. Not the API key. |
| `register_webhook` | boolean | no | `false` | no | If true, `listen` POSTs `/v3/domains/{domain}/webhooks` (or `/v1/webhooks` account-level) |
| `webhook_url` | string | when registering | — | no | HTTPS StudioQuack ingress URL |
| `timeout_seconds` | number | no | `25` | no | Connect+read. 0 / negative / blank → 25 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `api_key` / signing key |

FUNCTION ports: none. Attachments: `file` ref-ids; handler streams into multipart `attachment` parts. Never inline bytes.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | yes | RFC 5322 mailbox, authorized on the domain |
| `to` | array of string | yes | Recipients |
| `cc` / `bcc` | array of string | no | Cc / Bcc |
| `subject` | string | no | Subject |
| `text` | string | one of text/html/template | Plain body |
| `html` | string | one of text/html/template | HTML body |
| `template` | string | one of | Stored template name |
| `template_variables` | object | no | `t:variables` JSON |
| `attachments` | array of `{file, filename?, mime_type?}` | no | `file` = ref-id → multipart `attachment` |
| `inline` | array of `{file, filename?, content_id}` | no | Inline CID images (`file` ref-id) |
| `tags` | array of string | no | `o:tag` (max per account plan) |
| `user_variables` | object | no | `v:` custom vars echoed on webhooks |
| `testmode` | boolean | no | `o:testmode` |
| `require_tls` | boolean | no | `o:require-tls` |

Send options (`o:`, `h:`, `v:`, `t:`) total **≤ 16 KB**.

### `operation = get-event`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | one of message_id / event_id | Mailgun id from send (`id` in send response) |
| `event_id` | string | one of | Log item id if known |
| `start` / `end` | string | no | RFC 2822 window (default last 24h) |
| `duration` | string | no | e.g. `1d`, `2h` — if set, overwrites start |

### `operation = list-events`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `start` / `end` | string | no | RFC 2822 |
| `duration` | string | no | e.g. `1d` |
| `events` | array of string | no | e.g. `delivered`, `failed`, `complained` |
| `recipient` | string | no | Filter |
| `limit` | number | no | Page size (default 50) |
| `page_token` | string | no | Logs pagination token |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_types` | array of string | no | Domain webhook names: `accepted`, `delivered`, `permanent_fail`, `temporary_fail`, `opened`, `clicked`, `unsubscribed`, `complained`. Default: delivered + fails + complained. |
| `payload` | object | inbound | Body `{ signature, event-data }` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `id` | string | send-email | Mailgun message id (`<…@domain>`) |
| `message` | string | send-email | Provider queued text |
| `event` | object | get-event | First matching log item |
| `events` | array | list-events / listen | Log or webhook `event-data` objects |
| `next_page_token` | string | list-events | Pagination |
| `error` | string | failure | Never secrets |

## Legal

- [Mailgun Terms of Service](https://www.mailgun.com/legal/terms/) and [Acceptable Use](https://www.mailgun.com/legal/aup/)
- **EU vs US:** pick `base_url` to match domain region. Message bodies, events, suppressions stay in-region ([overview](https://documentation.mailgun.com/docs/mailgun/api-reference/api-overview)).
- Recipients and event payloads are personal data. Honor complaints (`complained`) and unsubscribes; keep sending to complained addresses is AUP/CAN-SPAM risk.
- Rate limits: `X-RateLimit-Limit` / `Remaining` / `Reset`. HTTP 429 → wait until Reset.
- Official-API-first: JDK HTTP to the configured Mailgun host only.

## Implementation notes

- JDK HTTP only. No Mailgun SDK.
- Auth: `Authorization: Basic base64("api:" + api_key)`.
- Send: `multipart/form-data` (not JSON). Resolve `file` refs into parts named `attachment` / `inline`.
- Logs: JSON POST `/v1/analytics/logs`. Always include a domain filter matching config `domain`.
- **Webhook verify:** HMAC-SHA256 of `timestamp` concatenated with `token` (no separator), key = `webhook_signing_key`, hex compare to `signature.signature` (constant-time). Reject if timestamp skew > 15 minutes. Cache `token` to drop replays. Subaccount payloads may include `parent-signature`.
- `listen` same node; StudioQuack message-event ingress. Register domain webhooks with POST `/v3/domains/{domain}/webhooks` (`id` = event name, `url`). Changes can take up to 10 minutes.
- Retry 429/5xx with jitter, max 3. Map 401/403 to `{ error }` (wrong region or sending-only key used for logs).
- Errors never throw past dispatch.

### Example

Send success: `{ "status": "ok", "id": "<msg@mg.example.com>", "message": "Queued. Thank you." }`.
