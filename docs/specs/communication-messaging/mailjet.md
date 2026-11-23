# Mailjet

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/mailjet/`
- **Researched:** 2026-08-20
- **Official API:** Mailjet Email Send API **v3.1** + Email REST **v3** (webhooks) + SMS API **v4**
- **Docs:** [Versioning](https://dev.mailjet.com/email/reference/overview/versioning/) · [Send API v3.1](https://dev.mailjet.com/email/guides/send-api-v31/) · [Send emails reference](https://dev.mailjet.com/email/reference/send-emails/) · [Event callback URLs](https://dev.mailjet.com/email/reference/webhook/) · [Event tracking](https://documentation.mailjet.com/hc/en-us/articles/360043578154-Event-Tracking-API) · SMS: `POST https://api.mailjet.com/v4/sms-send`
- **Auth:** Email = Basic (`api_key` + `secret_key`); SMS = Bearer (`sms_token`) — secret bindings: `secret_key`, `sms_token`

## Scope

One node for Mailjet **Email** and **SMS**. Email send uses **Send API v3.1** (`POST /v3.1/send`, max 50 messages/call, detailed errors, sandbox flag). Webhooks use REST v3 ` /v3/REST/eventcallbackurl`. SMS uses **v4** with a **separate SMS Bearer token** from the SMS UI — Email API keys cannot send SMS. `listen` is Email Event API (sent, open, click, bounce, spam, blocked, unsub), same node.

**Do not build:** Send API **v3** `/v3/send` (higher batch, weaker errors) as the default — v3.1 is current; contact/list/campaign REST (CRM category); SMS delivery webhooks as a second node (optional later on this `listen` if Mailjet documents SMS callbacks — **unverified 2026-08-20**, omit SMS from `listen` until confirmed).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/v3.1/send` | Inline `TextPart` / `HTMLPart` |
| `send-email-template` | POST | `/v3.1/send` | `TemplateID` + `TemplateLanguage: true` + `Variables` |
| `send-sms` | POST | `/v4/sms-send` | Transactional SMS. Bearer token. E.164 `To`. |
| `listen` | POST (inbound) | `/v3/REST/eventcallbackurl` + inbound | Email events. HTTPS + HTTP Basic. **No HMAC.** |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `api_key` | password | email ops | — | **yes** | Public API key (Basic user) |
| `secret_key` | password | email ops | — | **yes** | Secret API key (Basic password) |
| `sms_token` | password | `send-sms` | — | **yes** | SMS Bearer from app.mailjet.com SMS section. Not the Email secret. |
| `webhook_basic_user` | string | `listen` | — | no | Basic user on callback URL |
| `webhook_basic_password` | password | `listen` | — | **yes** | Basic password |
| `register_webhook` | boolean | no | `false` | no | POST eventcallbackurl |
| `webhook_url` | string | when registering | — | no | HTTPS (not a site root) |
| `timeout_seconds` | number | no | `25` | no | 0 / negative / blank → 25 |

EU residency: Mailjet states customer data is stored in **EU** data centres (Sinch/Mailjet). There is **no** Mailgun-style `api.eu.mailjet.com` split; host is `https://api.mailjet.com`. Execute a DPA with Sinch/Mailjet for EEA personal data. **Unverified:** a US-only processing addendum distinct from EU hosting — cite live DPA at implement time.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never keys/tokens |

FUNCTION ports: none. Attachments: `file` ref-ids → Mailjet `Attachments[].Base64Content`. Never inline bytes on the port. Official send docs show Base64; the handler encodes after resolving the ref.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | `{email, name?}` | yes | Validated sender |
| `to` | array of `{email, name?}` | yes | Recipients |
| `cc` / `bcc` | array | no | |
| `subject` | string | yes | Subject |
| `text_part` / `html_part` | string | one of | Body |
| `attachments` | array of `{file, filename, mime_type}` | no | `file` = ref-id |
| `inlined_attachments` | array of `{file, filename, mime_type, content_id}` | no | CID |
| `custom_id` | string | no | Correlation (`CustomID`) |
| `event_payload` | string | no | Echoed on webhooks |
| `sandbox` | boolean | no | Validate only, do not send |

### `operation = send-email-template`

Same addressing/attachment/custom_id fields, plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | number | yes | Stored template |
| `template_language` | boolean | no | Default **true** |
| `variables` | object | no | Template variables |
| `subject` | string | no | Override |

Do not send `HTMLPart` together with `TemplateID`.

### `operation = send-sms`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | yes | Alphanumeric sender ID (carrier rules apply) |
| `to` | string | yes | E.164 (`+336…`) |
| `text` | string | yes | Body (concat/encoding limits per Mailjet SMS guide) |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_type` | string | when registering | `sent` \| `open` \| `click` \| `bounce` \| `spam` \| `blocked` \| `unsub`. Register once per type or use grouped Version=2. |
| `payload` | array or object | inbound | Event object(s); grouped payloads are arrays |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `messages` | array | send-email* | Per-message `{ status, to, message_id, … }` from v3.1 `Messages` |
| `message_id` | string | SMS | SMS `MessageId` |
| `sms_count` | number | SMS | Segments |
| `event` / `events` | object / array | listen | Normalized `{ event, time, email, error, hard_bounce, … }` |
| `error` | string | failure | Never secrets |

v3.1 may return HTTP 200 with per-message `Status: error`. Treat mixed results like Postmark batch: include `messages`; `{ error }` only if the HTTP call failed or every message failed.

## Legal

- [Mailjet Terms](https://www.mailjet.com/legal/terms/) · [Privacy](https://www.mailjet.com/privacy-policy/) · Sinch/Mailjet DPA for GDPR
- Email and SMS credentials are **separate**. SMS is telecom: sender-ID rules, TCPA/GDPR consent. Do not send SMS with Email keys.
- Recipients and event payloads are personal data. Honor `spam` / `unsub` / bounce.
- CAN-SPAM for commercial email.
- Official-API-first: JDK HTTP to `api.mailjet.com`.

## Implementation notes

- JDK HTTP only. Email: `Authorization: Basic base64(api_key + ":" + secret_key)`. SMS: `Authorization: Bearer <sms_token>`.
- Send v3.1 JSON `{ "Messages": [ { … } ] }`. This node sends **one** Messages element per invoke (workflow loops via BPMN). Do not silently pack 50 unless input is already an array named `messages` — keep input as one message to match 1-in/1-out simplicity. If `messages` array is present, cap at 50.
- Webhooks: **no signature**. HTTPS + Basic (`https://user:pass@host/path` or ingress Basic). `Version: 2` groups events (~1/s). Non-200 retries ~30s for 24h.
- `listen` same node; StudioQuack message-event ingress.
- Retry 429/5xx with jitter. Never throw past dispatch.

### Example

Email success (parsed): `{ "status": "ok", "messages": [{ "status": "success", "message_id": "…" }] }`.
