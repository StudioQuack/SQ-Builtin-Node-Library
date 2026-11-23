# Vonage

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/vonage/`
- **Researched:** 2026-08-20
- **Official API:** Vonage **Messages API v1.0** (`POST https://{api-region}.nexmo.com/v1/messages`)
- **Docs:** https://developer.vonage.com/en/messages/overview · https://developer.vonage.com/en/messages/technical-details · https://developer.vonage.com/en/api/messages · https://developer.vonage.com/en/messages/guides/sms-migration-to-messages · https://developer.vonage.com/en/messages/concepts/messages-status-callbacks
- **Auth:** `jwt` (Vonage Application ID + PKCS#8 private key, **preferred**) or `basic` (`api_key` + `api_secret`) — secret binding: `private_key` / `api_secret`. JWT is required for application-level inbound/status webhooks.

## Scope

Sends SMS (and, if extended later, other Messages channels) through the **current official Messages API**, not the legacy SMS API. Vonage documents Messages API as the path for all new messaging integrations; SMS API (`POST https://rest.nexmo.com/sms/json`) is **legacy** and is **not** what this node calls.

One endpoint: `POST https://api.nexmo.com/v1/messages` with JSON `{ channel: "sms", message_type: "text", to, from, text }`. Regions: `api` (default), `api-eu`, `api-us`, `api-ap` via `api_region`.

`listen` is Messages inbound + status webhooks on the Vonage Application (JWT). Verify HS256 JWT (`Authorization: Bearer`) with `VONAGE_API_SIGNATURE_SECRET` and `payload_hash` = SHA-256 of the raw body. Do **not** apply legacy SMS `sig` verification to Messages webhooks.

**Do not build:** legacy SMS API; Messages WhatsApp/RCS/Messenger/Viber/Email as first-class verbs in v1 (same endpoint, omitted); Verify; Voice; Number Insight; Dispatch API.

**Dropped vs catalog seed:** `get-message`. Messages API v1 has **no GET-by-UUID**. Status is webhook-only (`listen`) plus dashboard/Reports. Do not call Reports API from this node in v1.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-sms` | POST | `https://{api-region}.nexmo.com/v1/messages` | Outbound SMS (`channel=sms`, `message_type=text`). |
| `listen` | inbound | Application inbound + status webhook URLs | Delivery status + inbound SMS. Operation on **this** node. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-sms` | no | Chooses the row above. |
| `auth_type` | select | yes | `jwt` | no | `jwt` (production) \| `basic` (sandbox / simple send; **does not** deliver to application webhook URLs). |
| `application_id` | string | if `jwt` | — | no | Vonage Application UUID. JWT claim `application_id`. |
| `private_key` | password | if `jwt` | — | **yes** | PKCS#8 PEM downloaded once from the Application. RS256. Never logged. |
| `api_key` | password | if `basic` | — | **yes** | Account API key (Basic username). |
| `api_secret` | password | if `basic` | — | **yes** | Account API secret. Basic auth. |
| `signature_secret` | password | `listen` | — | **yes** | Messages webhook JWT HS256 secret (`VONAGE_API_SIGNATURE_SECRET`). Distinct from API secret. |
| `from` | string | no | — | no | Default sender: owned number or alphanumeric sender ID (country rules). No leading `+`/`00` in Vonage examples — digits with country code, e.g. `447700900000`, or alpha ID. |
| `api_region` | select | no | `api` | no | `api` \| `api-eu` \| `api-us` \| `api-ap`. Host `{api-region}.nexmo.com`. |
| `timeout_seconds` | number | no | `30` | no | Connect+read timeout. 0/negative → 30. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include private keys or API secrets. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = send-sms`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `to` | string | yes | Destination digits with country code, min 7 / max 15 (E.164 without `+` per Vonage examples). |
| `from` | string | if config blank | Sender ID / number. |
| `text` | string | yes | Body. Auto unicode vs GSM. Cap 1000 characters on Messages SMS `text` (docs). Concatenation as Vonage documents. |
| `client_ref` | string | no | Echoed on status webhooks (`client_ref`). |
| `sms_encoding_type` | string | no | Optional `text` \| `unicode` under `sms.encoding_type`. Default: auto-detect. |

US 10DLC: +1 10-digit long codes into US networks require brand/campaign registration ([10 DLC](https://developer.vonage.com/en/messages/technical-details)).

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `start` (document/return Application webhook contract) \| `event` (inbound already at StudioQuack ingress). |
| `event_kind` | string | `event` | `status` \| `inbound`. |

Status payload includes `message_uuid`, `status` (`submitted`/`delivered`/`rejected`/`undeliverable`/`read`), `channel`, `usage.price`. Inbound includes `message_uuid`, `from`, `to`, `text`. Verify JWT + `payload_hash`. HTTP 204/200 quickly.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok`. |
| `error` | string | failure | Validation or `Vonage Messages API Error: HTTP {code}`. Never private key / JWT / secrets. |
| `http_status` | number | HTTP error | Vendor status (`429` rate limit). |
| `message_uuid` | string | send-sms | UUID from `202` body. |
| `event` | object | listen `event` | `{ event_kind, message_uuid, message_status, from, to, text, client_ref }` (text is PII). |
| `signature_valid` | boolean | listen `event` | JWT + hash check. Fail closed. |

## Legal

- Vonage API terms (Communications APIs): confirm live URL from https://developer.vonage.com (product ToS; historically https://www.vonage.com/legal/ ).
- Acceptable Use / messaging compliance as published in the developer portal.
- Rate limit: **75 requests/second per API key** default ([technical-details](https://developer.vonage.com/en/messages/technical-details)). Channel/network limits may still reject after HTTP 202 via status `rejected`.
- US A2P 10DLC, TCPA, GDPR: numbers and message bodies are personal data. Consent/STOP handling is the operator’s duty. StudioQuack does not collect consent.
- Official-API-first: `*.nexmo.com` Messages API only. No Dashboard scrape. No legacy SMS API.

## Implementation notes

- JDK HTTP only. **JWT (RS256):** compact JWS with claims `application_id`, `iat`, `exp` (≤ 15 min recommended, max 24h), `jti`. Sign with PKCS#8 from `private_key`. Header `Authorization: Bearer {jwt}`. HS256 tokens are rejected by Vonage for this API.
- **Basic:** `Authorization: Basic base64(api_key:api_secret)`.
- `Content-Type: application/json`. Success send: **HTTP 202** + `{ message_uuid }`. Treat 202 as success `ok`.
- Timeouts: 30s default. Retry GET-less API: **do not retry POST send** (duplicate SMS). Retry only on transport failure before a response, if the operator later adds idempotency — Messages send is not idempotent in v1.
- 429 → `{ error, http_status: 429 }`. Do not tight-loop.
- Webhook: parse `Authorization: Bearer`, verify HS256 with `signature_secret`, compare SHA-256 hex of raw body to `payload_hash`. Do not use SMS API `sig`/`nonce` logic.
- HTTP 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
- Redact private keys, JWTs, API secrets, and message text from logs.
