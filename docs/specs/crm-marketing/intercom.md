# Intercom

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md` (CRM, **not** Communication)
- **Folder when built:** `nodes/intercom/`
- **Researched:** 2026-08-20
- **Official API:** Intercom REST. Pin header `Intercom-Version: 2.16` (enum on current Contacts reference includes through **2.16**).
- **Docs:** https://developers.intercom.com/docs/build-an-integration/learn-more/rest-apis · Contacts https://developers.intercom.com/docs/references/rest-api/api.intercom.io/contacts
- **Auth:** `Authorization: Bearer` access token (private app) or OAuth access token — secret binding: `access_token`. OAuth client secrets: `client_id`, `client_secret`.

## Scope

Workspace CRM + Inbox: contacts (unified **user** and **lead** `role`), companies, conversations, messages, tags. Legacy `/users` and `/leads` collections are retired; do not call them.

**Region:** config `region` is the API host without scheme (`baseUrl` `https://{region}`):

| `region` | Base |
|---|---|
| `api.intercom.io` | `https://api.intercom.io` |
| `api.eu.intercom.io` | `https://api.eu.intercom.io` |
| `api.au.intercom.io` | `https://api.au.intercom.io` |

Webhooks: subscribe in Developer Hub (HTTPS URL, HEAD probe). **REST subscribe is retired** — `trigger.listen` documents the ingress URL and verifies `X-Hub-Signature` (HMAC-SHA1 of body with the app’s client secret, as Intercom documents).

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.create` | POST | `/contacts` | Create user or lead (`role`) |
| `contact.get` | GET | `/contacts/{id}` | By Intercom id |
| `contact.list` | GET | `/contacts` | Cursor `starting_after`, `per_page` |
| `contact.update` | PUT | `/contacts/{id}` | |
| `contact.delete` | DELETE | `/contacts/{id}` | Archive/delete per current version semantics |
| `company.create` | POST | `/companies` | |
| `company.get` | GET | `/companies/{id}` | |
| `company.list` | GET | `/companies` | |
| `company.update` | PUT | `/companies/{id}` | |
| `company.listUsers` | GET | `/companies/{id}/contacts` | Contacts attached to company |
| `conversation.create` | POST | `/conversations` | Start conversation |
| `conversation.reply` | POST | `/conversations/{id}/reply` | Admin/user reply |
| `conversation.list` | GET | `/conversations` | |
| `message.send` | POST | `/messages` | Outbound message / in-app as documented |
| `tag.add` | POST | `/contacts/{id}/tags` | Attach tag (`id` or name per version) |
| `trigger.listen` | n/a | Developer Hub webhooks | Verify + emit payload |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | |
| `access_token` | password | yes | empty | yes | Bearer |
| `client_secret` | password | yes for webhook HMAC | empty | yes | `X-Hub-Signature` |
| `region` | select | yes | `api.intercom.io` | no | Host without scheme (`https://{region}`) |
| `api_version` | string | no | `2.16` | no | `Intercom-Version` |
| `timeout_seconds` | number | no | `25` | no | Webhooks must be answered in ~5 s on ingress |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Contact/conversation bodies are PII |
| `output` | OUTPUT | object or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `contact.create` / `contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update yes | Contact id |
| `role` | string | create no | `user` \| `lead` |
| `email` | string | no | |
| `external_id` | string | no | |
| `name` | string | no | |
| `phone` | string | no | |
| `custom_attributes` | object | no | |
| `unsubscribed_from_emails` | boolean | no | |

Create requires at least one identifier Intercom accepts (typically `email` or `external_id`).

### `contact.get` / `contact.delete` / `tag.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Contact id |
| `tag_id` | string | `tag.add` yes | Tag to attach |

### `contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `per_page` | number | no | |
| `starting_after` | string | no | Cursor |

### `company.create` / `company.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update yes | |
| `company_id` | string | create often | Your company id |
| `name` | string | no | |
| `custom_attributes` | object | no | |

### `company.get` / `company.listUsers`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Company id |

### `company.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` / `per_page` | number | no | As documented for companies list |

### `conversation.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | object | yes | `{ type, id }` contact |
| `body` | string | yes | |

### `conversation.reply`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversation_id` | string | yes | |
| `type` | string | yes | e.g. `admin` |
| `admin_id` | string | when admin | |
| `body` | string | yes | |
| `message_type` | string | no | `comment` / `note` / `open` as documented |

### `conversation.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `per_page` | number | no | |
| `starting_after` | string | no | |

### `message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `message_type` | string | yes | e.g. `inapp` / `email` |
| `subject` | string | email | |
| `body` | string | yes | |
| `from` | object | yes | Admin |
| `to` | object | yes | Contact |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | yes | Notification JSON (`type=notification_event`, `topic`, `data.item`) |
| `signature` | string | yes | `X-Hub-Signature` |

## Output fields (by operation)

### Contact / company / conversation / message / tag

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `resource` | object | 2xx | Provider object (`type`, `id`, …) |
| `error` | string | failure | Flatten `errors[].message` from `error.list` |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 (`X-RateLimit-Reset`) | |

### List ops

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | array | 2xx | `data` array |
| `pages` | object | 2xx | Cursor pagination |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | HMAC ok | `ok` |
| `topic` | string | | e.g. `conversation.user.created` |
| `item` | object | | `data.item` |
| `error` | string | bad signature | |

## Legal

- REST overview (regions): https://developers.intercom.com/docs/build-an-integration/learn-more/rest-apis
- Version header: https://developers.intercom.com/docs/build-an-integration/learn-more/rest-apis/update-your-api-version
- Webhooks: https://developers.intercom.com/docs/webhooks
- Terms: https://www.intercom.com/legal/terms-and-policies
- Conversations and contacts are PII. Customer owns the workspace. Never log Bearer tokens or message bodies.
- Rate limiting is documented on Intercom “Rate Limiting”; honor `X-RateLimit-*`.

## Implementation notes

- Headers: `Authorization: Bearer`, `Accept: application/json`, `Intercom-Version`. JDK HTTP.
- Timeout 25 s. Response cap 2 MiB. TLS verified. `Redirect.NEVER`.
- Search (`POST /contacts/search`) is **not** a catalog verb; do not add unless catalog gains `contact.search`.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry.
