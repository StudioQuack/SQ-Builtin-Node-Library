# Attio

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/attio/`
- **Researched:** 2026-08-20
- **Official API:** Attio REST API **v2** (`https://api.attio.com`, OpenAPI `info.version` **2.0.0**). JSON over HTTPS. People/company convenience paths exist under `/v2/objects/people|companies/records` as well as generic `/v2/objects/{object}/records`.
- **Docs:** Overview https://docs.attio.com/rest-api/overview · Auth https://docs.attio.com/rest-api/guides/authentication · Rate limits https://docs.attio.com/rest-api/guides/rate-limiting · Webhooks https://docs.attio.com/rest-api/guides/webhooks · Records https://docs.attio.com/rest-api/endpoint-reference/records/create-a-record · Index https://docs.attio.com/llms.txt
- **Auth:** `oauth2` (multi-workspace) or workspace API key (Bearer). Secrets: `clientSecret`, `workspaceToken` (and stored `accessToken`).

## Scope

One node for Attio CRM. Prefer **OAuth 2.0** for multi-workspace apps (`https://app.attio.com/oauth/token`); a single-workspace **API key** from developer settings is allowed. Scopes are set on the app or on the key (e.g. `record_permission:read-write`, `object_configuration:read`). Standard objects: people, companies, deals, users, workspaces. Lists hold **entries** that point at a parent record.

`person.upsert` / `company.upsert` map to official `PUT` upsert-by-matching-attribute (email/domain).

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `record.create` | POST | `/v2/objects/{object}/records` | Create; **throws** on unique-attribute conflict |
| `record.get` | GET | `/v2/objects/{object}/records/{record_id}` | One record |
| `record.update` | PATCH | `/v2/objects/{object}/records/{record_id}` | Append multiselect; use PUT to overwrite multiselect |
| `record.search` | POST | `/v2/objects/{object}/records/query` plus `/v2/records/search` for fuzzy | Filter/sort query or cross-object search |
| `record.delete` | DELETE | `/v2/objects/{object}/records/{record_id}` | Delete |
| `person.upsert` | PUT | `/v2/objects/people/records` (upsert) | Match on `email_addresses` (or configured unique attr) |
| `company.upsert` | PUT | `/v2/objects/companies/records` (upsert) | Match on `domains` |
| `list.create` | POST | `/v2/lists` | Create a list (`workspace_access` / member access rules apply) |
| `list.entry.create` | POST | `/v2/lists/{list}/entries` | Add a parent record to a list |
| `list.entry.update` | PATCH | `/v2/lists/{list}/entries/{entry_id}` | Update entry attributes |
| `list.entry.delete` | DELETE | `/v2/lists/{list}/entries/{entry_id}` | Remove from list |
| `list.entry.search` | POST | `/v2/lists/{list}/entries/query` | Filter/sort entries |
| `attribute.create` | POST | `/v2/objects/{object}/attributes` or `/v2/lists/{list}/attributes` | Create an attribute |
| `attribute.archive` | PATCH | `/v2/{objects\|lists}/{id}/attributes/{attribute}` | Set `is_archived: true` (no attribute DELETE in v2 index) |
| `selectOption.create` | POST | attribute options sub-resource | Add a select option |
| `task.create` | POST | `/v2/tasks` | Create a task |
| `task.update` | PATCH | `/v2/tasks/{task_id}` | `deadline_at`, `is_completed`, `linked_records`, `assignees` only |
| `trigger.listen` | POST `/v2/webhooks` + inbound | Create webhook subscriptions; receive events on ingress | Same node, not a second node |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `record.create` | no | Chooses the row above |
| `auth` | select | yes | `apiKey` | no | `apiKey` or `oauth2` |
| `workspaceToken` | password | if apiKey | | **yes** | Single-workspace access token (Bearer) |
| `clientId` | string | if oauth2 | | no | App id from https://build.attio.com |
| `clientSecret` | password | if oauth2 | | **yes** | App secret |
| `accessToken` | password | oauth2 runtime | | **yes** | Stored Bearer after code exchange |
| `scopes` | string | oauth2 | | no | Space-delimited scopes |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Attribute values as Attio typed objects |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = record.create | record.get | record.update | record.delete | record.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `object` | string | yes | Slug or id (`people`, `companies`, `deals`, custom) |
| `recordId` | string | get/update/delete | `record_id` |
| `values` | object | create/update | Attribute slug → Attio value arrays |
| `matchingAttribute` | string | no | Used only if implementer routes create to upsert |
| `filter` / `sorts` / `limit` / `offset` | mixed | search | Query body for `POST .../records/query` |
| `query` | string | fuzzy search | Text for `POST /v2/records/search` |
| `objects` | string[] | fuzzy search | Restrict objects |

### `operation = person.upsert | company.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `values` | object | yes | Person: typically `email_addresses`, `name`. Company: `domains`, `name` |
| `matchingAttribute` | string | no | Override unique attr (`email_addresses` / `domains`) |

`avatar_url` / `logo_url` cannot be set via API (official note).

### `operation = list.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | List name |
| `parentObject` | string | yes | Object slug the list holds |
| `workspaceAccess` | string | yes | Must be `full-access` **or** a member with `full-access` |

### `operation = list.entry.create | list.entry.update | list.entry.delete | list.entry.search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list` | string | yes | List id or slug |
| `entryId` | string | update/delete | `entry_id` |
| `parentRecordId` | string | create | Parent record |
| `parentObject` | string | create | Parent object |
| `entryValues` | object | no | List-specific attributes |
| `filter` / `sorts` | mixed | search | Query body |

### `operation = attribute.create | attribute.archive | selectOption.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | `object` or `list` id/slug |
| `apiSlug` / `title` / `type` | string | create | Attribute definition |
| `attributeId` | string | archive/option | Attribute id |
| `optionTitle` | string | selectOption.create | Option label |

### `operation = task.create | task.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `taskId` | string | update | Task id |
| `content` | string | create | Task body |
| `deadlineAt` | string | no | ISO datetime |
| `isCompleted` | boolean | no | Completion |
| `assignees` | string[] | no | Workspace member ids |
| `linkedRecords` | object[] | no | Record references |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `targetUrl` | string | when registering | Ingress URL (`POST /v2/webhooks`) |
| `subscriptions` | object[] | when registering | Event types (record.created, …) |
| *(inbound)* | object | listen | Webhook body; verify Attio signature per webhooks guide |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `resource` | object | CRUD | `{ data: { id, values, ... } }` |
| `data` | array | list/search | Records or entries |
| `event` | object | listen | Inbound webhook |
| `error` | object | 4xx/5xx | `{ code, message, status, type }` — no tokens, no emails |

## Legal

- Attio developer terms via https://attio.com and the developer platform. OAuth apps are registered at https://build.attio.com.
- Rate limits: **100 rps read**, **25 rps write**. `429` + `Retry-After`. List-records / list-entries also have **score-based** limits (per-query complexity and 10s sliding window).
- CRM PII (people, emails, notes, tasks). **(a)** Minimize attributes in `values`. **(b)** Customer owns the Attio workspace and is the controller. **(c)** StudioQuack does not accept Attio ToS on their behalf. **(d)** Never log Bearer tokens or attribute values.
- Official-API-first: `api.attio.com` only. SQL endpoint is Enterprise-only — out of this node’s catalog ops.

## Implementation notes

- JDK `HttpClient` only. `Authorization: Bearer`. `Content-Type: application/json`.
- Timeouts: connect 10s, read 30s. Body cap 1 MiB.
- Retry: 429/5xx, max 3, honor `Retry-After`. Separate read vs write token buckets (100 / 25 rps).
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Unique-attribute conflicts on `record.create` are 400-class — operators should use upsert ops.
- Webhook signatures: verify before dispatching `listen`. Registration is `trigger` behaviour on the same node.
