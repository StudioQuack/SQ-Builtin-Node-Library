# Zendesk Sell

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/zendesk-sell/`
- **Researched:** 2026-08-20
- **Official API:** Zendesk Sell **Core API v2** + **Firehose API v3** (ex-Base CRM). Host `https://api.getbase.com`. **Not** Zendesk Support (`*.zendesk.com/api/v2`).
- **Docs:** https://developer.zendesk.com/api-reference/sales-crm/introduction/ — requests https://developer.zendesk.com/api-reference/sales-crm/requests/ — OAuth https://developer.zendesk.com/api-reference/sales-crm/authentication/introduction/ — leads https://developer.zendesk.com/api-reference/sales-crm/resources/leads/ — rate limits https://developer.zendesk.com/api-reference/sales-crm/rate-limits/ — Firehose https://developer.zendesk.com/api-reference/sales-crm/firehose/overview/
- **Auth:** oauth2 — secret bindings: `clientSecret`, `accessToken`, `refreshToken`

## Scope

One node for **Zendesk Sell** (Sales CRM). CRUD uses Core **`/v2/{resource}`**. JSON envelope `{ "data": { … }, "meta": { "type": "lead" } }`. `PUT` is partial update. All requests HTTPS + JSON + a **non-empty `User-Agent`** (missing UA → 400 `invalid_user_agent`).

OAuth app is **Sell-specific** (separate from Support). Token endpoint `/oauth2/token` on `api.getbase.com`. Single-user dashboard tokens may not expire; OAuth access tokens from the token endpoint last about **one hour** (refresh).

`trigger.listen` is **Firehose** `GET /v3/{resource}/stream` (pull one page). Firehose is a **premium** Sell API (Growth / Snap Advanced). Core-only orgs get a clear `{ error }` — do not call Support webhooks.

**Customer owns the Sell account.** Contacts/leads/deals are PII.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `lead.create` | POST | `/v2/leads` | Create lead (`last_name` **or** `organization_name`) |
| `lead.get` | GET | `/v2/leads/:id` | One lead |
| `lead.list` | GET | `/v2/leads` | Page leads (`per_page` max 100) |
| `lead.update` | PUT | `/v2/leads/:id` | Partial update |
| `contact.create` | POST | `/v2/contacts` | Create person/company contact |
| `contact.get` | GET | `/v2/contacts/:id` | One contact |
| `contact.list` | GET | `/v2/contacts` | Page contacts |
| `contact.update` | PUT | `/v2/contacts/:id` | Partial update |
| `deal.create` | POST | `/v2/deals` | Create deal |
| `deal.get` | GET | `/v2/deals/:id` | One deal |
| `deal.list` | GET | `/v2/deals` | Page deals |
| `deal.update` | PUT | `/v2/deals/:id` | Partial update |
| `deal.delete` | DELETE | `/v2/deals/:id` | Delete deal |
| `note.create` | POST | `/v2/notes` | Note on `lead` / `contact` / `deal` |
| `task.create` | POST | `/v2/tasks` | Create task |
| `task.get` | GET | `/v2/tasks/:id` | One task |
| `pipeline.list` | GET | `/v2/pipelines` | Pipelines |
| `stage.list` | GET | `/v2/stages` | Stages (optional `pipeline_id`) |
| `trigger.listen` | GET | `/v3/{resource}/stream` | Firehose page (`leads`, `contacts`, `deals`, `notes`, `tasks`) |

Decimal **deal value**: API may return decimals as **strings**. Do not coerce through integer on update (precision loss warning in Sell intro).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `lead.list` | no | Chooses the row above |
| `clientId` | string | no | | no | OAuth client id (refresh) |
| `clientSecret` | password | no | | yes | OAuth client secret |
| `accessToken` | password | yes | | yes | Bearer token |
| `refreshToken` | password | no | | yes | Refresh grant |
| `timeout_seconds` | number | no | `25` | no | Connect + read. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens; do not log emails/phones |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = lead.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `last_name` | string | yes* | Required unless `organization_name` |
| `organization_name` | string | yes* | Required unless `last_name` |
| `first_name` / `email` / `phone` / `mobile` / `title` / `status` / `owner_id` / `source_id` / `website` / `address` / `tags` / `custom_fields` | mixed | no | Core lead fields |

Wrap body as `{ "data": { … } }`. `meta.type` optional.

### `operation = lead.get` | `contact.get` | `deal.get` | `task.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |

### `operation = lead.list` | `contact.list` | `deal.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | From 1 |
| `per_page` | number | no | Default 25; **max 100** |
| `sort_by` | string | no | e.g. `updated_at:desc` |
| `ids` | string | no | Comma-separated ids |
| `owner_id` / `email` / `status` | mixed | no | Resource filters as documented |
| `custom_fields` | object | no | `custom_fields[name]=value` |

### `operation = lead.update` | `contact.update` | `deal.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Resource id |
| `fields` | object | yes | Partial `data` map. `tags` replaces the full set |

### `operation = contact.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `is_organization` | boolean | no | Company vs person |
| `name` / `first_name` / `last_name` / `email` / `phone` / `owner_id` / `address` / `tags` / `custom_fields` | mixed | no | Contact fields per Core Contacts docs |

### `operation = deal.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Deal name |
| `contact_id` | number | no | Associated contact |
| `value` | number \| string | no | Amount; keep decimals as string if present |
| `currency` | string | no | ISO currency |
| `stage_id` / `pipeline_id` / `owner_id` / `hot` / `tags` / `custom_fields` | mixed | no | |

### `operation = deal.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Deal id |

### `operation = note.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource_type` | string | yes | `lead` \| `contact` \| `deal` |
| `resource_id` | number | yes | Parent id |
| `content` | string | yes | Note body (PII possible) |
| `is_important` | boolean | no | |
| `tags` | string[] | no | |

### `operation = task.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `content` | string | yes | Task text |
| `resource_type` / `resource_id` | mixed | no | Association |
| `due_date` | string | no | ISO-8601 |
| `owner_id` | number | no | |

### `operation = pipeline.list` | `stage.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` / `per_page` | number | no | Pagination |
| `pipeline_id` | number | no | `stage.list` filter |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource` | string | yes | `leads` \| `contacts` \| `deals` \| `notes` \| `tasks` |
| `position` | string | yes | `top` (cursor only, empty items), `tail`, or Firehose position token |
| `limit` | number | no | 1–100; default 25 |

One GET per dispatch (not a socket). Persist `meta.position` in the workflow for the next poll. `position=top` returns empty `items` by design.

## Output fields (by operation)

### create / get / update (lead, contact, deal, note, task)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | number | success | Resource id |
| `data` | object | success | Sell `data` (**PII**) |
| `meta` | object | success | `{ type }` |
| `error` | string | failure | Sell error code/message; no token |

### list ops + `pipeline.list` + `stage.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | object[] | success | `{ data, meta }` rows |
| `count` | number | success | Page count |
| `error` | string | failure | |

### `deal.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 204/2xx | `ok` |
| `id` | number | success | Deleted id |
| `error` | string | failure | |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `items` | object[] | success | Events (`data` + `meta.event_type`) |
| `position` | string | success | Next Firehose cursor — persist |
| `top` | boolean | success | True after `position=top` |
| `error` | string | failure | Plan/addon missing → actionable message |

## Legal

- Sell intro: https://developer.zendesk.com/api-reference/sales-crm/introduction/
- Rate limit: **36,000 requests/hour** and **10 requests/token/second**. HTTP **429** until reset.
- Firehose: premium; 72-hour history; not Support tickets.
- CRM records are PII. Customer owns the Sell org. Never log tokens, emails, phones, or note content.
- Separate OAuth app from Zendesk Support. Wrong host (`zendesk.com` Support API) is out of scope.
- Official-API-first: `api.getbase.com` only.

## Implementation notes

- JDK `HttpClient` only. Host `api.getbase.com`. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer {accessToken}`, `Accept: application/json`, `Content-Type: application/json` on writes, **`User-Agent: StudioQuack/1.0`**.
- Empty token → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- 429 → `{ error, http_status: 429, retry_after_seconds }` honor `Retry-After` else 6. 5xx/timeout → 30. Do not tight-loop. Stay under 10 rps/token.
- Refresh expired OAuth access tokens via `/oauth2/token`; never log refresh tokens.
- Logs: operation, resource id, status, counts — never payloads.
- **DO NOT BUILD** in this task.
