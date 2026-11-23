# Postmark

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/postmark/`
- **Researched:** 2026-08-20
- **Official API:** Postmark REST API (current; base `https://api.postmarkapp.com`)
- **Docs:** [Overview / tokens](https://postmarkapp.com/developer/api/overview) · [Email API](https://postmarkapp.com/developer/api/email-api) · [Templates](https://postmarkapp.com/developer/api/templates-api) · [Bounces](https://postmarkapp.com/developer/api/bounce-api) · [Webhooks](https://postmarkapp.com/developer/api/webhooks-api) · [Webhook security](https://postmarkapp.com/developer/webhooks/webhooks-overview)
- **Auth:** `api_key` — secret binding: `server_token` (optional unused `account_token` is out of scope)

## Scope

Sends **application / transactional** email through one Postmark **Server**. All seed operations use the **Server Token** (`X-Postmark-Server-Token`). Postmark is not a list-upload marketing ESP; bulk newsletters belong on a **Broadcast Message Stream** if the operator uses streams, and this node still must not grow into campaign/list tooling. Default stream is `outbound` (transactional). `listen` is Postmark webhooks (Delivery, Bounce, SpamComplaint, plus optional Open/Click/SubscriptionChange) on the same node.

**Do not build:** Account-token APIs (domains, sender signatures, servers, data-removal); `POST /email/bulk` campaign Bulk API; inbound parse as a separate node; template CRUD (`/templates`) — send-with-template only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/email` | One message. Max 50 recipients (To+Cc+Bcc), 10 MB payload. |
| `send-template` | POST | `/email/withTemplate` | Server template by `TemplateId` or `TemplateAlias` + `TemplateModel`. |
| `send-batch` | POST | `/email/batch` | Up to **500** messages, 50 MB. HTTP 200 even if some rows fail — inspect each `ErrorCode`. |
| `get-bounce` | GET | `/bounces/{bounceid}` | Single bounce. |
| `list-bounces` | GET | `/bounces` | Page; **`count` and `offset` required**. |
| `listen` | POST (inbound) | Webhooks API + inbound POST | Delivery / Bounce / SpamComplaint. **No HMAC.** HTTP Basic + HTTPS. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `server_token` | password | yes | — | **yes** | Server API token. Header `X-Postmark-Server-Token`. Test value `POSTMARK_API_TEST` validates without sending. |
| `message_stream` | string | no | `outbound` | no | Stream id. Transactional default `outbound`. Do not send promo blasts on transactional streams (Postmark may pause sending). |
| `webhook_basic_user` | string | `listen` | — | no | Basic auth user Postmark will send |
| `webhook_basic_password` | password | `listen` | — | **yes** | Basic auth password. Compare inbound `Authorization`. |
| `register_webhook` | boolean | no | `false` | no | If true, POST `/webhooks` with `HttpAuth` + triggers |
| `webhook_url` | string | when registering | — | no | HTTPS ingress URL (no embedded user:pass if using `HttpAuth` object) |
| `timeout_seconds` | number | no | `25` | no | 0 / negative / blank → 25 |

`account_token` / `X-Postmark-Account-Token`: **not a config param**. Seed ops do not need it.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | yes | Confirmed Sender Signature (`Name <addr@domain>`) |
| `to` | string | yes | Comma-separated, max 50 combined with cc/bcc |
| `cc` / `bcc` | string | no | Comma-separated |
| `subject` | string | yes | Subject |
| `html_body` | string | one of html/text | HTML |
| `text_body` | string | one of html/text | Plain |
| `reply_to` | string | no | Reply-To |
| `tag` | string | no | Stats tag, max 1000 chars |
| `metadata` | object | no | Correlation key/values |
| `track_opens` | boolean | no | Open tracking |
| `track_links` | string | no | `None` \| `HtmlAndText` \| `HtmlOnly` \| `TextOnly` |
| `attachments` | array of `{file, filename, mime_type, content_id?}` | no | `file` = ref-id |
| `message_stream` | string | no | Overrides config |

### `operation = send-template`

Same addressing/attachment fields as send-email, plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | number | one of id/alias | Numeric template id |
| `template_alias` | string | one of id/alias | Alias |
| `template_model` | object | yes | Merge model JSON |

Do not send both a full HtmlBody and a template.

### `operation = send-batch`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `messages` | array | yes | 1–500 objects, each shaped like `send-email` input |

### `operation = get-bounce`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bounce_id` | number | yes | Bounce id |

### `operation = list-bounces`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `count` | number | yes | Page size, max 500 |
| `offset` | number | yes | Skip |
| `type` | string | no | Bounce type filter |
| `inactive` | boolean | no | Inactive recipients |
| `email_filter` | string | no | Address contains |
| `tag` | string | no | Tag |
| `message_id` | string | no | Correlate to send `MessageID` |
| `from_date` / `to_date` | string | no | Eastern Time `YYYY-MM-DD…` |
| `message_stream` | string | no | Default `outbound` |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `triggers` | object | no | Enable Delivery, Bounce (`IncludeContent` default false), SpamComplaint. Default all three. |
| `payload` | object | inbound | JSON with `RecordType`, `MessageID`, … |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `message_id` | string | send / template | Correlate webhooks |
| `submitted_at` | string | send | Timestamp |
| `results` | array | send-batch | Per-row `{ error_code, message, message_id, to }` |
| `bounce` | object | get-bounce | Bounce record (email is PII) |
| `bounces` | array | list-bounces | Page |
| `total_count` | number | list-bounces | Total matching |
| `event` | object | listen | `record_type`, `message_id`, `type`, `email`, `description` |
| `error` | string | failure | Never tokens |

Batch: HTTP 200 with mixed `ErrorCode` ≠ 0 is **partial success**. `status` is `ok` if any sent; include `results`. If all fail, `{ error }` summarizing first error plus `results`.

## Legal

- [Postmark Terms of Service](https://postmarkapp.com/terms-of-service) / [Acceptable Use](https://postmarkapp.com/acceptable-use)
- Transactional-first product. Promotional bulk on a **transactional** stream can pause the server. This node defaults `message_stream=outbound`. Broadcast streams are allowed only when the operator sets `message_stream` to a broadcast stream id they created — still no list-upload UI.
- Bounce and complaint handling is mandatory for reputation. Use `listen` or `list-bounces`; stop mailing inactive addresses (Error 406).
- Recipients, bounce dumps, and webhook content are personal data. `IncludeContent` on bounce webhooks is **off** by default (bodies).
- Official-API-first: JDK HTTP to `api.postmarkapp.com`.

## Implementation notes

- JDK HTTP only. No Postmark SDK.
- Headers: `Accept: application/json`, `Content-Type: application/json`, `X-Postmark-Server-Token`.
- **Webhooks are unsigned.** Protect ingress with HTTP Basic (`webhook_basic_*`) and TLS. Optionally allowlist Postmark’s published webhook IPs. Validate `RecordType` + `MessageID` before correlating. 403 stops retries; non-2xx retries.
- `listen` same node; StudioQuack message-event ingress.
- Attachments: resolve `file` → Base64 `Content`. Do not put bytes on INPUT.
- Retry 429/5xx with jitter. 422 validation → `{ error }` no retry.
- Never throw past dispatch.

### Example

`send-email` success: `{ "status": "ok", "message_id": "0a129aee-…", "submitted_at": "…" }`.
