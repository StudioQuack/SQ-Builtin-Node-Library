# Affinity

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/affinity/`
- **Researched:** 2026-08-20
- **Official API:** Affinity API **v2** dated revision **`2026-07-15`** (`Affinity-Version: 2026-07-15`) plus legacy **v1** at the same host for person/organization/list-entry **writes** that v2 does not yet cover. v1 is **not** deprecated (Affinity: “API V1 will not be deprecated at this time”). v2 is **not** at feature parity with v1.
- **Docs:** v2 intro https://developer.affinity.co/pages/external-api-v2/introduction · v2 persons https://developer.affinity.co/api-reference/2026-07-15/persons/get-all-persons · v1 https://api-docs.affinity.co/ · FAQs https://support.affinity.co/s/article/Getting-started-with-the-Affinity-API-faqs
- **Auth:** `api_key` — secret binding: `apiKey` (v2 Bearer; v1 Basic empty-user + key **or** Bearer)

## Scope

One node for Affinity relationship CRM. Base URL `https://api.affinity.co`. **Reads, search, and list-field writes** use v2 (`/v2/...`) with header `Affinity-Version: 2026-07-15`. **Person/organization CRUD and list-entry create/delete** use v1 paths (`/persons`, `/organizations`, `/lists/{id}/list-entries`) because v2 documents GET persons/companies and list-entry field PATCH, not POST `/v2/persons`. Available only on **select license types**. One API key per user; v1+v2 calls share monthly, per-minute, and concurrent limits.

Do not call undocumented scrape endpoints. Do not merge a different CRM into this node.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `person.create` | POST | `/persons` (v1) | Create a person (`first_name`, `last_name`, `emails`) |
| `person.get` | GET | `/v2/persons/{id}` | One person; pass `fieldIds` / `fieldTypes` for field data |
| `person.list` | GET | `/v2/persons` | Page persons (`cursor`, `limit` 1–100) |
| `person.update` | PUT | `/persons/{person_id}` (v1) | Partial update |
| `person.delete` | DELETE | `/persons/{person_id}` (v1) | Delete person |
| `organization.create` | POST | `/organizations` (v1) | Create an organization |
| `organization.get` | GET | `/v2/companies/{id}` | v2 companies = organizations |
| `organization.list` | GET | `/v2/companies` | Page companies |
| `organization.update` | PUT | `/organizations/{organization_id}` (v1) | Update organization |
| `organization.delete` | DELETE | `/organizations/{organization_id}` (v1) | Delete organization |
| `list.get` | GET | `/v2/lists/{listId}` or v1 `GET /lists/{list_id}` | One list |
| `list.list` | GET | `/v2/lists` or v1 `GET /lists` | All lists the key can see |
| `listEntry.create` | POST | `/lists/{list_id}/list-entries` (v1) | Add a person/org/opp to a list |
| `listEntry.get` | GET | `/v2/lists/{listId}/list-entries/{listEntryId}` | One list entry + fields |
| `listEntry.list` | GET | `/v2/lists/{listId}/list-entries` | Page entries; use `fieldIds` for values |
| `listEntry.delete` | DELETE | `/lists/{list_id}/list-entries/{list_entry_id}` (v1) | Remove from list |

v2 extras the implementer may use internally (not extra catalog verbs): `POST /v2/persons/search`, `POST /v2/companies/search`, `PATCH /v2/lists/{listId}/list-entries/{listEntryId}/fields` (up to 100 field updates).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `person.list` | no | Chooses the row above |
| `apiKey` | password | yes | | **yes** | v2: `Authorization: Bearer`. v1: Basic with **empty username** and key as password, or Bearer |
| `apiVersion` | string | no | `2026-07-15` | no | Sent as `Affinity-Version` on v2 calls |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo the key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = person.create | person.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `personId` | number | update/delete/get | Affinity person id |
| `firstName` | string | create | `first_name` |
| `lastName` | string | create | `last_name` |
| `emails` | string[] | create | Array; empty array allowed |
| `organizationIds` | number[] | no | Linked org ids |

### `operation = person.get | person.delete | person.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `personId` | number | get/delete | Person id |
| `cursor` / `limit` | mixed | list | Cursor page; `limit` max 100 |
| `ids` | number[] | list | Optional id filter |
| `fieldIds` / `fieldTypes` | string[] | get/list | Without these, v2 returns **no field data** |

### `operation = organization.create | organization.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organizationId` | number | update | Org id |
| `name` | string | create | Organization name |
| `domain` | string | no | Domain |
| `personIds` | number[] | no | Linked people |

### `operation = organization.get | organization.delete | organization.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `organizationId` | number | get/delete | Company id on v2 |
| `cursor` / `limit` / `fieldIds` / `fieldTypes` | mixed | list/get | Same field rules as persons |

### `operation = list.get | list.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | number | get | List id |
| `cursor` / `limit` | mixed | list | v2 pagination |

### `operation = listEntry.create | listEntry.get | listEntry.list | listEntry.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | number | yes | List |
| `listEntryId` | number | get/delete | Entry id |
| `entityId` | number | create | Person, org, or opportunity id (v1 `entity_id`) |
| `cursor` / `limit` / `fieldIds` | mixed | list | v2 list-entries |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `resource` | object | get/create/update | Person, company, list, or list-entry |
| `id` | number | writes | Affinity id |
| `data` / `pagination` | object | v2 lists | Cursor page (`next`/`prev` cursors) |
| `success` | boolean | v1 delete | `{ success: true }` |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no key, no emails |

## Legal

- API access is license-gated: https://support.affinity.co/hc/en-us/articles/5563700459533-Getting-started-with-the-Affinity-API-FAQs. Key generation: Settings → Manage Apps; role “Generate an API key”.
- v1 and v2 traffic **share** rate/quota. Honor `429`. Do not republish relationship-intelligence or interaction data outside the customer’s Affinity contract.
- Relationship CRM data (emails, interaction frequency, notes, list fields) is highly sensitive. **(a)** Minimize fields; never request field types the workflow does not need. **(b)** Customer owns the Affinity workspace and is the controller. **(c)** StudioQuack does not accept Affinity’s terms on their behalf. **(d)** Never log the API key, email addresses, or field values.
- Official-API-first: `api.affinity.co` only.

## Implementation notes

- JDK `HttpClient` only. v2: `Authorization: Bearer`, `Affinity-Version: 2026-07-15`, `Accept: application/json`. v1 writes: Basic `-u :$APIKEY` (empty username).
- Timeouts: connect 10s, read 30s. Body cap 1 MiB. v2 `limit` ≤ 100.
- Retry: 429/5xx, max 3, honor `Retry-After`. Shared v1+v2 quota — one limiter for the node.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Missing `fieldIds` is not an error; it returns empty fields — document that in handler logs as a shape, not as PII.
- Permissions: v2 GET persons requires “Export All People directory”; list field writes require “Export data from Lists”. Map 403 to `{ error }` with that hint, not a retry loop.
