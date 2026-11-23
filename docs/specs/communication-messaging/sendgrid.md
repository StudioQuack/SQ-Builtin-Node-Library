# SendGrid

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/sendgrid/`
- **Researched:** 2026-08-20
- **Official API:** Twilio SendGrid Web API v3
- **Docs:** [Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send) · [Marketing Contacts](https://www.twilio.com/docs/sendgrid/api-reference/contacts/add-or-update-a-contact) · [Marketing Lists](https://www.twilio.com/docs/sendgrid/api-reference/lists/get-all-lists) · [Event Webhook](https://www.twilio.com/docs/sendgrid/for-developers/tracking-events/event) · [Signed Event Webhook](https://www.twilio.com/docs/sendgrid/for-developers/tracking-events/getting-started-event-webhook-security-features)
- **Auth:** `api_key` — secret binding: `api_key`

## Scope

Calls **Twilio SendGrid Web API v3** at `https://api.sendgrid.com`. Mail Send (`POST /v3/mail/send`) and Marketing Campaigns Contacts/Lists (`/v3/marketing/…`) are **two official v3 surfaces** on one node. `listen` is the Event Webhook (delivered / bounced / dropped / deferred / spam_report / unsubscribe / open / click), not a sibling node. Do not call Mail Send v2, the SMTP API, or scrape the dashboard.

**Do not build:** Mail Send v2 (`/v3` only); SMTP API / `unique_args` (v3 uses `custom_args`); Single Sends / Marketing Campaigns *send*; Stats; Suppressions CRUD; Inbound Parse (use `listen` Event Webhook); OAuth2 Event Webhook client-credentials (optional extra — ECDSA signed webhook is the required verify path).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/v3/mail/send` | Transactional send. HTTP **202** with empty body on success. |
| `upsert-contact` | PUT | `/v3/marketing/contacts` | Insert or update up to 30 000 contacts / 6 MB. Async `job_id`. |
| `get-contact` | POST | `/v3/marketing/contacts/search/identifiers/{identifier_type}` | Fetch by `email` / `phone_number_id` / `external_id` / `anonymous_id` (up to 100). |
| `list-contacts` | GET | `/v3/marketing/contacts` | Up to **50** most recently uploaded contacts. Pagination is retired. |
| `delete-contact` | DELETE | `/v3/marketing/contacts?ids=` | Delete by comma-separated contact UUIDs. Async job. |
| `create-list` | POST | `/v3/marketing/lists` | Create a static Marketing list. |
| `get-list` | GET | `/v3/marketing/lists/{id}` | List metadata; optional `contact_sample`. |
| `list-lists` | GET | `/v3/marketing/lists` | Page of lists (`page_size`, `page_token`). |
| `update-list` | PATCH | `/v3/marketing/lists/{id}` | Rename a list. |
| `delete-list` | DELETE | `/v3/marketing/lists/{id}` | Delete list; `delete_contacts=true` also deletes members. |
| `listen` | POST (inbound) | Event Webhook → StudioQuack message-event ingress | Verify ECDSA signature; emit delivery/engagement events. Register via [Webhooks API](https://www.twilio.com/docs/sendgrid/api-reference/webhooks) if `register_webhook=true`. |

`get-contact` by SendGrid UUID: if input has `contact_id` only, GET `/v3/marketing/contacts/{id}` instead of the identifiers search.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `api_key` | password | yes | — | **yes** | SendGrid API key. Header `Authorization: Bearer`. Full Access or Mail Send + Marketing + Webhooks scopes as needed. Never log. |
| `from_name` | string | no | — | no | Default From display name |
| `webhook_verification_key` | password | `listen` | — | **yes** | Event Webhook **public** verification key (PEM). ECDSA P-256. Not the API key. |
| `register_webhook` | boolean | no | `false` | no | If true, `listen` POSTs `/v3/user/webhooks/event/settings` with `webhook_url` |
| `webhook_url` | string | `listen` when registering | — | no | HTTPS StudioQuack message-event ingress URL |
| `timeout_seconds` | number | no | `25` | no | Connect+read timeout. 0 / negative / blank → 25 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secrets never on this port |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` or the verification key |

FUNCTION ports: none.

Mail Send attachments go in INPUT `attachments` (official body array). There is no top-level `file` input.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | array of `{email, name?}` | yes | Recipients (personalizations[0].to) |
| `cc` | array of `{email, name?}` | no | Cc |
| `bcc` | array of `{email, name?}` | no | Bcc |
| `from` | `{email, name?}` | yes for send-email | Verified sender. Must match a [Sender Authentication](https://www.twilio.com/docs/sendgrid/ui/sending-email/sender-verification) identity. |
| `reply_to` | `{email, name?}` | no | Reply-To |
| `subject` | string | yes | Subject |
| `html` | string | one of html/text | HTML body |
| `text` | string | one of html/text | Plain body |
| `attachments` | array of `{file, filename?, mime_type?, content_id?, disposition?}` | no | `file` = platform ref-id |
| `custom_args` | object | no | v3 custom arguments echoed on Event Webhook (correlation) |
| `categories` | array of string | no | Max 10 |
| `send_at` | number | no | Unix send-at |
| `sandbox` | boolean | no | `mail_settings.sandbox_mode.enable` — validate, do not deliver |

### `operation = upsert-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contacts` | array | yes | Each needs `email` or another identifier. Reserved fields: `first_name`, `last_name`, address fields, `custom_fields` (field **IDs**, already created). |
| `list_ids` | array of uuid | no | Add contacts to these lists |

### `operation = get-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | one of | SendGrid contact UUID → GET by id |
| `identifier_type` | string | with identifiers | `email` \| `phone_number_id` \| `external_id` \| `anonymous_id` |
| `identifiers` | array of string | with type | Values to match (emails lowercased) |

### `operation = list-contacts`

No required fields. Optional `list_id` is **not** a server-side filter on this GET (sample of 50 recent). For search, use `get-contact` with SGQL via identifiers, not a second list op.

### `operation = delete-contact`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_ids` | array of uuid | yes | IDs to delete |

### `operation = create-list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | List name |

### `operation = get-list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List UUID |
| `contact_sample` | boolean | no | Include a sample contact |

### `operation = list-lists`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default 100, max 1000 |
| `page_token` | string | no | From previous `_metadata.next` |

### `operation = update-list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List UUID |
| `name` | string | yes | New name |

### `operation = delete-list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | List UUID |
| `delete_contacts` | boolean | no | Also delete members (async `job_id`) |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `events` | array of string | no | Subset: `processed`, `dropped`, `delivered`, `deferred`, `bounce`, `open`, `click`, `spam_report`, `unsubscribe`, `group_unsubscribe`, `group_resubscribe`. Default: `delivered`, `bounce`, `dropped`, `spam_report`. |
| `raw_body` | string | inbound | Raw POST bytes for signature (engine supplies) |
| `signature` | string | inbound | Header `X-Twilio-Email-Event-Webhook-Signature` |
| `timestamp` | string | inbound | Header `X-Twilio-Email-Event-Webhook-Timestamp` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `message_id` | string | `send-email` | From `X-Message-Id` response header |
| `job_id` | string | upsert / delete contact / delete-list with contacts | Async Marketing job |
| `contact` / `contacts` | object / array | get / list contacts | Contact records (PII) |
| `contact_count` | number | list-contacts | Account contact count |
| `list` / `lists` | object / array | list ops | List id, name, `contact_count` |
| `page_token` | string | list-lists | Next page if present |
| `events` | array | `listen` | Parsed webhook objects (`event`, `email`, `sg_message_id`, `reason`, `custom_args`, …) |
| `error` | string | failure | HTTP status + provider message. Never keys. |

## Legal

- [Twilio SendGrid Terms of Service](https://www.twilio.com/en-us/legal/tos) and [Email API / SendGrid acceptable use](https://www.twilio.com/en-us/legal/aup)
- [CAN-SPAM](https://www.ftc.gov/business-guidance/resources/can-spam-act-compliance-guide-business): marketing mail needs a physical postal address and a working unsubscribe. Transactional mail is not a loophole for promotions.
- Verified sender / domain required. Unverified From → HTTP 403.
- Marketing contacts are personal data (GDPR/CCPA). Minimize fields; honor unsubscribes and spam reports (`listen`).
- Typical free/Essentials quotas are plan-specific (e.g. daily send caps). HTTP 429 → backoff; do not retry 4xx other than 429.
- Official-API-first: JDK `HttpClient` to `api.sendgrid.com` only.

## Implementation notes

- JDK HTTP only. No SendGrid Java SDK.
- Headers: `Authorization: Bearer <api_key>`, `Content-Type: application/json`, `Accept: application/json`.
- Mail Send success is **202** with empty body — treat as success; capture `X-Message-Id`.
- Upsert is **202** + `job_id`. Do not poll import status in v1 unless added later.
- **Signed Event Webhook:** concatenate timestamp bytes + **raw** body bytes; SHA-256; ECDSA verify with the public key (P-256). Do not JSON-parse before hashing. Libraries that re-serialize JSON will fail verify.
- `listen` is the same node. Inbound events are delivered through the StudioQuack engine's message-event ingress. Reject unsigned payloads when `webhook_verification_key` is set (required in production).
- Timeouts: `timeout_seconds`. Payload cap: 20 MiB ingress already; Mail Send attachments resolved from file refs, total MIME within SendGrid’s published limit (~30 MB).
- Retry: 429 / 5xx with jitter, max 3. Never retry 401/403.
- Errors: HTTP 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch. Redact Bearer token.
- Correlation: put a workflow/run id in `custom_args` on send; `listen` echoes it for BPMN message correlation.

### Examples

Send:

```json
{
  "to": [{"email": "ada@example.com", "name": "Ada"}],
  "subject": "Receipt",
  "html": "<p>Thanks</p>",
  "custom_args": {"sq_run_id": "run_123"},
  "attachments": [{"file": "file_ref_abc", "filename": "receipt.pdf", "mime_type": "application/pdf"}]
}
```

Success: `{ "status": "ok", "message_id": "…" }`.
