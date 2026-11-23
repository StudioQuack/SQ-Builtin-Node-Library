# HighLevel

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/highlevel/`
- **Researched:** 2026-08-20
- **Official API:** HighLevel (GoHighLevel) Public API **v2**, host `https://services.leadconnectorhq.com`. Required header `Version` (date-based default **`2021-07-28`**; some newer docs pages enumerate `v3` — send what that endpoint’s Version switcher lists).
- **Docs:** https://marketplace.gohighlevel.com/docs/
- **Auth:** OAuth 2.0 (Marketplace app: agency **or** location token) **or** Private Integration Token (PIT, typically `pit-…`). Secret bindings: `access_token` / PIT, plus `client_id`/`client_secret` for OAuth refresh. **API v1 reached end-of-support 2025-12-31** — do not call v1.

## Scope

Sub-account CRM: contacts, opportunities, tasks, calendar free-slots and booking, webhook listen. One node. **Agency vs location:**

- **Location token / location PIT:** call location-scoped resources (`locationId` on the contact, opportunities for that sub-account).
- **Agency token:** agency resources only unless you mint a location token via `POST /oauth/locationToken` (`companyId` + `locationId`, `oauth.write`, form-urlencoded). Do not write contacts with a raw agency token (cross-location leak).
- Marketplace distribution: Agency vs Sub-Account install; follow Marketplace ToS.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.upsert` | POST | `/contacts/upsert` | Create/update per location duplicate settings (`locationId` required) |
| `contact.get` | GET | `/contacts/{contactId}` | |
| `contact.list` | POST | `/contacts/search` | Search/list. Legacy `GET /contacts/` is deprecated. |
| `contact.update` | PUT | `/contacts/{contactId}` | |
| `contact.delete` | DELETE | `/contacts/{contactId}` | |
| `opportunity.create` | POST | `/opportunities/` | Pipeline deal |
| `opportunity.get` | GET | `/opportunities/{id}` | |
| `opportunity.list` | GET | `/opportunities/search` | Query filters |
| `opportunity.update` | PUT | `/opportunities/{id}` | |
| `opportunity.delete` | DELETE | `/opportunities/{id}` | |
| `task.create` | POST | `/contacts/{contactId}/tasks` | Contact task |
| `task.get` | GET | `/contacts/{contactId}/tasks/{taskId}` | |
| `task.list` | GET | `/contacts/{contactId}/tasks` | |
| `task.update` | PUT | `/contacts/{contactId}/tasks/{taskId}` | |
| `task.delete` | DELETE | `/contacts/{contactId}/tasks/{taskId}` | |
| `calendar.freeSlots` | GET | `/calendars/{calendarId}/free-slots` | Availability |
| `calendar.book` | POST | `/calendars/events/appointments` | Book slot |
| `trigger.listen` | n/a | Marketplace app webhook settings (50+ events) | SQ ingress; verify per HighLevel webhook signature docs |

Task paths verified: https://marketplace.gohighlevel.com/docs/ghl/contacts/create-task/ and get-all-tasks. Create body requires `title`, `dueDate`, `completed`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.upsert` | no | |
| `auth_type` | select | yes | `pit` | no | `pit` \| `oauth_location` \| `oauth_agency` |
| `access_token` | password | yes | empty | yes | PIT or OAuth access token (location-scoped for CRM writes) |
| `client_id` | string | OAuth | empty | no | Marketplace app |
| `client_secret` | password | OAuth | empty | yes | Refresh |
| `locationId` | string | yes for location ops | empty | no | Sub-account id. bodyFrom/queryFrom fallback when INPUT `locationId` is blank. |
| `company_id` | string | agency→location exchange | empty | no | |
| `api_version` | string | no | `2021-07-28` | no | `Version` header |
| `timeout_seconds` | number | no | `25` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Contacts are PII |
| `output` | OUTPUT | object or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `contact.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `locationId` | string | yes unless config | Body `locationId` |
| `email` | string | no | Duplicate-match key |
| `phone` | string | no | Duplicate-match key |
| `first_name` / `last_name` / `name` | string | no | |
| `tags` | array | no | Overwrites all tags if sent — prefer dedicated tag APIs for add/remove (out of catalog) |
| `custom_fields` | array | no | `{ id, field_value }` as documented |
| `create_new_if_duplicate_allowed` | boolean | no | Maps `createNewIfDuplicateAllowed` |
| `dnd` | boolean | no | |
| `source` | string | no | |
| `assigned_to` | string | no | User id |

### `contact.get` / `contact.update` / `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | |
| `record` | object | update yes | Partial contact JSON |

### `contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `locationId` | string | yes | Search body/query as docs |
| `query` | string | no | |
| `page` / `page_limit` | number | no | |
| `filters` | object | no | Official search filters only |

### Opportunity ops

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | get/update/delete | Opportunity id |
| `contact_id` | string | create | |
| `pipeline_id` | string | create | |
| `pipeline_stage_id` | string | create | |
| `name` | string | no | |
| `monetary_value` | number | no | |
| `status` | string | no | |
| `locationId` | string | list | Search query |

### Task ops

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contact_id` | string | yes | |
| `task_id` | string | get/update/delete | |
| `title` | string | create yes | |
| `body` | string | no | |
| `due_date` | string | create yes | ISO-8601 `dueDate` |
| `completed` | boolean | create yes | |
| `assigned_to` | string | no | |

### `calendar.freeSlots`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `calendar_id` | string | yes | |
| `start_date` | string | yes | Query as documented (epoch or `YYYY-MM-DD`) |
| `end_date` | string | yes | |
| `timezone` | string | no | |
| `user_id` | string | no | |

### `calendar.book`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `calendar_id` | string | yes | |
| `locationId` | string | yes | |
| `contact_id` | string | yes | |
| `start_time` | string | yes | ISO-8601 |
| `end_time` | string | no | |
| `title` | string | no | |
| `appointment_status` | string | no | e.g. `confirmed` |
| `assigned_user_id` | string | no | |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | yes | Webhook JSON (`type`, location, contact ids) |
| `signature` | string | yes | Provider signature header as Marketplace webhook docs |

## Output fields (by operation)

### Contact / opportunity / task / calendar.book

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `resource` | object | 2xx | `contact` / opportunity / task / appointment |
| `new` | boolean | upsert 2xx | HighLevel `new` flag |
| `trace_id` | string | when present | |
| `error` | string | failure | |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | 30 |

### `contact.list` / `opportunity.list` / `task.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `results` | array | 2xx | |
| `meta` | object | 2xx | Pagination |

### `calendar.freeSlots`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `slots` | object | 2xx | Date-keyed availability map |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | verified | `ok` |
| `event_type` | string | | |
| `payload` | object | | |

## Legal

- Docs: https://marketplace.gohighlevel.com/docs/
- Upsert: https://marketplace.gohighlevel.com/docs/ghl/contacts/upsert-contact/
- Create task: https://marketplace.gohighlevel.com/docs/ghl/contacts/create-task/
- V1 EOS: https://help.gohighlevel.com/support/solutions/articles/48001060529-highlevel-api-documentation
- Marketplace / OAuth: https://help.gohighlevel.com/support/solutions/articles/155000000136-how-to-get-started-with-the-developer-s-marketplace
- Operator accepts HighLevel Marketplace and product ToS. Contacts, SMS consent, and call recordings (out of this node) are PII. Never log Bearer/PIT.
- SMS/email sends are **not** catalog ops on this node (Conversations API is a different surface).

## Implementation notes

- Every request: `Authorization: Bearer`, `Version: {api_version}`, `Accept: application/json`. JDK HTTP. No vendor SDK.
- If `auth_type=oauth_agency` and operation is location CRM: first `POST https://services.leadconnectorhq.com/oauth/locationToken` (form-urlencoded `companyId`, `locationId`); use returned location token; **do not log it**.
- Timeout 25 s. Body cap 1 MiB. TLS verified. `Redirect.NEVER`.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry.
