# Folk

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/folk/`
- **Researched:** 2026-08-20
- **Official API:** Folk External API **REST v1**, OpenAPI version **`2025-06-09`** (not GraphQL)
- **Docs:** https://developer.folk.app/api-reference/overview — versioning https://developer.folk.app/api-reference/versioning — OpenAPI https://developer.folk.app/schemas/2025-06-09.json
- **Auth:** `api_key` — secret binding: `apiKey`

## Scope

One node for Folk CRM. Calls Folk’s **public REST API** at `https://api.folk.app` (`/v1/people`, `/v1/companies`, `/v1/groups`, `/v1/notes`, `/v1/webhooks`). Catalog row “Folk GraphQL” is **wrong**: official docs (help.folk.app 2025-09-01 and developer.folk.app) describe a JSON REST API with Bearer keys. Pin `X-API-Version: 2025-06-09`. API access is a paid-plan feature. People/companies/notes are PII; the **customer owns the Folk workspace**. Do not scrape `app.folk.app`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `person.create` | POST | `/v1/people` | Create a person (duplicate merge may run server-side) |
| `person.get` | GET | `/v1/people/{personId}` | Fetch one person by `per_…` id |
| `person.update` | PATCH | `/v1/people/{personId}` | Partial update |
| `person.search` | GET | `/v1/people` | List/filter people (`filter[…]`) |
| `company.create` | POST | `/v1/companies` | Create a company |
| `company.get` | GET | `/v1/companies/{companyId}` | Fetch one company by `com_…` id |
| `company.update` | PATCH | `/v1/companies/{companyId}` | Partial update |
| `group.list` | GET | `/v1/groups` | List workspace groups |
| `note.create` | POST | `/v1/notes` | Create a note on a person/company/object |
| `customField.set` | PATCH | `/v1/people/{id}` or `/v1/companies/{id}` | Set `customFieldValues` on an entity (not a separate RPC) |
| `trigger.listen` | POST | `/v1/webhooks` | Register webhook → SQ message-event ingress |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `person.get` | no | Chooses the row above |
| `apiKey` | password | yes | | yes | Workspace API key (`Authorization: Bearer`) |
| `apiVersion` | string | no | `2025-06-09` | no | Sent as `X-API-Version` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo `apiKey` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = person.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `firstName` | string | no | Given name (max 500) |
| `lastName` | string | no | Family name (max 500) |
| `fullName` | string | no | Combined name (max 1000) |
| `emails` | string[] | no | Emails; first is primary |
| `phones` | string[] | no | Phone numbers |
| `jobTitle` | string | no | Title |
| `groups` | object[] | no | `[{ "id": "grp_…" }]`; max 100 |
| `companies` | object[] | no | `{ id }` or `{ name }` (creates company); max 20 |
| `customFieldValues` | object | no | Per-group custom values |
| `description` | string | no | Bio (max 5000) |

### `operation = person.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `personId` | string | yes | `per_…` id |

### `operation = person.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `personId` | string | yes | `per_…` id |
| `firstName` / `lastName` / `emails` / `phones` / `jobTitle` / `groups` / `companies` / `customFieldValues` / `description` | mixed | no | Same shapes as create; omit unchanged fields |

### `operation = person.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `filter` | object | no | Folk filter map, e.g. `{ "fullName": { "like": "Ada" } }` |
| `combinator` | string | no | `and` (default) or `or` |
| `cursor` | string | no | Pagination cursor |
| `limit` | number | no | Page size (honor provider max) |

### `operation = company.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Company name |
| `emails` / `phones` / `urls` / `addresses` | string[] | no | Contact points |
| `groups` | object[] | no | `[{ "id": "grp_…" }]` |
| `industry` / `description` | string | no | Profile |
| `customFieldValues` | object | no | Per-group custom values |

### `operation = company.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `companyId` | string | yes | `com_…` id |

### `operation = company.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `companyId` | string | yes | `com_…` id |
| `name` / `emails` / `groups` / `customFieldValues` / … | mixed | no | Partial update |

### `operation = group.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `cursor` | string | no | Pagination |
| `visibility` | string | no | Filter if documented on list-groups |

### `operation = note.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `content` | string | yes | Note body |
| `entityId` | string | yes | `per_…` / `com_…` / object id the note attaches to |
| `visibility` | string | no | e.g. workspace vs private (provider enum) |

### `operation = customField.set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `entityType` | string | yes | `person` or `company` |
| `entityId` | string | yes | Target id |
| `customFieldValues` | object | yes | Values keyed by group + field (Folk schema) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Webhook label (max 255) |
| `targetUrl` | string | yes | Public HTTPS SQ message-event URL |
| `subscribedEvents` | object[] | yes | `{ eventType }` — e.g. `person.created`, `person.updated`, `person.deleted`, `company.created`, `note.created` |

## Output fields (by operation)

### `person.create` / `person.get` / `person.update` / `customField.set` (person)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `id` | string | success | `per_…` |
| `person` | object | success | Provider person JSON (minimize before logging) |
| `error` | object | failure | `{ status, code, message }` |

### `person.search`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `people` | object[] | success | Page of people |
| `nextCursor` | string | when more pages | Pagination |
| `error` | object | failure | `{ status, code, message }` |

### `company.create` / `company.get` / `company.update` / `customField.set` (company)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `id` | string | success | `com_…` |
| `company` | object | success | Provider company JSON |
| `error` | object | failure | `{ status, code, message }` |

### `group.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `groups` | object[] | success | `{ id, name, … }` |
| `nextCursor` | string | when more pages | Pagination |
| `error` | object | failure | `{ status, code, message }` |

### `note.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `id` | string | success | Note id |
| `note` | object | success | Created note |
| `error` | object | failure | `{ status, code, message }` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `webhookId` | string | success | Folk webhook id |
| `targetUrl` | string | success | Registered URL (no secrets) |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- Terms: https://www.folk.app/terms-of-use
- Privacy: https://www.folk.app/privacy-policy
- Rate limit (published): **600 requests/minute per user**; `429` + `Retry-After`. Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- PII: contacts, emails, notes, custom fields. Minimize INPUT. Customer is controller of Folk data. Never log tokens or payload bodies.
- Official-API-first: `api.folk.app` only. No scrape of the Folk UI.

## Implementation notes

- JDK `HttpClient` only. Host allowlist: `api.folk.app`.
- Headers: `Authorization: Bearer <apiKey>`, `Content-Type: application/json`, `X-API-Version: 2025-06-09`, `Accept: application/json`.
- Timeouts: connect 10s, read 30s. Response cap 2 MiB.
- Retry: `429` honor `Retry-After`; `5xx` exponential backoff + jitter, max 3. Do not retry `4xx` except 429.
- Errors → `{ error }` on OUTPUT. Never throw past dispatch.
- Validate Folk ids (`per_`, `com_`, `grp_`) before calling.
- **DO NOT BUILD** in this task.
