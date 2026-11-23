# WhatsApp

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md` (canonical). Discovery cross-ref in `docs/catalog/social-media.md` — **same node**. Social `trigger` = this `listen`.
- **Folder when built:** `nodes/whatsapp/`
- **Researched:** 2026-08-20
- **Official API:** WhatsApp Business **Cloud API** on **Meta Graph API** (docs examples as of 2026-05-21 use `v26.0`; pin via config)
- **Docs:** https://developers.facebook.com/docs/whatsapp/cloud-api/overview/ · Messages: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages · Media: https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media/ · Webhooks: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/set-up-webhooks/
- **Auth:** Meta system-user (or user) access token — secret binding: `access_token`. Webhooks: `app_secret` (HMAC) + `verify_token`.

## Scope

Sends and receives WhatsApp messages **only** through the **official WhatsApp Business Cloud API** (`https://graph.facebook.com/{version}/{phone-number-id}/messages`). JDK HTTP. Media via `POST /{phone-number-id}/media`, `GET /{media-id}`, `DELETE /{media-id}`, then GET the short-lived media URL.

`listen` is **Meta Graph webhooks** (`whatsapp_business_messaging` / `messages` field) with **X-Hub-Signature-256** (HMAC-SHA256 of the raw body with `app_secret`).

`send-and-wait` is **StudioQuack correlation** (send a session or template message, wait for an inbound `messages` webhook from the same `wa_id`) — not a Meta RPC.

**Customer-care window:** free-form **service** messages only while a 24-hour window is open (user messaged or called the business). Outside that window, **only pre-approved template messages** (`send-template`).

**DO NOT BUILD:** WhatsApp Web multi-device protocol, Baileys, whatsapp-web.js, QR-session scrape, or any unofficial “personal WhatsApp” API. Twilio WhatsApp is a **different** catalog row (`Twilio`) — do not mix hosts.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-message` | POST | `/{version}/{phone-number-id}/messages` | Session/service message: `type` text, image, audio, document, video, sticker, location, contacts, interactive, reaction, … `messaging_product=whatsapp`. |
| `send-template` | POST | same | `type=template` with approved `name` + `language.code` + `components`. Required outside the 24h window. |
| `send-and-wait` | POST + correlate | same then webhook | Send then wait for inbound user message. StudioQuack, not Graph. |
| `mark-read` | POST | same | Body `status=read`, `message_id`. First-class; not in catalog seed. Optional `typing_indicator`. |
| `upload-media` | POST | `/{version}/{phone-number-id}/media` | multipart `messaging_product=whatsapp` + `file` + MIME `type`. Returns media id (stored ~30 days). |
| `download-media` | GET + GET | `/{version}/{media-id}` then GET `{url}` | Retrieve URL (expires ~5 minutes) then download with Bearer → platform `file`. |
| `delete-media` | DELETE | `/{version}/{media-id}?phone_number_id=` | Delete uploaded media. |
| `listen` | inbound GET/POST | Meta webhook | GET: hub challenge. POST: `entry.changes.value.messages` / `statuses`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-message` | no | Chooses the row above. |
| `graph_version` | string | no | `v26.0` | no | Graph version segment. Official send-messages guide (updated 2026-05-21) uses `v26.0`. Older pins (`v23.0`) still work until Meta retires them. |
| `graph_base_url` | string | no | `https://graph.facebook.com` | no | Official Graph host. |
| `access_token` | password | yes | — | **yes** | System-user token with `whatsapp_business_messaging` (and management as needed). Never on INPUT. |
| `phone_number_id` | string | yes | — | no | Cloud API phone-number id (not the E.164 display number). |
| `app_secret` | password | `listen` | — | **yes** | Meta app secret for `X-Hub-Signature-256`. |
| `verify_token` | password | `listen` | — | **yes** | Operator-chosen string; must match App Dashboard webhook verify token. |
| `default_to` | string | no | — | no | Default recipient E.164 (`+15551234567`). |
| `send_and_wait_timeout_seconds` | number | no | `3600` | no | Correlation wait (still bounded by the 24h window for session replies). |
| `timeout_seconds` | number | no | `30` | no | Graph JSON timeout. |
| `upload_timeout_seconds` | number | no | `120` | no | Media upload/download. |
| `max_upload_bytes` | number | no | `16777216` | no | Enforce WhatsApp type-specific caps (images 5 MB, docs 100 MB, video 16 MB typical — fail closed using Meta’s current media limits table). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | No access token. Media is `file` ref-id or Meta `media_id`. |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Message bodies must not appear in logs. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | if config blank | Recipient E.164 **with** `+` and country code (Cloud API prepends the business country if `+` is omitted — misdelivery risk). |
| `recipient_type` | string | no | `individual` (default) \| `group`. |
| `type` | string | yes | `text` \| `image` \| `audio` \| `document` \| `video` \| `sticker` \| `location` \| `contacts` \| `interactive` \| `reaction` \| … |
| `body` | string | `text` | Text body. |
| `preview_url` | boolean | no | Link preview for text. |
| `media_id` | string | media types | Meta media id from `upload-media`. |
| `file` | file | media, alternative | Upload first (handler may chain upload+send in one invoke). Prefer explicit `upload-media` then id. |
| `link` | string | media, alternative | Public HTTPS URL (Cloud API caches ~10 min). Prefer `file` / `media_id` (SSRF). |
| `caption` | string | no | Caption where supported. |
| `filename` | string | document | Document filename. |
| `latitude` / `longitude` / `name` / `address` | number/string | location | Location fields. |
| `context_message_id` | string | no | Quote/reply (`context.message_id`). |
| `reaction_emoji` | string | `reaction` | Emoji. |
| `interactive` | object | `interactive` | Buttons/list/CTA/flow JSON as Meta documents. |

If the 24h window is closed, Graph returns an error — OUTPUT `{ error }` (do not throw). Workflows that must reach cold users use `send-template`.

### `operation = send-template`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | if config blank | Recipient. |
| `template_name` | string | yes | Approved template name. |
| `language_code` | string | yes | e.g. `en_US`. |
| `components` | object | no | Header/body/button parameters (JSON array). Header media uses `media_id` or upload handle — `file` ref-ids converted by the handler. |

### `operation = send-and-wait`

Union of `send-message` **or** `send-template` fields plus:

| Field | Type | Required | Purpose |
|---|---|---|---|
| `send_kind` | string | no | `session` (default) \| `template`. |
| `correlation_timeout_seconds` | number | no | Override wait. |

Success OUTPUT includes the inbound `message_id` / `from` / `type` (text not logged).

### `operation = mark-read`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | Inbound wamid (`wamid.…`). |
| `typing_indicator` | boolean | no | If true, send typing indicator + read (Cloud API combined call as currently documented). |

### `operation = upload-media`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform ref-id. |
| `mime_type` | string | yes | e.g. `image/jpeg`, `application/pdf`. Must match WhatsApp’s allow-list. |

### `operation = download-media` / `delete-media`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `media_id` | string | yes | Meta media id. |
| `filename` | string | download, no | Stored name. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. Graph 200 only means **accepted**, not delivered. |
| `error` | string | failure | Graph error message/code. No tokens. |
| `http_status` | number | HTTP error | Graph status. |
| `message_id` | string | send | `messages[0].id` (`wamid.…`). |
| `wa_id` | string | send | `contacts[0].wa_id`. |
| `message_status` | string | template pacing | `messages[0].message_status` if present. |
| `media_id` | string | upload-media | Uploaded id. |
| `file` | file | download-media | Platform ref-id. |
| `mime_type` | string | download | From media GET. |
| `sha256` | string | download metadata | Graph media hash if returned. |
| `event` | object | listen | `{ kind: message\|status, from, message_id, type, timestamp, status }` — omit text/media bytes from logs. |
| `reply_message_id` | string | send-and-wait | Inbound wamid. |

## Legal

- Meta Platform Terms: https://developers.facebook.com/terms
- WhatsApp Business / Commerce Policy: https://www.whatsapp.com/legal/business-policy (confirm live URL)
- Cloud API overview: https://developers.facebook.com/docs/whatsapp/cloud-api/overview/
- Opt-in required. Quality rating / messaging limits in WhatsApp Manager. Template quality scores can pause sending.
- 24h customer-service window vs templates is a **policy + technical** gate — do not attempt unofficial session bypass.
- Message bodies, phone numbers, and media are personal data; possible PHI. Media on Meta servers ~30 days unless deleted.
- One webhook endpoint per Meta app (typically). Honor HMAC; reject unsigned bodies.
- Unofficial WhatsApp Web clients violate WhatsApp terms — **out of scope forever** for this node.

## Implementation notes

- JDK HTTP. Header `Authorization: Bearer {access_token}`, `Content-Type: application/json` for messages; multipart for media upload.
- Default version `v26.0` (researched 2026-08-20 from official send-messages examples). Make `graph_version` configurable without code change.
- Upload: `-F messaging_product=whatsapp -F type={mime} -F file=@…`. Download: GET media id (optional `phone_number_id` query) → `url` → GET url **with Bearer** (fails without token). Do not persist the temporary URL.
- Webhook GET: if `hub.mode=subscribe` and `hub.verify_token` matches, return **plain text** `hub.challenge` 200.
- Webhook POST: HMAC SHA-256 of raw body, prefix `sha256=`, compare to `X-Hub-Signature-256`. On success, 200 quickly. Parse `object=whatsapp_business_account`.
- Timeouts: 30s JSON, 120s media. Retry 429/5xx with backoff; Meta error 4xx (window closed, invalid template) → OUTPUT error, no retry.
- Delivery order of multiple sends is **not** guaranteed; wait for `delivered` status webhook before sequencing.
- TTL: non-auth messages retried up to ~30 days; auth templates ~10 minutes — if no `delivered` webhook, assume dropped.
- Never throw past dispatch. Never log tokens, app secret, template bodies, or user text.
- Egress: `graph.facebook.com` and the media CDN host returned by Graph (allowlist dynamically or `lookaside.fbsbx.com` / documented media hosts).
