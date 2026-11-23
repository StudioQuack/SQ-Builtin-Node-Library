# ActiveCampaign

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/activecampaign/`
- **Researched:** 2026-08-20
- **Official API:** ActiveCampaign REST API **v3** (path `/api/3`). No version header; behaviour changes ship via the developer changelog. Create-contact reference last updated **2026-08-15**. Legacy v1 remains available but is **not** used by this node.
- **Docs:** https://developers.activecampaign.com/reference · Auth: https://developers.activecampaign.com/reference/authentication · Base URL: https://developers.activecampaign.com/reference/url · Rate limits: https://developers.activecampaign.com/reference/rate-limits · Webhooks: https://developers.activecampaign.com/page/webhooks · Index: https://developers.activecampaign.com/llms.txt
- **Auth:** `api_key` — secret binding: `apiKey`

## Scope

One node for ActiveCampaign CRM + email automation. Calls the account-specific v3 REST host taken from **Settings → Developer** (do **not** invent `{account}.api-us1.com`; non-US accounts use other hosts). JSON over HTTPS. Contact–organization nodes are **deprecated**; use Account–Contact endpoints. This node does not call the eCommerce GraphQL API (REST `ecomOrders` / `ecomCustomers` / `connections` cover catalog ecommerce ops).

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.create` | POST | `/api/3/contacts` | Create a contact (`email` typical) |
| `contact.get` | GET | `/api/3/contacts/{id}` | Fetch one contact |
| `contact.list` | GET | `/api/3/contacts` | List/search/filter (`email`, list, account, …) |
| `contact.update` | PUT | `/api/3/contacts/{id}` | Update; prefer `POST /api/3/contact/sync` when upsert-by-email is intended |
| `contact.delete` | DELETE | `/api/3/contacts/{id}` | Delete a contact |
| `contactList.add` | POST | `/api/3/contactLists` | Subscribe/unsubscribe a contact to a list (`status`) |
| `contactList.remove` | POST | `/api/3/contactLists` | Unsubscribe: same resource with `status: 2` |
| `contactTag.add` | POST | `/api/3/contactTags` | Attach tag id to contact id |
| `contactTag.remove` | DELETE | `/api/3/contactTags/{id}` | Remove a contactTag row |
| `account.create` | POST | `/api/3/accounts` | Create a CRM account (organization) |
| `account.get` | GET | `/api/3/accounts/{id}` | Fetch one account |
| `account.list` | GET | `/api/3/accounts` | List accounts |
| `account.update` | PUT | `/api/3/accounts/{id}` | Update an account |
| `account.delete` | DELETE | `/api/3/accounts/{id}` | Delete an account |
| `accountContact.create` | POST | `/api/3/accountContacts` | Link contact ↔ account |
| `accountContact.update` | PUT | `/api/3/accountContacts/{id}` | Update the link |
| `accountContact.delete` | DELETE | `/api/3/accountContacts/{id}` | Unlink |
| `deal.create` | POST | `/api/3/deals` | Create a deal |
| `deal.get` | GET | `/api/3/deals/{id}` | Fetch one deal |
| `deal.list` | GET | `/api/3/deals` | List deals |
| `deal.update` | PUT | `/api/3/deals/{id}` | Update a deal |
| `deal.delete` | DELETE | `/api/3/deals/{id}` | Delete a deal |
| `deal.note.create` | POST | `/api/3/notes` | Note with `reltype=Deal` + `relid` |
| `deal.note.update` | PUT | `/api/3/notes/{id}` | Update a note |
| `list.list` | GET | `/api/3/lists` | List mailing lists |
| `tag.create` | POST | `/api/3/tags` | Create a tag |
| `tag.get` | GET | `/api/3/tags/{id}` | Fetch one tag |
| `tag.list` | GET | `/api/3/tags` | List tags |
| `tag.update` | PUT | `/api/3/tags/{id}` | Update a tag |
| `tag.delete` | DELETE | `/api/3/tags/{id}` | Delete a tag |
| `ecommerce.order.create` | POST | `/api/3/ecomOrders` | Deep Data order |
| `ecommerce.order.get` | GET | `/api/3/ecomOrders/{id}` | Fetch order |
| `ecommerce.order.list` | GET | `/api/3/ecomOrders` | List orders |
| `ecommerce.order.update` | PUT | `/api/3/ecomOrders/{id}` | Update order |
| `ecommerce.order.delete` | DELETE | `/api/3/ecomOrders/{id}` | Delete order |
| `ecommerce.customer.create` | POST | `/api/3/ecomCustomers` | Deep Data customer |
| `ecommerce.customer.get` | GET | `/api/3/ecomCustomers/{id}` | Fetch customer |
| `ecommerce.customer.list` | GET | `/api/3/ecomCustomers` | List customers |
| `ecommerce.customer.update` | PUT | `/api/3/ecomCustomers/{id}` | Update customer |
| `ecommerce.customer.delete` | DELETE | `/api/3/ecomCustomers/{id}` | Delete customer |
| `ecommerce.orderProduct.get` | GET | `/api/3/ecomOrderProducts/{id}` | Fetch line item |
| `ecommerce.orderProduct.list` | GET | `/api/3/ecomOrderProducts` | List line items |
| `connection.create` | POST | `/api/3/connections` | Deep Data connection |
| `connection.get` | GET | `/api/3/connections/{id}` | Fetch connection |
| `connection.list` | GET | `/api/3/connections` | List connections |
| `connection.update` | PUT | `/api/3/connections/{id}` | Update connection |
| `connection.delete` | DELETE | `/api/3/connections/{id}` | Delete connection |
| `trigger.listen` | inbound POST | StudioQuack ingress | Receive account webhooks (subscribe, unsubscribe, open, …) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.create` | no | Chooses the row above |
| `apiUrl` | string | yes | | no | Exact API base from Developer tab, e.g. `https://xyz.api-us1.com` (no trailing `/api/3`) |
| `apiKey` | password | yes | | **yes** | Header `Api-Token`. Per-user key; keep off client-side code |
| `eventType` | string | for `trigger.listen` | | no | Webhook action filter (subscribe, unsubscribe, update, open, click, bounce, …) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo `Api-Token` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create | contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | update | Path id |
| `email` | string | create (unless `allowNullEmail`) | Contact email |
| `firstName` / `lastName` / `phone` | string | no | Profile |
| `fieldValues` | object[] | no | `{ field: customFieldId, value }` — **id, not name** |
| `tags` | string[] | create only | Tag **names** on create body (official example) |
| `upsertByEmail` | boolean | no | If true on update, POST `/contact/sync` instead of PUT by id |

### `operation = contact.get | contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Contact id |

### `operation = contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | no | Filter |
| `listId` | string | no | Filter by list |
| `limit` / `offset` | number | no | Pagination |

### `operation = contactList.add | contactList.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Contact |
| `listId` | string | yes | List |
| `status` | number | add | `1` subscribe, `2` unsubscribe (official contactList statuses) |

### `operation = contactTag.add | contactTag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | add | Contact id |
| `tagId` | string | add | Tag id (integer id, not name) |
| `contactTagId` | string | remove | `contactTags` row id |

### `operation = account.create | account.update | account.get | account.delete | account.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountId` | string | get/update/delete | Account id |
| `name` | string | create | Account name |
| `accountUrl` | string | no | Website |
| `fields` | object[] | no | Custom account fields |
| `limit` / `offset` | number | list | Pagination |

### `operation = accountContact.create | accountContact.update | accountContact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountContactId` | string | update/delete | Link id |
| `account` | string | create | Account id |
| `contact` | string | create | Contact id |
| `jobTitle` | string | no | Title on the association |

### `operation = deal.create | deal.update | deal.get | deal.delete | deal.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dealId` | string | get/update/delete | Deal id |
| `title` | string | create | Deal title |
| `value` | number | no | Value in cents |
| `currency` | string | no | ISO currency |
| `pipeline` / `stage` / `owner` / `contact` | string | no | Pipeline placement |
| `limit` / `offset` | number | list | Pagination |

### `operation = deal.note.create | deal.note.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `noteId` | string | update | Note id |
| `dealId` | string | create | `relid` with `reltype=Deal` |
| `note` | string | yes | Body |

### `operation = list.list | tag.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` / `offset` | number | no | Pagination |

### `operation = tag.create | tag.get | tag.update | tag.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tagId` | string | get/update/delete | Tag id |
| `tag` | string | create | Tag name |
| `tagType` | string | no | e.g. `contact` |
| `description` | string | no | Description |

### `operation = ecommerce.order.* | ecommerce.customer.* | ecommerce.orderProduct.* | connection.*`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | get/update/delete | Resource id |
| `connectionid` | string | order/customer writes | Deep Data connection |
| `email` / `externalid` / `totalPrice` / `currency` / `orderProducts` | mixed | order create | Official ecomOrders fields |
| `limit` / `offset` | number | list | Pagination |
| `service` / `externalid` / `name` | string | connection create | Connection identity |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none on INPUT)* | | | Ingress POST; at-least-once, **never retried** by ActiveCampaign |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status (200/201) |
| `resource` | object | CRUD | Envelope (`contact`, `deal`, `account`, `ecomOrder`, …) plus sideloaded arrays |
| `id` | string | create/get | Resource id |
| `meta` | object | list | `total` when the collection paginates |
| `event` | object | `trigger.listen` | Webhook POST fields (`type`, contact, campaign, …) |
| `error` | object | 4xx/5xx | `{ code, message, status, details }` — no token, no email/phone values |

## Legal

- Developer terms via the ActiveCampaign account and https://www.activecampaign.com/legal. API keys are per user; Admin-only endpoints (e.g. `GET /settings/account`) must not be called unless the key’s user is Admin.
- Rate limits (official): **5 requests per second per account**, shared with eComm GraphQL. `429` includes `Retry-After`; responses also send `RateLimit-Limit` / `RateLimit-Remaining`.
- Email marketing: caller holds CAN-SPAM/CASL/GDPR consent. List subscribe via API still requires a lawful basis and the list’s sender reminder/URL rules.
- PII: contacts, phones, custom fields, deals, ecommerce orders. **(a)** Minimize fields. **(b)** Customer owns the ActiveCampaign account and is the controller. **(c)** StudioQuack does not accept vendor ToS on their behalf. **(d)** Never log `apiKey` or PII values.
- Official-API-first: v3 REST only. Do not scrape the app UI. Do not ship v1 `contact_add` query-string calls.

## Implementation notes

- JDK `HttpClient` only (ActiveCampaign publishes no official SDK). Header `Api-Token`. Base = operator `apiUrl` + `/api/3`.
- Timeouts: connect 10s, read 30s. Body cap 1 MiB. Pagination `limit`/`offset`.
- Retry: `429`/`5xx`, max 3, honor `Retry-After`. Token-bucket at 5 rps.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Some campaign-delete paths return HTTP 200 with `succeeded: 0` — treat `succeeded=0` as `{ error }`.
- Webhooks: register in the ActiveCampaign UI or `POST /api/3/webhooks` if building a `trigger` register later; inbound `trigger.listen` must be idempotent. HTTP 410 from ingress deactivates the vendor webhook.
