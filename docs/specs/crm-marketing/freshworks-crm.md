# Freshworks CRM

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/freshworks-crm/`
- **Researched:** 2026-08-20
- **Official API:** Freshsales / Freshworks CRM REST — **unversioned** live reference (no version header)
- **Docs:** https://developers.freshworks.com/crm/api/
- **Auth:** `api_key` — secret binding: `apiKey` (`Authorization: Token token=<apiKey>`)

## Scope

One node for **Freshworks CRM (Freshsales)**. Base URL `https://{bundleAlias}.myfreshworks.com/crm/sales/api` (legacy host `{bundleAlias}.freshworks.com/crm/sales/api` still appears in docs). Per-user API key; **bundle alias** is the subdomain of the CRM account inside a Freshworks org. Contacts, accounts (`sales_accounts`), deals, tasks, appointments, notes, and sales activities. List endpoints require a **saved view id** (`/view/{view_id}`), not a bare collection GET. There is **no REST webhook-subscribe** method; `trigger.listen` documents the official **Workflow “Trigger webhook”** action pointing at SQ ingress. **Customer owns the CRM tenant.** Do not call Freshdesk/Freshservice APIs from this node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/api/contacts` | Create contact (email **or** mobile **or** twitter required) |
| `contact.get` | GET | `/api/contacts/{id}` | View contact |
| `contact.list` | GET | `/api/contacts/view/{view_id}` | List contacts in a view (`page`) |
| `contact.update` | PUT | `/api/contacts/{id}` | Update contact |
| `contact.delete` | DELETE | `/api/contacts/{id}` | Delete contact |
| `account.create` | POST | `/api/sales_accounts` | Create account |
| `account.get` | GET | `/api/sales_accounts/{id}` | View account |
| `account.list` | GET | `/api/sales_accounts/view/{view_id}` | List accounts in a view |
| `account.update` | PUT | `/api/sales_accounts/{id}` | Update account |
| `account.delete` | DELETE | `/api/sales_accounts/{id}` | Delete account |
| `deal.create` | POST | `/api/deals` | Create deal |
| `deal.get` | GET | `/api/deals/{id}` | View deal |
| `deal.list` | GET | `/api/deals/view/{view_id}` | List deals in a view |
| `deal.update` | PUT | `/api/deals/{id}` | Update deal |
| `deal.delete` | DELETE | `/api/deals/{id}` | Delete deal |
| `appointment.create` | POST | `/api/appointments` | Create appointment |
| `appointment.get` | GET | `/api/appointments/{id}` | View appointment |
| `appointment.list` | GET | `/api/appointments?filter=…` | List appointments |
| `appointment.update` | PUT | `/api/appointments/{id}` | Update appointment |
| `appointment.delete` | DELETE | `/api/appointments/{id}` | Delete appointment |
| `task.create` | POST | `/api/tasks` | Create task |
| `task.get` | GET | `/api/tasks/{id}` | View task |
| `task.list` | GET | `/api/tasks?filter=…` | List tasks |
| `task.update` | PUT | `/api/tasks/{id}` | Update task |
| `task.delete` | DELETE | `/api/tasks/{id}` | Delete task |
| `note.create` | POST | `/api/notes` | Note on contact/account/deal |
| `note.update` | PUT | `/api/notes/{id}` | Update note |
| `note.delete` | DELETE | `/api/notes/{id}` | Delete note |
| `salesActivity.get` | GET | `/api/sales_activities/{id}` | View sales activity |
| `salesActivity.list` | GET | `/api/sales_activities` | List sales activities |
| `trigger.listen` | — | Workflow webhook (product, not REST) | Operator configures Workflow → SQ URL |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | Profile → API Settings key |
| `bundleAlias` | string | yes | | no | Subdomain (`widgetz` in `widgetz.myfreshworks.com`) — official bundle alias |
| `hostSuffix` | select | no | `myfreshworks.com` | no | `myfreshworks.com` or `freshworks.com` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `apiKey` |

FUNCTION ports: none.

## Input fields (by operation)

### `contact.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `first_name` / `last_name` | string | no | Name |
| `email` or `emails` | string / object[] | one of email/mobile/twitter | Identity (docs: `emails` preferred; `email` deprecated) |
| `mobile_number` | string | one of email/mobile/twitter | Mobile |
| `job_title` / `address` / `city` / `state` / `zipcode` / `country` | string | no | Profile |
| `sales_accounts` | object[] | no | `{ id, is_primary }`; one primary if any |
| `owner_id` | number | no | Owner user id |
| `custom_field` | object | no | `cf_*` keys |

### `contact.get` / `contact.update` / `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Contact id |
| `include` | string | no | Embed, e.g. `owner` |
| *(update)* same body keys as create | mixed | no | PUT body under `{ "contact": { … } }` |

### `contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `view_id` | number | yes | Saved filter/view id (`GET /api/contacts/filters` to discover) |
| `page` | number | no | 1-based; default 25 rows/page |

Account and deal get/update/delete/list use the same `id` / `view_id` / `page` pattern with `{ "sales_account": { … } }` or `{ "deal": { … } }` wrappers.

### `deal.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Deal name (`*` in official schema) |
| `amount` | number | yes | Deal value, decimal(15,2) |
| `sales_account_id` | number | yes | Existing account id (or create-account-on-the-fly per docs) |
| `deal_stage_id` / `deal_pipeline_id` | number | no | Pipeline/stage selectors |
| `owner_id` | number | no | Owner |
| `expected_close` | string | no | Date |
| `custom_field` | object | no | Custom deal fields |

### `appointment.create` / `task.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | Title |
| `from_date` / `end_date` / `due_date` | string | per type | ISO timestamps (UTC) |
| `targetable_id` / `targetable_type` | mixed | no | Related record |
| `owner_id` | number | no | Assignee |

### `note.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `description` | string | yes | Note body |
| `targetable_type` | string | yes | `Contact` / `SalesAccount` / `Deal` |
| `targetable_id` | number | yes | Record id |

### `salesActivity.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | Pagination |
| `filter` | string | no | Official list query `filter=` when present on the task/appointment list docs |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callbackUrl` | string | yes | SQ message-event HTTPS URL (returned as instructions; **not** POSTed to Freshsales REST) |
| `eventHint` | string | no | Operator-facing: contact/deal/account create or update |

Handler does **not** call a subscribe API. Output explains Workflow → Actions → Trigger webhook. Verify `X-SQ-API-KEY` on ingress.

## Output fields (by operation)

### Create / get / update (contact, account, deal, task, appointment, note, sales activity)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `id` | number | success | Record id |
| `record` | object | success | Provider wrapper (`contact`, `sales_account`, `deal`, …) — PII, do not log |
| `error` | object | failure | `{ status, code, message }` from `errors` body |

### List operations

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `items` | object[] | success | Page of records |
| `page` | number | success | Requested page |
| `error` | object | failure | `{ status, code, message }` |

### `contact.delete` / `account.delete` / `deal.delete` / `task.delete` / `appointment.delete` / `note.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `deleted` | boolean | success | `true` |
| `error` | object | failure | `{ status, code, message }` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `manual_workflow_webhook` |
| `callbackUrl` | string | success | URL to paste in Freshsales Workflow |
| `instructions` | string | success | Point to https://crmsupport.freshworks.com/support/solutions/articles/50000002418 |
| `error` | object | failure | Missing URL / entitlement |

## Legal

- Freshworks Terms: https://www.freshworks.com/terms/
- Privacy: https://www.freshworks.com/privacy/
- Rate limit (docs errors table): **1000 API requests per hour per account**; HTTP `429`.
- PII: names, emails, phones, notes. GDPR erasure: prefer official forget endpoints only if the operator explicitly chooses them (out of this node’s catalog verbs).
- Official-API-first: `{bundleAlias}.{hostSuffix}/crm/sales/api` only.

## Implementation notes

- JDK `HttpClient` only. Allow hosts matching `*.myfreshworks.com` and `*.freshworks.com` (SSRF: no IPs, no other suffixes).
- Header: `Authorization: Token token=<apiKey>`, `Content-Type: application/json`.
- Timeouts: connect 10s, read 30s. Body cap 2 MiB.
- Retry: `429` until the hour window; `5xx` backoff + jitter, max 3. Follow `301/302` to HTTPS.
- JSON wrappers required: `{ "contact": {…} }`, `{ "sales_account": {…} }`, `{ "deal": {…} }`.
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
