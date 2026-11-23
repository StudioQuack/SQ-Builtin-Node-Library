# Bird (MessageBird)

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/messagebird/`
- **Researched:** 2026-08-20
- **Official API:** **Bird SMS API** (`POST /v1/sms/messages` on regional hosts `https://us1.platform.bird.com` / `https://eu1.platform.bird.com`)
- **Docs:** https://bird.com/en-us/docs/guides/sms/overview · https://bird.com/en-us/docs/guides/sms/sending-sms · https://bird.com/en-us/docs/guides/sms/migrate · https://bird.com/en-us/docs/guides/sms/migrate/connectivity-platform · https://docs.bird.com/api/channels-api · https://docs.bird.com/api/api-access/api-authorization
- **Auth:** Bearer workspace API key (`bk_{region}_…`) — secret binding: `api_key`. (Catalog “accessKey” maps to this workspace key. Do not send `Authorization: AccessKey` at `rest.messagebird.com`.)

## Scope

MessageBird **rebranded to Bird**. The historical SMS surface `https://rest.messagebird.com` (“Connectivity Platform” / MessageBird REST API, `Authorization: AccessKey`, `GET /balance`, `POST /messages`) is **legacy**. Bird’s own migration guide maps that API to **`POST /v1/sms/messages`** on `https://us1.platform.bird.com` or `https://eu1.platform.bird.com` with `Authorization: Bearer bk_{region}_…`. **This node implements that current SMS API.**

**Not this node:** Bird **Channels API** (`https://api.bird.com`, `POST /workspaces/{workspaceId}/channels/{channelId}/messages`, `Authorization: AccessKey`) — omnichannel CRM messaging (SMS/WhatsApp/Email). **Not** Conversations/Inbox API. Operators who need omnichannel should get a future channels node; this folder stays SMS.

**Do not build:** `rest.messagebird.com` (legacy); Conversations API as the SMS send path; Channels API in v1; MMS (`media_urls` is `422 SMSUnsupportedFeature` on the SMS API as of 2026-08-20); scheduled send (`scheduled_at` reserved/rejected).

**Dropped vs catalog seed:** `get-balance`. Workspace `bk_*` keys cannot call organization billing (`org:billing` is session/org-role; wallet is not a workspace SMS endpoint). Legacy `GET https://rest.messagebird.com/balance` is Connectivity Platform — **do not call it**. Low balance surfaces as HTTP **402** on send.

**Added vs seed:** `get-message` — current SMS API has a read endpoint (message id `sms_…`); catalog seed omitted it.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-sms` | POST | `{host}/v1/sms/messages` | One SMS to one recipient. HTTP **202**. |
| `get-message` | GET | `{host}/v1/sms/messages/{id}` | Read status, segments, cost after accept. |
| `listen` | POST + inbound | `{host}/v1/webhooks` subscribe `sms.*`; Standard Webhooks HMAC | `sms.accepted`/`sent`/`delivered`/`failed`/`received`/… Operation on **this** node. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-sms` | no | Chooses the row above. |
| `api_key` | password | yes | — | **yes** | Workspace key `bk_us1_…` / `bk_eu1_…`. Header `Authorization: Bearer`. Must match `region`. |
| `region` | select | yes | `us1` | no | `us1` \| `eu1`. Host `https://{region}.platform.bird.com`. |
| `from` | string | no | — | no | Default sender: owned E.164, alphanumeric ID (1–11, at least one letter), or short code. |
| `category` | select | no | `transactional` | no | Default free-text category: `transactional` \| `marketing` \| `authentication` \| `service`. |
| `webhook_signing_secret` | password | `listen` | — | **yes** | Standard Webhooks secret for `webhook-signature`. |
| `timeout_seconds` | number | no | `30` | no | Connect+read timeout. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `bk_*` keys. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-sms`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes | One recipient E.164 (`+31612345678`). No recipient arrays. |
| `from` | string | yes unless `template` | Sender. US does not accept alphanumeric IDs. |
| `text` | string | yes unless `template` | Body. Cap **12 segments** (~1836 GSM-7 / 804 UCS-2). Bird does **not** truncate; over → 422. |
| `category` | string | yes unless `template` | `transactional` \| `marketing` \| `authentication` \| `service`. |
| `template` | object | no | Stored template send. Mutually exclusive with `text`/`from`/`category`. |
| `smart_encoding` | boolean | no | `options.smart_encoding`. Default false (alters body). |
| `tags` | object[] | no | `{ name, value }[]` max 20. Filter dimensions. |
| `metadata` | object | no | Arbitrary JSON ≤ 2 KB; echoed on webhooks. |
| `idempotency_key` | string | no | Header `Idempotency-Key` for safe retries. |

Enable destination countries in Bird dashboard (default-deny). Unenabled country → `422 SMSDestinationNotEnabled`. Empty wallet → **402**.

Do **not** send reserved fields (`scheduled_at`, `media_urls`, …) — `422 SMSUnsupportedFeature`.

### `operation = get-message`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_id` | string | yes | `sms_…` from send. Immediate GET after 202 may 404 briefly — one short retry is OK. |

Authentication category bodies may be returned as `**REDACTED**` on read.

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (POST `/v1/webhooks` with event types) \| `event` (already delivered). |
| `callback_url` | string | `start` | HTTPS StudioQuack message-event ingress. |
| `event_types` | string[] | no | Default `sms.accepted`, `sms.sent`, `sms.delivered`, `sms.undelivered`, `sms.failed`, `sms.expired`, `sms.rejected`, `sms.received`. |

Verify Standard Webhooks: HMAC-SHA256 over `{webhook-id}.{webhook-timestamp}.{raw body}`, headers `webhook-id`, `webhook-timestamp`, `webhook-signature`. Deduplicate on `webhook-id`. At-least-once, unordered.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Validation or `Bird SMS API Error: HTTP {code}`. |
| `http_status` | number | HTTP error | 402 wallet, 422 validation, … |
| `message_id` | string | send/get | `sms_…`. |
| `message_status` | string | send/get | `accepted` on 202; later `sent`/`delivered`/… |
| `segments` | object | get | `{ count, encoding, characters }`. |
| `cost` | object | get after pricing | `{ amount, currency_code }` or null at accept time. |
| `webhook_id` | string | listen `start` | Subscription id. |
| `event` | object | listen `event` | `{ type, sms_id, to, from, text, metadata }` (text PII). |
| `signature_valid` | boolean | listen `event` | Fail closed. |

## Legal

- Bird Terms of Service: https://bird.com/legal/terms-of-service
- Privacy / DPA as published on bird.com/legal.
- Sender-ID / 10DLC / destination allowlist are workspace configuration. US A2P needs 10DLC or verified toll-free. Marketing requires lawful consent (TCPA, ePrivacy, local telecom).
- GDPR: destinations, bodies, metadata are personal data. `authentication` OTPs are redacted on later reads — still PII in flight.
- Official-API-first: regional `platform.bird.com` SMS API. **Do not** call `rest.messagebird.com` for new builds.

## Implementation notes

- JDK HTTP only. `Authorization: Bearer {api_key}`. JSON. Host from `region` only (no user-controlled host).
- Send success = **202**. Map to `status: ok` + `message_status: accepted`.
- Idempotency-Key on send retries after timeout.
- Timeouts: 30s. Do not retry non-idempotent send without the key.
- HTTP 4xx/5xx → `{ error, http_status }` on OUTPUT. Never throw past dispatch.
- Redact `bk_*` keys and message bodies from logs.
- Folder name stays `messagebird` (catalog key); product name in the node label is **Bird**.
