# DocuSign

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/docusign/`
- **Researched:** 2026-08-20
- **Official API:** DocuSign eSignature REST API **v2.1** (current major). Product monthly line **26.2.x** as of 2026-08 (Demo **26.2.02.00**, August 2026).
- **Docs:** https://developers.docusign.com/docs/esign-rest-api/
- **What’s new in v2.1:** https://developers.docusign.com/docs/esign-rest-api/esign101/new-v21/
- **Auth:** `bearer` via `access_token` minted outside StudioQuack. Paths use `/accounts/{account_id}/…`.

## Scope

One node for the customer’s DocuSign eSignature account. The workflow operator is the **e-sign sender** (ESIGN / eIDAS). StudioQuack is not the sender, is not a DocuSign ISV on the customer’s behalf, and does not accept DocuSign terms for the operator.

Calls the official REST surface under `/restapi/v2.1/accounts/{account_id}/…`. OfficialApiNode does not mint OAuth tokens — bind an access token minted outside StudioQuack. Prefer Connect webhooks over envelope polling.

**Do not build:** SOAP, eSignature REST v2, HTML scrape of the DocuSign web app, or any unofficial wrapper.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `envelope_create_from_template` | POST | `/restapi/v2.1/accounts/{account_id}/envelopes` | Create an envelope from a **template id** (`templateId` + `templateRoles`). Default `status=created` (draft). |
| `envelope_send` | PUT | `/restapi/v2.1/accounts/{account_id}/envelopes/{envelopeId}` | Send a draft: body `{ "status": "sent" }`. |
| `envelope_get` | GET | `/restapi/v2.1/accounts/{account_id}/envelopes/{envelopeId}` | Envelope status / summary. Do not poll faster than Connect + resource-limit rules allow. |
| `envelope_void` | PUT | `/restapi/v2.1/accounts/{account_id}/envelopes/{envelopeId}` | Void in-flight: `{ "status": "voided", "voidedReason": "…" }` (`voidedReason` required). |
| `document_download` | GET | `/restapi/v2.1/accounts/{account_id}/envelopes/{envelopeId}/documents/{documentId}` | Download PDF/ZIP. `documentId` may be a document id or keyword `combined` / `archive` / `certificate` / `portfolio`. **OUTPUT `file`.** |
| `recipient_list` | GET | `/restapi/v2.1/accounts/{account_id}/envelopes/{envelopeId}/recipients` | Recipients and routing status. |
| `bulk_send` | POST | `/restapi/v2.1/accounts/{account_id}/bulk_send_lists` then POST `/restapi/v2.1/accounts/{account_id}/bulk_send_lists/{bulkSendListId}/send` | Create a bulk-send list and send against a sendable envelope or template. Optional POST `…/test` first. |
| `listen` | POST | `/restapi/v2.1/accounts/{account_id}/connect` | Register **DocuSign Connect** (official webhooks) to the StudioQuack message-event ingress URL. Envelope-scoped Connect may also be set via `eventNotification` on create — still this operation for account-level config. |

Caller-supplied **template ids are strings**. PDF/DOCX bytes never live on ports or config as base64 — only type `file` (platform ref-id).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `envelope_create_from_template` | no | Verb in the table above. |
| `access_token` | password | yes | | **yes** | OAuth access token minted outside StudioQuack. Bearer. Never on INPUT. |
| `account_id` | string | yes | | no | Account GUID in REST paths `/accounts/{account_id}/…`. INPUT `account_id` overrides. |
| `timeout_seconds` | number | no | `60` | no | Connect + read timeout for JDK `HttpClient`. Downloads may use a higher cap (see notes). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | One INPUT. No secrets on this port. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens, PEM, or file bytes. `document_download` includes `file` (ref-id). |

FUNCTION ports: **none**.

## Input fields (by operation)

### `operation = envelope_create_from_template`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | string | yes | DocuSign template GUID. Not a file. |
| `email_subject` | string | no | Overrides template subject. |
| `email_blurb` | string | no | Email body. |
| `status` | string | no | `created` (default) or `sent` if the caller wants create+send in one POST. Prefer `envelope_send` for the catalog split. |
| `template_roles` | object[] | yes | `{ email, name, roleName, … }`. Do **not** send `recipients` together with `templateId` (API rejects it). |
| `event_notification` | object | no | Optional envelope-level Connect. Account-level still uses `listen`. |

### `operation = envelope_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_id` | string | yes | Draft envelope to send. |

### `operation = envelope_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_id` | string | yes | Envelope GUID. |

### `operation = envelope_void`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_id` | string | yes | Envelope to void. |
| `voided_reason` | string | yes | Required by the API; emailed to recipients. |

### `operation = document_download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_id` | string | yes | Envelope GUID. |
| `document_id` | string | no | Default `combined`. Or a document id / `archive` / `certificate` / `portfolio`. |
| `certificate` | boolean | no | When `document_id=combined`, include the certificate of completion. |

### `operation = recipient_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_id` | string | yes | Envelope GUID. |

### `operation = bulk_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `envelope_or_template_id` | string | yes | Sendable envelope id or template id for `…/send`. |
| `bulk_copies` | object[] | yes | Recipients / custom fields per copy (bulk send list body). |
| `test_first` | boolean | no | If true, POST `…/test` and abort send when `canBeSent` is not true. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | **HTTPS** StudioQuack message-event ingress URL (not a node-hosted server). |
| `envelope_events` | string[] | no | e.g. `Sent`, `Delivered`, `Completed`, `Declined`, `Voided`. |
| `event_data_version` | string | no | Use Connect JSON / `restv2.1` as documented for Connect 2.0. |
| `name` | string | no | Connect configuration name. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | HTTP-mapped result (`ok`) or provider envelope status when relevant. |
| `envelope_id` | string | create / send / void / bulk | Envelope or batch identifier. |
| `envelope` | object | get | Provider envelope summary (ids, status, timestamps — no document bytes). |
| `recipients` | object | recipient_list | Recipient list JSON. |
| `bulk_send_list_id` | string | bulk_send | List id from create. |
| `batch_id` | string | bulk_send | Id from `…/send` when present. |
| `file` | **file** | document_download | Platform ref-id of the stored PDF/ZIP. **Never** base64 on the port. |
| `filename` | string | document_download | Suggested name (`envelope-{id}.pdf`). |
| `content_type` | string | document_download | `application/pdf` or `application/zip`. |
| `connect_id` | string | listen | Connect configuration id. |
| `error` | string | failure | Actionable, non-secret message. |

## Legal

- Developer terms: https://www.docusign.com/legal/terms-and-conditions/developers
- eSignature / MSA: operator’s DocuSign customer agreement (https://www.docusign.com/legal/terms-and-conditions).
- **Customer is the sender.** Envelope “from” identity is the OAuth user (JWT impersonation or auth-code user). StudioQuack must not be named as sender in product copy or envelope metadata.
- **ESIGN / eIDAS:** legal effect is between the customer, their signers, and DocuSign. This node only transports API calls.
- **PHI / DPA:** documents and recipient PII leave the daemon to DocuSign. Do not send PHI without the customer’s BAA/DPA with DocuSign and a lawful basis. StudioQuack is not the HIPAA covered entity or DocuSign BA for the customer’s envelopes.
- **Demo vs prod:** `account-d.docusign.com` + `demo.docusign.net` are non-production. Production IK requires Go-Live. Do not mix demo tokens with production `base_uri`.
- **Rate limits** (published): account hourly cap (`X-RateLimit-Limit`, commonly 3,000/hour); 30-second burst (200 demo / 500 prod); per-envelope GET/PUT hourly caps; **one GET envelope status per unique envelope per 15 minutes** (violations fail Go-Live). Prefer Connect. See https://developers.docusign.com/platform/resource-limits/
- Official-API-first: no scrape, no web-app automation.

## Implementation notes

- JDK `HttpClient` only. No DocuSign Java SDK.
- JWT: `POST {oauth_base}/oauth/token` with `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`. Access token TTL ~1 hour; no refresh token — mint a new JWT. Then `GET {oauth_base}/oauth/userinfo` for `base_uri`.
- Auth-code: standard confidential grant + refresh; same userinfo step.
- Authorization: `Bearer {access_token}` on REST calls.
- Timeouts: default 60s; `document_download` allow up to 120s read; cap download buffer (e.g. 50 MiB) then fail `{ error }`.
- Retry: 429 / 5xx with jitter; honor rate-limit headers. **Do not** retry void/send blindly (non-idempotent).
- Errors: map 4xx/5xx to `{ error }` on OUTPUT. Never throw past dispatch. Never echo `Authorization` or PEM.
- `listen` only **registers** Connect. Ingress verification (HMAC / OAuth for Connect) is handled by the StudioQuack engine's message-event ingress, not a FUNCTION port.
- Logs: envelope ids, HTTP status, byte sizes — never document bytes or recipient PII values.
