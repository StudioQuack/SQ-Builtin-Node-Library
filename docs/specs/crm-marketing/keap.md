# Keap

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/keap/`
- **Researched:** 2026-08-20
- **Official API:** Keap REST **v2** (resources) + REST **v1** (REST Hooks only)
- **Docs:** https://developer.infusionsoft.com/docs/restv2/ · OAuth: https://developer.keap.com/getting-started-oauth-keys/ · REST Hooks: https://developer.keap.com/rest-hook-documentation/
- **Auth:** oauth2 — secret bindings: `client_secret`, `refresh_token` (access token is daemon-held, never on INPUT)

## Scope

One node for Keap (Infusionsoft) CRM + email + ecommerce. The designer picks `operation`; the handler calls `https://api.infusionsoft.com/crm/rest/v2/…` with `Authorization: Bearer {access_token}`. REST Hooks (`trigger.listen`) remain on **v1** `POST /crm/rest/v1/hooks` (not present on the v2 resource list). Do not use XML-RPC. Do not ship a vendor SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.upsert` | POST | `/crm/rest/v2/contacts` | Create contact (email in `email_addresses[]`). Duplicate handling is Keap-side; prefer get-by-email then PATCH when the operator needs strict upsert. |
| `contact.get` | GET | `/crm/rest/v2/contacts/{contact_id}` | Fetch one contact |
| `contact.list` | GET | `/crm/rest/v2/contacts` | Page contacts (`page_size`, `page_token`, `filter`) |
| `contact.delete` | DELETE | `/crm/rest/v2/contacts/{contact_id}` | Delete contact |
| `company.create` | POST | `/crm/rest/v2/companies` | Create company |
| `company.list` | GET | `/crm/rest/v2/companies` | Page companies |
| `contactNote.create` | POST | `/crm/rest/v2/contacts/{contact_id}/notes` | Add note |
| `contactNote.get` | GET | `/crm/rest/v2/contacts/{contact_id}/notes/{note_id}` | Fetch one note |
| `contactNote.list` | GET | `/crm/rest/v2/contacts/{contact_id}/notes` | List notes |
| `contactNote.update` | PATCH | `/crm/rest/v2/contacts/{contact_id}/notes/{note_id}` | Update note |
| `contactNote.delete` | DELETE | `/crm/rest/v2/contacts/{contact_id}/notes/{note_id}` | Delete note |
| `contactTag.add` | POST | `/crm/rest/v2/tags/{tag_id}/contacts:applyTags` | Apply tag to contact ids |
| `contactTag.remove` | POST | `/crm/rest/v2/tags/{tag_id}/contacts:removeTags` | Remove tag from contact ids |
| `contactTag.list` | GET | `/crm/rest/v2/tags/{tag_id}/contacts` | Contacts that have the tag |
| `ecommerce.order.create` | POST | `/crm/rest/v2/orders` | Create order |
| `ecommerce.order.get` | GET | `/crm/rest/v2/orders/{order_id}` | Fetch order |
| `ecommerce.order.list` | GET | `/crm/rest/v2/orders` | Page orders |
| `ecommerce.order.delete` | DELETE | `/crm/rest/v2/orders/{order_id}` | Delete order |
| `ecommerce.product.create` | POST | `/crm/rest/v2/products` | Create product |
| `ecommerce.product.get` | GET | `/crm/rest/v2/products/{product_id}` | Fetch product |
| `ecommerce.product.list` | GET | `/crm/rest/v2/products` | Page products |
| `ecommerce.product.delete` | DELETE | `/crm/rest/v2/products/{product_id}` | Delete product |
| `email.send` | POST | `/crm/rest/v2/emails:send` | Send email to contacts (consent required) |
| `email.listSent` | GET | `/crm/rest/v2/emails` | List recorded emails |
| `email.record` | POST | `/crm/rest/v2/emails` | Record an email without sending |
| `file.upload` | POST | `/crm/rest/v2/files` | Multipart create file |
| `file.list` | GET | `/crm/rest/v2/files` | List files |
| `file.delete` | DELETE | `/crm/rest/v2/files/{file_id}` | Delete file |
| `trigger.listen` | POST | `/crm/rest/v1/hooks` | Subscribe REST Hook (`eventKey` + `hookUrl`) |

Base origin: `https://api.infusionsoft.com`. Token URL: `POST https://api.infusionsoft.com/token`. Authorize: `https://accounts.infusionsoft.com/app/oauth/authorize` (`response_type=code`, `scope=full`). Refresh tokens rotate — persist the new refresh token.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | OAuth app client id (developer portal) |
| `client_secret` | password | yes | empty | yes | OAuth client secret |
| `refresh_token` | password | yes | empty | yes | Rotating refresh token bound in the daemon secret store |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s. Cap 120 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. `file.upload` uses type `file` (ref-id). |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `given_name` | string | no | First name |
| `family_name` | string | no | Last name |
| `email` | string | yes | Maps to `email_addresses[{ email, field: "EMAIL1" }]` |
| `phone` | string | no | `phone_numbers[{ number, field: "PHONE1" }]` |
| `company_id` | string | no | Existing company id |
| `custom_fields` | object | no | Extra JSON fields Keap documents for Contact |

### `operation = contact.get` | `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Path id |

### `operation = contact.list` | `company.list` | `ecommerce.order.list` | `ecommerce.product.list` | `file.list` | `email.listSent`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page_size` | number | no | Default 50; clamp 1–1000 |
| `page_token` | string | no | Next-page token from prior response |
| `filter` | string | no | Provider filter expression when documented |

### `operation = company.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `company_name` | string | yes | Company name |
| `email` | string | no | Company email if supported on the payload |

### `operation = contactNote.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Parent contact |
| `title` | string | no | Note title |
| `body` | string | yes | Note body (PII possible — do not log) |

### `operation = contactNote.get` | `contactNote.update` | `contactNote.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Parent contact |
| `note_id` | string | yes | Note id |
| `title` | string | no | Update only |
| `body` | string | no | Update only |

### `operation = contactTag.add` | `contactTag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tag_id` | string | yes | Tag id |
| `contact_ids` | array | yes | Numeric/string ids; body `{ "contact_ids": […] }` |

### `operation = contactTag.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tag_id` | string | yes | Tag id |
| `page_size` | number | no | Pagination |
| `page_token` | string | no | Pagination |

### `operation = ecommerce.order.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Order contact |
| `order_date` | string | no | ISO date as documented |
| `order_items` | array | yes | Line items (product id, qty, price) per v2 Order schema |
| `order_title` | string | no | Title |

### `operation = ecommerce.order.get` | `ecommerce.order.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `order_id` | string | yes | Path id |

### `operation = ecommerce.product.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_name` | string | yes | Name |
| `product_price` | number | no | Price |
| `product_short_desc` | string | no | Description |

### `operation = ecommerce.product.get` | `ecommerce.product.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `product_id` | string | yes | Path id |

### `operation = email.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_ids` | array | yes | Recipients who opted in |
| `subject` | string | yes | Subject |
| `html_body` | string | yes | HTML body |
| `plain_text_body` | string | no | Text part |

### `operation = email.record`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | Contact |
| `subject` | string | no | Subject |
| `html_body` | string | no | Body to store |

### `operation = file.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform file ref-id (never inline bytes) |
| `file_name` | string | yes | Filename |
| `file_association` | string | yes | e.g. `CONTACT` |
| `contact_id` | string | no | Required when association is CONTACT |
| `is_public` | boolean | no | Default false |

### `operation = file.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file_id` | string | yes | Path id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `event_key` | string | yes | e.g. `contact.add` (list via `GET /crm/rest/v1/hooks/event_keys`) |
| `callback_url` | string | yes | StudioQuack message-event ingress URL (`hookUrl`) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get/send | Resource id (`contact_id`, `order_id`, hook `key`, …) |
| `resource` | object | 2xx | Provider JSON (capped). Do not flatten emails into logs. |
| `items` | array | list ops | Page of resources |
| `next_page_token` | string | list, more pages | Pass back as `page_token` |
| `file` | file | `file.upload` | Stored ref-id of uploaded bytes if the engine materializes a copy; otherwise provider file id in `id` |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 30 |

## Legal

- Developer portal: https://developer.keap.com/
- REST Hooks policy (4 attempts, 30 s receiver timeout, 410 deactivates): https://developer.keap.com/rest-hook-documentation/
- Contacts, notes, emails, files, and orders are PII. Customer owns the Keap account. Caller is responsible for CAN-SPAM / CASL / GDPR consent before `email.send`.
- Never log access tokens, refresh tokens, email addresses, or file bytes.
- Official-API-first: no XML-RPC for new work; no scrape of the Keap UI.

**UNVERIFIED:** published numeric REST rate-limit (not stated on the OAuth / REST Hook pages fetched 2026-08-20). Honor 429 anyway.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Refresh: `POST https://api.infusionsoft.com/token` (`grant_type=refresh_token`). Store the **new** refresh token. `Authorization: Bearer` on API calls. Empty secret → `{ error }` no HTTP.
- `file.upload`: `multipart/form-data`; resolve `file` ref-id to bytes in the sandbox; cap upload **10_000_000** bytes.
- Response body cap **1_000_000** bytes. Timeout default 30 s.
- REST Hook handshake: Keap POSTs `X-Hook-Secret` to `callback_url`; ingress must echo it with HTTP 200 (engine webhook path, not this handler’s outbound call).
- 429 → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Logs: operation, http_status, sizes, ids — never tokens or email bodies.
