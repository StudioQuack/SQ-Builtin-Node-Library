# PandaDoc

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/pandadoc/`
- **Researched:** 2026-08-20
- **Official API:** PandaDoc Public API, path prefix **`/public/v1`**. OpenAPI `info.version` **8.11.1** (docs page updated 2026-07-07). Server: `https://api.pandadoc.com`.
- **Docs:** https://developers.pandadoc.com/
- **Auth:** API-Key header via `api_key`. OAuth app credentials are not used at runtime.

## Scope

One node for the customer’s PandaDoc workspace. **Templates live on this node** (`template_list` plus create-from-template) — there is no second “PandaDoc Templates” node.

The workflow operator is the **e-sign sender**. StudioQuack is not the sender and does not accept PandaDoc terms for the operator.

Create-from-file streams a platform `file` ref as multipart to `POST /public/v1/documents?upload`. Template ids are strings. Document bytes on input/output are always type `file`.

**Do not build:** unofficial scrape, a separate template node, or vendor SDK usage.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `document_create_from_template` | POST | `/public/v1/documents` | JSON body with `template_uuid` (string id), recipients, tokens/fields. |
| `document_create_from_file` | POST | `/public/v1/documents?upload` | Multipart upload of PDF/DOCX/RTF. **Input `file`.** |
| `document_send` | POST | `/public/v1/documents/{id}/send` | Send a document in `document.draft` (wait out `document.uploaded`). |
| `document_get` | GET | `/public/v1/documents/{id}` | Details / status. |
| `document_list` | GET | `/public/v1/documents` | List/search (query params). |
| `document_download` | GET | `/public/v1/documents/{id}/download` | PDF bytes → **OUTPUT `file`**. Completed/signed docs: official **Download Protected Document** endpoint when the plain download forbids it. |
| `document_delete` | DELETE | `/public/v1/documents/{id}` | Delete by id. |
| `template_list` | GET | `/public/v1/templates` | List templates in **this** node. |
| `session_create` | POST | `/public/v1/documents/{id}/session` | Embedded signing session URL for a recipient. |
| `listen` | POST | `/public/v1/webhook-subscriptions` | Official webhook subscription to StudioQuack ingress. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `document_create_from_template` | no | Verb in the table above. |
| `api_key` | password | yes | | **yes** | `Authorization: API-Key {key}`. Sandbox vs production keys are different products. |
| `webhook_shared_key` | password | if listen | | **yes** | HMAC-SHA256 shared key for ingress verification. |
| `timeout_seconds` | number | no | `60` | no | JDK connect+read timeout. |

OAuth authorize URL (operator consent, not this node): `https://app.pandadoc.com/oauth2/authorize` (scopes `read+write` / `read` per official docs).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `file` on create-from-file. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | `document_download` returns `file`. No secrets. |

FUNCTION ports: **none**.

## Input fields (by operation)

### `operation = document_create_from_template`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_uuid` | string | yes | PandaDoc template id. Not a file. |
| `name` | string | no | Document name. |
| `recipients` | object[] | yes | `{ email, first_name, last_name, role, … }`. |
| `tokens` | object | no | Template tokens (key/value). |
| `fields` | object | no | Field prefill. |
| `metadata` | object | no | Caller metadata (no secrets). |

### `operation = document_create_from_file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | **file** | yes | Platform ref-id of PDF/DOCX/RTF. Resolve at dispatch; stream multipart. Never store base64. |
| `name` | string | no | Document name. |
| `recipients` | object[] | yes | Same shape as create-from-template. |
| `parse_form_fields` | boolean | no | Parse PDF form fields when supported. |
| `fields` | object | no | Prefill after parse. |

### `operation = document_send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Must be `document.draft` (not `document.uploaded`). |
| `subject` | string | no | Email subject. |
| `message` | string | no | Email body. |
| `silent` | boolean | no | If true, no recipient email/SMS. |

### `operation = document_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Document id. |

### `operation = document_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `count` | number | no | Page size (honor provider max). |
| `page` | number | no | Page index as documented. |
| `status` | string | no | Filter. |
| `q` | string | no | Search query. |
| `tag` | string | no | Tag filter when documented. |

### `operation = document_download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Document id. Prefer webhooks over polling for `document.completed`. |
| `protected` | boolean | no | If true (or provider requires it for completed docs), call the official protected-download path instead of `/download`. |

### `operation = document_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Document to delete. |

### `operation = template_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `count` | number | no | Page size. |
| `page` | number | no | Page index. |
| `q` | string | no | Search. |
| `tag` | string | no | Tag filter. |

### `operation = session_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `document_id` | string | yes | Document in a session-eligible state (`document.sent` typical). |
| `recipient` | string | yes | Recipient email or id as required by the session API. |
| `lifetime` | number | no | Session TTL seconds if the API accepts it. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | HTTPS StudioQuack message-event ingress. |
| `triggers` | string[] | no | e.g. document state-changed events from https://developers.pandadoc.com/docs/webhook-events |
| `name` | string | no | Subscription name. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` or provider document status (`document.draft`, `document.sent`, …). |
| `document_id` | string | create / send / get / delete | Provider id. |
| `document` | object | get / create | Summary JSON (no file bytes). |
| `documents` | object[] | list | List page. |
| `templates` | object[] | template_list | Template ids and names. |
| `file` | **file** | document_download | Stored PDF ref-id. |
| `filename` | string | document_download | Suggested filename. |
| `session_id` | string | session_create | Session id. |
| `session_url` | string | session_create | Embedded sign URL (not a secret in the DocuSign sense, but do not log if it embeds tokens). |
| `expires_at` | string | session_create | Expiry. |
| `subscription_id` | string | listen | Webhook subscription uuid. |
| `error` | string | failure | Non-secret error. |

## Legal

- OpenAPI `termsOfService`: https://www.pandadoc.com/master-services-agreement/
- Product terms (confirm live URL): https://www.pandadoc.com/terms-of-service/
- API usage / plan: production API typically Enterprise; sandbox keys on trial/Business with Dev Center. Each API-created document may consume a usage credit (https://support.pandadoc.com/en/articles/9714958-pandadoc-api).
- **Customer is the sender.** The authenticated PandaDoc user/workspace sends the document. StudioQuack is not a party to the e-sign transaction.
- **ESIGN / eIDAS:** operator + PandaDoc; this node is transport only.
- **PHI / DPA:** files and recipient PII go to PandaDoc. Require the customer’s BAA (https://www.pandadoc.com/hipaa/) and DPA before PHI. StudioQuack is not PandaDoc’s BA for the customer.
- **Demo vs prod:** sandbox API key is capped and must not be used as production. Sandbox recipient rules (same org domain on trial) apply.
- **Rate limits** (per user, 60s sliding window, production key/OAuth) — https://developers.pandadoc.com/reference/limits.md :

  | Action | RPM |
  |---|---:|
  | Create from PDF (document) | 300 |
  | Create from template (document) | 500 |
  | Send | 400 |
  | Document details | 600 |
  | Template details | 300 |
  | List / status / delete | 2000 |
  | Download | 100 |
  | Download protected | 300 |
  | **Any sandbox endpoint** | **10** |

  HTTP **429** when exceeded. Max JSON body **2 MB**. Upload PDF max **50 MB** (413 if larger).
- Official-API-first: no scrape.

## Implementation notes

- JDK `HttpClient` only. No PandaDoc SDK.
- Auth header: `API-Key {key}` or `Bearer {access_token}`.
- Create-from-file: `multipart/form-data`; resolve `file` ref to a stream. After create, status is often `document.uploaded` for 3–5s — `document_send` must wait for `document.draft` (webhook `listen` or bounded GET, not a tight poll loop).
- `document_download`: store response bytes in the platform file store; emit `file` ref-id. If completed docs require protected download, follow https://developers.pandadoc.com/reference/download-protected-document.md — still type `file` out.
- Retry: 429 with backoff per action RPM; do not retry DELETE/send without idempotency.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Never log API keys or PDF bytes.
- `listen`: POST subscription only. Verify HMAC-SHA256 (`X-PandaDoc-Signature` / shared key) and dedupe `X-PandaDoc-Webhook-Event-Id` on **ingress**, not via a FUNCTION port. Guides: https://developers.pandadoc.com/docs/webhook-setup.md
