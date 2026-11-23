# Twilio

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/twilio/`
- **Researched:** 2026-08-20
- **Official API:** Twilio Programmable Messaging REST API (Messages resource `2010-04-01`) + Programmable Voice REST API (Calls resource `2010-04-01`)
- **Docs:** https://www.twilio.com/docs/messaging/api · https://www.twilio.com/docs/messaging/api/message-resource · https://www.twilio.com/docs/whatsapp/api · https://www.twilio.com/docs/voice/api · https://www.twilio.com/docs/voice/api/call-resource · https://www.twilio.com/docs/usage/webhooks/webhooks-security
- **Auth:** HTTP Basic — `account_sid` + `auth_token` **or** API Key SID + API Key secret (preferred) — secret binding: `auth_token` / `api_key_secret`. Webhook HMAC always uses the **Account Auth Token**.

## Scope

Calls Twilio over HTTPS (`https://api.twilio.com/2010-04-01/Accounts/{AccountSid}/…`) to send SMS/MMS, send WhatsApp **through Twilio’s WhatsApp channel**, place a TTS outbound call, fetch/list Message resources, and register inbound + status webhooks (`listen`). JDK HTTP only. No Twilio SDK.

**Twilio WhatsApp is not Meta Cloud API.** `send-whatsapp` uses the same Messages resource as SMS, with `From` / `To` in `whatsapp:+E.164` form (Twilio WhatsApp Business Platform channel). It does **not** call `graph.facebook.com`. The separate planned `whatsapp` node (`WhatsApp Business Cloud API`) is the Meta Graph path. Operators who already have a Twilio-hosted WABA use **this** node; operators on Meta Cloud API directly use `whatsapp`. Do not mix credentials or webhook secrets across the two nodes.

IE1 (Dublin) residency: set `api_base_url` to `https://api.dublin.ie1.twilio.com/2010-04-01` and use region-specific credentials ([Messaging API with Twilio regions](https://www.twilio.com/docs/global-infrastructure/messaging-api-with-twilio-regions)).

**Do not build:** Conversations API, Studio flows, Verify, Lookup, Content Template CRUD, RCS as a first-class verb (same Messages resource if added later), TwiML bins, conference/queue/recording management, Meta Graph WhatsApp.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-sms` | POST | `/Accounts/{AccountSid}/Messages.json` | Outbound SMS. `To` + (`From` or `MessagingServiceSid`) + `Body`. |
| `send-mms` | POST | `/Accounts/{AccountSid}/Messages.json` | Outbound MMS. Same as SMS plus one or more `MediaUrl` values. |
| `send-whatsapp` | POST | `/Accounts/{AccountSid}/Messages.json` | WhatsApp via **Twilio channel**. `From=whatsapp:+…`, `To=whatsapp:+…`. Not Meta Cloud API. |
| `make-call-tts` | POST | `/Accounts/{AccountSid}/Calls.json` | Outbound call. Handler builds TwiML `<Response><Say>…</Say></Response>` into `Twiml` (no `Url` required). |
| `get-message` | GET | `/Accounts/{AccountSid}/Messages/{MessageSid}.json` | Fetch one Message resource. |
| `list-messages` | GET | `/Accounts/{AccountSid}/Messages.json` | Page messages (`To`, `From`, `DateSent`, `PageSize`). |
| `listen` | inbound + optional POST | Number/Messaging Service webhooks + `StatusCallback`; signature [webhooks-security](https://www.twilio.com/docs/usage/webhooks/webhooks-security) | Status callbacks, inbound SMS/MMS/WhatsApp, Voice status. Operation on **this** node — never a sibling trigger node. |

Retired / unofficial verbs are omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-sms` | no | Chooses the row above. |
| `auth_type` | select | yes | `api_key` | no | `api_key` (API Key SID + secret, preferred) \| `auth_token` (Account SID + Auth Token). |
| `account_sid` | string | yes | — | no | `AC…`. Path `{AccountSid}` on every 2010-04-01 call. |
| `auth_token` | password | if `auth_type=auth_token` **or** any `listen` HMAC | — | **yes** | Account Auth Token. **Required for `X-Twilio-Signature` even when REST uses an API key.** Never logged. |
| `api_key_sid` | string | if `auth_type=api_key` | — | no | API Key SID (`SK…`). Basic username. |
| `api_key_secret` | password | if `auth_type=api_key` | — | **yes** | API Key secret. Basic password. Never logged. |
| `from` | string | no | — | no | Default sender: E.164 Twilio number, alphanumeric sender, short code, or `whatsapp:+E.164`. Overridable per invoke. |
| `messaging_service_sid` | string | no | — | no | Default `MG…`. If set, POST uses `MessagingServiceSid` instead of `From` unless input supplies `from`. |
| `api_base_url` | string | no | `https://api.twilio.com/2010-04-01` | no | Override for IE1: `https://api.dublin.ie1.twilio.com/2010-04-01`. Must be HTTPS. |
| `status_callback_url` | string | no | — | no | Default `StatusCallback` HTTPS URL (StudioQuack message-event ingress). |
| `timeout_seconds` | number | no | `30` | no | Connect+read timeout. 0/negative → 30. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. MMS/WhatsApp media use `MediaUrl`. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include Auth Token, API secrets, or TwiML bodies with PII. |

FUNCTION ports: none.

## Input fields (by operation)

Shared send fields (`send-sms`, `send-mms`, `send-whatsapp`):

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes | Destination E.164 (`+15551234567`). For WhatsApp the handler prefixes `whatsapp:` if missing. |
| `from` | string | if config `from` and `messaging_service_sid` both blank | Sender. SMS/MMS: E.164 / alphanumeric / short code. WhatsApp: `whatsapp:+E.164` (Twilio WA-enabled number). |
| `messaging_service_sid` | string | no | Overrides config. Mutually exclusive with per-request `from` when both set — `from` wins. |
| `body` | string | SMS/WhatsApp text; MMS if no media | Message text. SMS segment rules apply (GSM-7 160 / UCS-2 70). |
| `status_callback_url` | string | no | Per-message `StatusCallback`. Overrides config. |
| `validity_period` | number | no | Seconds Twilio keeps an undelivered message (optional Twilio field). |
| `risk_check` | string | no | `enable` (default) \| `disable` — Twilio `RiskCheck`. |

### `operation = send-sms`

Uses the shared send table. Do not send `MediaUrl`. US 10DLC numbers must be on a registered A2P campaign; unregistered traffic is surcharged/filtered.

### `operation = send-mms`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media` | file \| file[] | yes (or `body` + media) | Platform file ref-ids. Handler resolves each to an HTTPS URL Twilio can GET (`MediaUrl`, repeatable). Never inline bytes. US/Canada MMS; other countries may reject. |
| `body` | string | no | Caption. |

### `operation = send-whatsapp`

**Channel: Twilio Programmable Messaging WhatsApp** (`whatsapp:+…` addresses). **Not** Meta Cloud API (`/{phone-number-id}/messages` on Graph).

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes | Recipient. E.164 or `whatsapp:+E.164`. |
| `from` | string | if config blank | Twilio WhatsApp sender `whatsapp:+E.164`. |
| `body` | string | session / freeform | Body. Template-first traffic (outside 24h window) must use a WhatsApp template approved on the Twilio-hosted WABA — pass `content_sid` when required. |
| `content_sid` | string | templates | Twilio Content SID (`HX…`) for WhatsApp templates. |
| `content_variables` | object | no | JSON map of template variables (Twilio `ContentVariables` stringified JSON). |
| `media` | file \| file[] | no | Media via `MediaUrl` from file ref-ids (images/audio/video/documents WhatsApp allows). |

WhatsApp opt-in is mandatory ([Twilio WhatsApp overview](https://www.twilio.com/docs/whatsapp/api)). Sending without opt-in can suspend the WABA.

### `operation = make-call-tts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes | Destination E.164, SIP, or client identity. |
| `from` | string | if config blank | Twilio number or verified caller ID (E.164). |
| `text` | string | yes | Spoken text. Handler XML-escapes and wraps: `<Response><Say>{text}</Say></Response>` as `Twiml`. Do not accept raw TwiML from INPUT (injection). |
| `voice` | string | no | `<Say voice="…">`. Twilio `man` / `woman` / `alice` or a Neural/Google/Amazon voice name Twilio documents. Default `alice`. |
| `language` | string | no | `<Say language="en-US">` (BCP-47 as Twilio lists). |
| `record` | boolean | no | Default `false`. If true, set `Record=true`. Recordings are personal data — consent laws may apply. |
| `status_callback_url` | string | no | Call status callback. |
| `timeout` | number | no | Seconds to wait for answer (`Timeout`). |

Do not expose a generic `url` TwiML fetch in v1 (SSRF). TTS-only.

### `operation = get-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_sid` | string | yes | `SM…` / `MM…`. |

Media on the resource: list Media subresource URLs; if the workflow needs bytes, a later download must store a platform `file` ref-id (out of v1 unless added). Do not put media bytes on OUTPUT.

### `operation = list-messages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | no | Filter. |
| `from` | string | no | Filter. |
| `date_sent` | string | no | `YYYY-MM-DD` (`DateSent`). |
| `date_sent_after` / `date_sent_before` | string | no | Inequality filters Twilio documents (`DateSent>` / `DateSent<`). |
| `page_size` | number | no | 1–1000; default 50. |
| `page_token` | string | no | Twilio pagination token from previous `next_page_uri`. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (return the ingress URL + HMAC instructions; optionally persist `status_callback_url` onto subsequent sends) \| `event` (Twilio already POSTed to StudioQuack webhook ingress). |
| `event_kind` | string | `event` | `message_status` \| `inbound_message` \| `call_status`. |

Inbound: `application/x-www-form-urlencoded` (`From`, `To`, `Body`, `MessageSid`, `MessageStatus`, `CallSid`, …). Validate `X-Twilio-Signature` with HMAC-SHA1 over the **exact** webhook URL + sorted POST params, key = Account Auth Token ([webhooks-security](https://www.twilio.com/docs/usage/webhooks/webhooks-security)). JSON webhooks: validate `bodySHA256` query + signature. Reject unsigned requests. Echo HTTP 200 quickly.

`listen` is not a second node.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Validation or `Twilio API Error: HTTP {code} {code}/{message}`. Never Auth Token / API secret. |
| `http_status` | number | HTTP error | Vendor status. |
| `twilio_error_code` | number | Twilio error body | e.g. 21610 unsubscribed, 30003 unreachable. |
| `sid` | string | send/get/call | Message SID (`SM`/`MM`) or Call SID (`CA`). |
| `message_status` | string | send/get | Twilio `queued`/`sending`/`sent`/`delivered`/`undelivered`/`failed`/`read` (WA). |
| `call_status` | string | make-call-tts | `queued`/`ringing`/`in-progress`/… |
| `to` / `from` | string | send/get | Addresses as Twilio stored them. |
| `num_segments` | string | SMS | Segment count. |
| `num_media` | string | MMS/WA | Media count. |
| `price` / `price_unit` | string | get-message | When Twilio has priced the resource. |
| `date_created` / `date_sent` | string | get/list | RFC 2822 timestamps Twilio returns. |
| `messages` | array | list-messages | `{ sid, status, to, from, date_sent, num_segments }` — **no Body**. |
| `next_page_uri` | string | list-messages | Opaque next page. |
| `event` | object | listen `event` | Normalized `{ event_kind, message_sid, call_sid, message_status, from, to, body }` (body is PII — OUTPUT only, never logs). |
| `signature_valid` | boolean | listen `event` | HMAC result. If false → `{ error }` and do not correlate. |

## Legal

- Twilio Terms of Service: https://www.twilio.com/en-us/legal/tos
- Acceptable Use Policy: https://www.twilio.com/en-us/legal/aup
- Messaging policies / A2P 10DLC: https://www.twilio.com/docs/messaging/compliance/a2p-10dlc
- WhatsApp (Twilio channel) opt-in: https://www.twilio.com/docs/whatsapp/api
- Privacy: https://www.twilio.com/en-us/legal/privacy
- Webhook security: https://www.twilio.com/docs/usage/webhooks/webhooks-security
- Rate limits: concurrent call and message throughput are account/campaign-specific (10DLC campaign throughput, toll-free verified limits). HTTP 429 → back off. Do not scrape Console HTML.
- **US A2P 10DLC:** all application SMS/MMS to US long codes must be Brand + Campaign registered. Unregistered traffic is filtered and surcharged. From **2026-06-30**, new 10DLC campaign API registrations require public `PrivacyPolicyUrl` and `TermsAndConditionsUrl`.
- **TCPA / consent:** US marketing SMS needs prior express written consent; STOP/HELP handling; quiet hours. Canada CASL, EU ePrivacy similarly. StudioQuack does not collect consent.
- **GDPR:** message bodies, numbers, recordings, and WhatsApp content are personal data. Recordings (`record=true`) may require two-party consent (varies by jurisdiction). Do not log `Body`, TwiML `Say` text, or media bytes.
- Official-API-first: `api.twilio.com` only. No Console scrape.

## Implementation notes

- JDK HTTP only. Basic auth: `Authorization: Basic base64(username:password)` where username is `api_key_sid` or `account_sid` and password is `api_key_secret` or `auth_token`. POST bodies `application/x-www-form-urlencoded`. Accept `application/json` (`.json` suffix).
- Timeouts: `timeout_seconds` default 30. Size cap: reject `body` > 1600 SMS chars (Twilio concatenates; still cap to avoid accidental novels) and media files > Twilio MMS/WhatsApp limits (check live docs; typical MMS 5 MB, WhatsApp per-type caps). Over → `{ error }` without calling Twilio.
- Retry: 429/503 truncated exponential backoff, honor `Retry-After`. Idempotent GET retry OK. **Do not retry POST send/call** unless the operator supplies an idempotency key (Twilio has no general Idempotency-Key on Messages v2010 — treat send as at-least-once; warn in docs). Max 3 retries on GET.
- Media: send short-lived HTTPS URLs the Twilio edge can fetch in `MediaUrl`. Never put bytes in `MediaUrl`.
- WhatsApp addressing: if `operation=send-whatsapp` and `to`/`from` lack `whatsapp:` prefix, prepend `whatsapp:`.
- TTS: XML-escape `text` (`&`, `<`, `>`). Ignore any input `twiml` field.
- HMAC: implement the Twilio signature algorithm in JDK `Mac.getInstance("HmacSHA1")` with Auth Token. Compare in constant time. Fail closed.
- HTTP 4xx/5xx → `{ error, http_status, twilio_error_code }` on OUTPUT. Never throw past dispatch.
- Redact Auth Token, API secret, and message bodies from logs and error strings (`***`).
