# Brevo

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/brevo/`
- **Researched:** 2026-08-20
- **Official API:** Brevo API v3 (ex-Sendinblue)
- **Docs:** [Developers](https://developers.brevo.com/) · [Auth](https://developers.brevo.com/docs/authentication-schemes) · [API keys](https://developers.brevo.com/docs/api-key-authentication) · [Send transactional email](https://developers.brevo.com/reference/send-transac-email) · [Create contact](https://developers.brevo.com/reference/create-contact) · [Attributes](https://developers.brevo.com/reference/get-attributes) · [Senders](https://developers.brevo.com/reference/get-senders.mdx) · [Webhooks](https://developers.brevo.com/docs/how-to-use-webhooks) · [Secured webhooks](https://developers.brevo.com/docs/secured-webhooks)
- **Auth:** `api_key` — secret binding: `api_key` (header `api-key`)

## Scope

Calls **Brevo API v3** at `https://api.brevo.com/v3`. Transactional SMTP send (`POST /smtp/email`) plus Contacts, contact Attributes, and Senders. `listen` is **transactional** webhooks (delivered, hardBounce, softBounce, spam, blocked, invalid, error, …), same node. OAuth 2.0 exists for partner apps; this node uses **API key only**.

**Do not build:** Marketing campaign send / A/B; SMS / WhatsApp; inbound parse; OAuth user-delegated flow; unofficial `app.sendinblue.com` scrape. Host aliases `api.sendinblue.com` are legacy — use `api.brevo.com`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/v3/smtp/email` | HTML/text transactional send |
| `send-template` | POST | `/v3/smtp/email` | Same endpoint with `templateId` + `params` |
| `create-contact` | POST | `/v3/contacts` | `updateEnabled` false (default). 4xx on identifier conflict unless `force_merge`. |
| `upsert-contact` | POST | `/v3/contacts` | `updateEnabled: true` |
| `get-contact` | GET | `/v3/contacts/{identifier}` | Email or contact id (`identifierType`) |
| `list-contacts` | GET | `/v3/contacts` | Page (`limit`, `offset`, `modifiedSince`) |
| `update-contact` | PUT | `/v3/contacts/{identifier}` | Patch attributes / lists |
| `delete-contact` | DELETE | `/v3/contacts/{identifier}` | Erase contact |
| `create-attribute` | POST | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | Custom attribute |
| `list-attributes` | GET | `/v3/contacts/attributes` | All attributes |
| `update-attribute` | PUT | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | Rename enum values etc. |
| `delete-attribute` | DELETE | `/v3/contacts/attributes/{attributeCategory}/{attributeName}` | |
| `create-sender` | POST | `/v3/senders` | New From identity (verification email) |
| `list-senders` | GET | `/v3/senders` | |
| `delete-sender` | DELETE | `/v3/senders/{id}` | |
| `listen` | POST (inbound) | `/v3/webhooks` register + inbound | Transactional events. Auth header / Basic — **no HMAC**. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `api_key` | password | yes | — | **yes** | From SMTP & API → API keys (`xkeysib-…`). Header `api-key`. **One key object** in current docs; restrict by IP allowlist in the dashboard. Do not use a marketing-automation-only key if the account still distinguishes keys — transactional send and contacts both need a key that is allowed to call those routes. |
| `webhook_auth_token` | password | `listen` | — | **yes** | Bearer token configured on the Brevo webhook (`auth.type=bearer`) or a shared secret in the path; **required** because Brevo does not sign payloads. |
| `register_webhook` | boolean | no | `false` | no | POST `/v3/webhooks` with `type=transactional` |
| `webhook_url` | string | when registering | — | no | HTTPS ingress |
| `timeout_seconds` | number | no | `25` | no | 0 / negative / blank → 25 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never the API key |

FUNCTION ports: none. Attachments: `file` ref-ids → Brevo `attachment` `{ url }` is **not** used (SSRF). Encode as `{ name, content }` Base64 from the ref. Never inline bytes on the port.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sender` | `{email, name?, id?}` | yes | Verified sender |
| `to` | array of `{email, name?}` | yes | Recipients |
| `cc` / `bcc` | array | no | |
| `reply_to` | `{email, name?}` | no | |
| `subject` | string | yes | Subject |
| `html_content` / `text_content` | string | one of | Body |
| `params` | object | no | Template-style variables in HTML |
| `tags` | array of string | no | Correlation |
| `attachments` | array of `{file, filename}` | no | `file` = ref-id |
| `headers` | object | no | Custom headers (not a Gmail thread API) |

### `operation = send-template`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_id` | number | yes | Stored template |
| `to` | array of `{email, name?}` | yes | |
| `params` | object | no | Merge params (≤ 100 KB per version) |
| `sender` | object | no | Override template sender |

### Contacts

| Field | Type | Required | Operations | Purpose |
|---|---|---|---|---|
| `email` | string | create/upsert unless ext_id/SMS | | |
| `ext_id` | string | optional | | External id |
| `attributes` | object | no | | **UPPERCASE** names already defined |
| `list_ids` | array of number | no | | Add to lists |
| `update_enabled` | — | — | upsert forces true; create false | |
| `force_merge` | boolean | no | create | Merge on identifier clash |
| `identifier` | string | get/update/delete | | Email or id |
| `identifier_type` | string | no | get | `email_id` default |
| `limit` / `offset` | number | list | | Max 50 typical page |

### Attributes

| Field | Type | Required | Purpose |
|---|---|---|---|
| `attribute_category` | string | create/update/delete | `normal`, `transactional`, `category`, `calculated`, `global` |
| `attribute_name` | string | create/update/delete | |
| `type` | string | create | `text`, `date`, `float`, `boolean`, `id`, … |
| `enumeration` | array | category type | `{ value, label }` |

### Senders

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` / `email` | string | create | From identity |
| `sender_id` | number | delete | |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `events` | array of string | no | Default: `delivered`, `hardBounce`, `softBounce`, `spam`, `blocked`, `invalid` |
| `payload` | object | inbound | `{ event, email, "message-id", … }` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `message_id` | string | send | Transactional message id |
| `contact_id` | number | create/upsert | |
| `contact` / `contacts` | object / array | get/list | PII |
| `count` | number | list-contacts | |
| `attributes` | array | list-attributes | |
| `senders` | array | list-senders | |
| `event` | object | listen | Normalized event |
| `error` | string | failure | Never keys |

## Legal

- [Brevo Terms](https://www.brevo.com/legal/termsofuse/) · [Privacy](https://www.brevo.com/legal/privacypolicy/) · [DPA / GDPR](https://www.brevo.com/legal/termsofuse/#data-processing-agreement) (operator must execute a DPA for EEA personal data)
- Contacts are personal data. `delete-contact` is the erasure path. Do not store extra copies of attribute dumps in engine variables (REF-only).
- Marketing vs transactional: campaign mail and SMTP transactional have different complaint profiles. This node’s `listen` is `type=transactional` only. Do not register marketing webhooks here.
- CAN-SPAM / ePrivacy: marketing lists need consent; transactional send is not a consent bypass.
- Official-API-first: JDK HTTP to `api.brevo.com`.

## Implementation notes

- JDK HTTP only. Header `api-key`, `Accept: application/json`, `Content-Type: application/json`.
- Send attachments: read file refs, Base64 `content`, never fetch operator-supplied URLs (SSRF).
- Webhooks: **unsigned**. Require `webhook_auth_token` (Bearer `auth` on create-webhook, or query/path secret). Cap 40 webhooks per account (marketing + transactional).
- Retry 429 (contacts often 10–60 RPS) and 5xx. 425 Too Early on contacts → surface `{ error }` with retry-after if present.
- Never throw past dispatch.

### Example

Send success: `{ "status": "ok", "message_id": "<…@domain.com>" }`.
