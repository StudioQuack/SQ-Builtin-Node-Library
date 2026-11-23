# Pipedrive

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/pipedrive/`
- **Researched:** 2026-08-20
- **Official API:** Pipedrive REST **v2** (stable for deals, persons, orgs, activities, products, search) + **v1** for notes, files, leads CRUD, webhooks. Webhooks **v2.0 default** since 2025-03-17; v1 webhooks deprecated through 2026.
- **Docs:** https://developers.pipedrive.com/docs/api/v1 · v2 overview https://pipedrive.readme.io/docs/pipedrive-api-v2 · v2 stable changelog https://developers.pipedrive.com/changelog/post/apiv2-endpoints-now-stable-improved-performance-lower-token-costs · OAuth https://pipedrive.readme.io/docs/marketplace-oauth-api · scopes https://pipedrive.readme.io/docs/marketplace-scopes-and-permissions-explanations · webhooks v2 https://pipedrive.readme.io/docs/guide-for-webhooks-v2 · `webhooks:full` https://developers.pipedrive.com/changelog/post/introducing-new-oauth-scopes-for-webhooks
- **Auth:** OAuth Bearer `access_token` minted outside StudioQuack.

## Scope

One CRM node for Pipedrive. Prefer **`/api/v2`** where the resource exists (deals, persons, organizations, activities, deal products, search). Keep **`/api/v1`** for notes, files, leads write, and webhook registration. Host: `https://api.pipedrive.com`. OAuth: `Authorization: Bearer` with an access token minted outside StudioQuack.

`trigger.listen` **requires OAuth scope `webhooks:full`** (or legacy `admin`). Personal API token webhooks are UI/API as documented for that user.

Do not scrape the Pipedrive web app.

## Operations (`operation` config values)

`{base}` = `https://api.pipedrive.com` with no trailing slash.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `activity.create` | POST | `/api/v2/activities` | Create activity |
| `activity.get` | GET | `/api/v2/activities/{id}` | Get |
| `activity.list` | GET | `/api/v2/activities` | List |
| `activity.update` | PATCH | `/api/v2/activities/{id}` | Update |
| `activity.delete` | DELETE | `/api/v2/activities/{id}` | Delete |
| `deal.create` | POST | `/api/v2/deals` | Create deal |
| `deal.get` | GET | `/api/v2/deals/{id}` | Get |
| `deal.list` | GET | `/api/v2/deals` | List |
| `deal.search` | GET | `/api/v2/deals/search` | Search |
| `deal.update` | PATCH | `/api/v2/deals/{id}` | Update |
| `deal.delete` | DELETE | `/api/v2/deals/{id}` | Delete |
| `deal.duplicate` | POST | `/api/v1/deals/{id}/duplicate` | Duplicate (v1 only — no v2 equivalent as of 2026-08; Pipedrive staff: keep v1 until v2 ships) |
| `deal.activity.list` | GET | `/api/v2/activities?deal_id=` | Activities for a deal |
| `dealProduct.add` | POST | `/api/v2/deals/{id}/products` | Attach product |
| `dealProduct.list` | GET | `/api/v2/deals/{id}/products` | List deal products |
| `dealProduct.update` | PATCH | `/api/v2/deals/{id}/products/{product_attachment_id}` | Update |
| `dealProduct.remove` | DELETE | `/api/v2/deals/{id}/products/{product_attachment_id}` | Remove |
| `person.create` | POST | `/api/v2/persons` | Create person |
| `person.get` | GET | `/api/v2/persons/{id}` | Get |
| `person.list` | GET | `/api/v2/persons` | List |
| `person.search` | GET | `/api/v2/persons/search` | Search |
| `person.update` | PATCH | `/api/v2/persons/{id}` | Update |
| `person.delete` | DELETE | `/api/v2/persons/{id}` | Delete |
| `org.create` | POST | `/api/v2/organizations` | Create org |
| `org.get` | GET | `/api/v2/organizations/{id}` | Get |
| `org.list` | GET | `/api/v2/organizations` | List |
| `org.search` | GET | `/api/v2/organizations/search` | Search |
| `org.update` | PATCH | `/api/v2/organizations/{id}` | Update |
| `org.delete` | DELETE | `/api/v2/organizations/{id}` | Delete |
| `lead.create` | POST | `/api/v1/leads` | Leads Inbox (v1) |
| `lead.get` | GET | `/api/v1/leads/{id}` | Get |
| `lead.list` | GET | `/api/v1/leads` | List |
| `lead.update` | PATCH | `/api/v1/leads/{id}` | Update lead (v1 PATCH) |
| `lead.delete` | DELETE | `/api/v1/leads/{id}` | Delete |
| `note.create` | POST | `/api/v1/notes` | Notes remain v1 |
| `note.get` | GET | `/api/v1/notes/{id}` | Get |
| `note.list` | GET | `/api/v1/notes` | List |
| `note.update` | PUT | `/api/v1/notes/{id}` | Update |
| `note.delete` | DELETE | `/api/v1/notes/{id}` | Delete |
| `file.create` | POST | `/api/v1/files` | Multipart; input type `file` |
| `file.get` | GET | `/api/v1/files/{id}` | Metadata |
| `file.download` | GET | `/api/v1/files/{id}/download` | Bytes → platform `file` ref |
| `file.delete` | DELETE | `/api/v1/files/{id}` | Delete |
| `product.list` | GET | `/api/v2/products` | Products |
| `trigger.listen` | POST | `/api/v1/webhooks` | Subscribe; body `version: "2.0"` |

OAuth scopes (minimum): `deals:full`, `contacts:full`, `activities:full`, `products:full` as needed; **`webhooks:full` for trigger.listen**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `deal.list` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Bearer minted outside StudioQuack |
| `timeout_seconds` | number | no | `30` | no | Downloads 60 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields; `file` for upload | No tokens |
| `output` | OUTPUT | `{ status, data, … }` or `{ error }`; `file` on download | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### Create/update (deal, person, org, activity, lead, note)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | get/update/delete | Entity id |
| `fields` | object | create/update | Pipedrive properties (`title`, `name`, `org_id`, `person_id`, `stage_id`, `content`, …). Custom fields keyed by hash |

Deal must have a person **or** org. Lead must have title + person or org.

### List/search

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Cap 500 (v2 cursor `cursor` / `limit`) |
| `cursor` | string | no | v2 pagination |
| `term` | string | search | Search term |
| `deal_id` / `person_id` / `org_id` | number | filter | When listing notes/files/activities |

### `dealProduct.*`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `deal_id` | number | yes | Deal |
| `product_id` | number | add | Product |
| `product_attachment_id` | number | update/remove | Attachment id |
| `item_price` / `quantity` | number | add | Line |

### `deal.duplicate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | Source deal id. POST `/api/v1/deals/{id}/duplicate` ([Deals](https://developers.pipedrive.com/docs/api/v1/Deals)). No v2 duplicate as of 2026-08 |

### `file.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform file ref |
| `deal_id` / `person_id` / `org_id` / `activity_id` | number | one | Association |

### `file.download`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | yes | File id |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `subscription_url` | string | yes | HTTPS ingress (no redirects; not a Pipedrive API URL) |
| `event_action` | string | yes | `create` \| `change` \| `delete` \| `*` |
| `event_object` | string | yes | `deal` \| `person` \| `organization` \| `activity` \| `lead` \| `*` \| … (v2 object list) |
| `version` | string | no | Default `2.0` |
| `name` | string | no | Webhook label |

Limit: **40 webhooks per user**. Ingress must accept Pipedrive v2 payload `meta.action` / `meta.entity`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object/array | success | v2 `data` (not v1 `success` wrapper only) |
| `additional_data` | object | list | Pagination |
| `id` | number | create | New entity id |
| `file` | file | `file.download` | Platform ref; size-capped |
| `webhook` | object | trigger.listen | id, events, url |
| `error` | string | failure | Pipedrive `error` / `error_info` |
| `http_status` | number | HTTP error | 401 missing `webhooks:full` on trigger |
| `retry_after_seconds` | number | 429 | Token-cost / rate limit |

## Legal

- Pipedrive Developer / Marketplace terms. Public apps use OAuth.
- CRM records are PII. Minimize fields. Never log tokens or note HTML.
- Customer owns the Pipedrive company. Webhook payloads go to operator-controlled ingress.
- Webhooks v1 sunset: subscribe **v2.0 only**.

## Implementation notes

- JDK `HttpClient` only. No Pipedrive SDK.
- Host is `https://api.pipedrive.com`. Access token is minted outside StudioQuack.
- v2 updates are **PATCH**, not PUT.
- `file.create`: `multipart/form-data`; file from ref-id, never inline bytes on the graph.
- `file.download`: stream to file store; cap **20 MiB** (ingress family); larger → `{ error }`.
- Body cap JSON **1_000_000** bytes. Timeout 30 s (60 s download).
- Logs: operation, entity type, id, HTTP status — never tokens, emails, or note content.
