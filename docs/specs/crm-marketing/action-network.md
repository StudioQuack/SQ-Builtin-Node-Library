# Action Network

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/action-network/`
- **Researched:** 2026-08-20
- **Official API:** Action Network REST API v2 (OSDI 1.1.1 / HAL+JSON). Changelog on the v2 index includes a 2026-07-08 update (`tag_list` on messages and actions; oData on `advocacy_campaigns`; event/attendance `status` values).
- **Docs:** https://actionnetwork.org/docs (getting started) · https://actionnetwork.org/docs/v2 (principles) · AEP `https://actionnetwork.org/api/v2/`
- **Auth:** `api_key` — secret binding: `apiKey`

## Scope

One node for the Action Network advocacy CRM. Calls the official REST v2 HAL+JSON API at `https://actionnetwork.org/api/v2`. The operator supplies a **partner** API key generated per list (personal account or group) on **Start Organizing → API & Sync**. Version 1 is deprecated; this node uses v2 only.

`person.create` is **not** a collection POST of a bare person. Official docs: the people collection rejects a bare POST; create/upsert uses the **person signup helper** (`POST /api/v2/people/` with a `person` hash). Petitions/events use the matching **record_*_helper** endpoints. Tags cannot be PUT/DELETE. Webhooks are configured in the Action Network UI (and delivered as HAL+JSON POSTs); this node exposes `trigger.listen` for inbound events on the same node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `person.create` | POST | `/api/v2/people/` (person signup helper) | Create or update a person by email/phone; optional `add_tags` / `remove_tags` |
| `person.get` | GET | `/api/v2/people/{id}` | Fetch one person (UUID without `action_network:` prefix) |
| `person.list` | GET | `/api/v2/people` | Page the list tied to the API key (`page`, `per_page` max 25) |
| `person.update` | PUT | `/api/v2/people/{id}` | Update an existing person |
| `event.create` | POST | `/api/v2/events` | Create an event |
| `event.get` | GET | `/api/v2/events/{id}` | Fetch one event |
| `event.list` | GET | `/api/v2/events` | List events (optional `start_date` / `end_date` filters) |
| `attendance.create` | POST | `/api/v2/events/{eventId}/attendances` (record attendance helper) | RSVP a person to an event |
| `attendance.get` | GET | `/api/v2/events/{eventId}/attendances/{id}` | Fetch one attendance |
| `attendance.list` | GET | `/api/v2/events/{eventId}/attendances` | List RSVPs for an event |
| `petition.create` | POST | `/api/v2/petitions` | Create a petition |
| `petition.get` | GET | `/api/v2/petitions/{id}` | Fetch one petition |
| `petition.list` | GET | `/api/v2/petitions` | List petitions |
| `petition.update` | PUT | `/api/v2/petitions/{id}` | Update petition metadata |
| `signature.create` | POST | `/api/v2/petitions/{petitionId}/signatures` (record signature helper) | Record a signature + inline person |
| `signature.get` | GET | `/api/v2/petitions/{petitionId}/signatures/{id}` | Fetch one signature |
| `signature.list` | GET | `/api/v2/petitions/{petitionId}/signatures` | List signatures |
| `signature.update` | PUT | `/api/v2/petitions/{petitionId}/signatures/{id}` | Update signature comments/fields |
| `tag.create` | POST | `/api/v2/tags` | Create a tag (group keys only; deduped by `name`) |
| `tag.get` | GET | `/api/v2/tags/{id}` | Fetch one tag |
| `tag.list` | GET | `/api/v2/tags` | List tags (group keys only) |
| `personTag.add` | POST | `/api/v2/tags/{tagId}/taggings` | Attach a person to a tag |
| `personTag.remove` | DELETE | `/api/v2/people/{personId}/taggings/{taggingId}` | Remove a tagging |
| `trigger.listen` | inbound POST | StudioQuack message-event ingress | Receive Action Network webhooks (signatures, donations, subscribers, etc.) |

Retired: API v1 (`api-key` header). Current auth header is `OSDI-API-Token`. Helper POSTs may omit the key for browser forms; this daemon node **always** sends the key and never embeds it in client-side code.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `person.create` | no | Chooses the row above |
| `apiKey` | password | yes (except `listen` verify-only) | | **yes** | Partner API key. Header `OSDI-API-Token`. Scopes one list (user or group). |
| `eventType` | string | for `trigger.listen` | | no | Webhook kind configured in API & Sync (e.g. signature, donation, subscribe) |
| `verifyToken` | password | no | | **yes** | Optional shared secret the operator also puts on the ingress URL query; Action Network webhooks are not HMAC-signed |

The catalog’s `groupOsgi` is **not** an HTTP parameter. Group vs personal list is determined by **which key** was generated. Do not send a group id header.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, resource }` or `{ error }` | Failures never include the API key or PII values |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = person.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes unless `phone` | Person email (`person.email_addresses[0].address`) |
| `phone` | string | yes unless `email` | Mobile/other number |
| `givenName` | string | no | `person.given_name` |
| `familyName` | string | no | `person.family_name` |
| `postalCode` | string | no | `person.postal_addresses[0].postal_code` |
| `emailStatus` | string | no | `subscribed` or `unsubscribed` (only valid statuses on this helper) |
| `mobileStatus` | string | no | Mobile list status; new people default unsubscribed unless `subscribed` |
| `addTags` | string[] | no | Existing tag **names** to add |
| `removeTags` | string[] | no | Existing tag **names** to remove |
| `backgroundRequest` | boolean | no | `?background_request=true` for async processing |

### `operation = person.get | person.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `personId` | string | yes | UUID without `action_network:` prefix |
| `givenName`, `familyName`, `email`, `phone`, `customFields` | mixed | update only | Fields allowed on PUT people |

### `operation = person.list | event.list | petition.list | tag.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `page` | number | no | 1-based page |
| `perPage` | number | no | Max **25** (API hard cap) |
| `startDate` / `endDate` | string | no | Event list only (ISO date) |
| `filter` | string | no | oData `modified_date` filter when listing people |

### `operation = event.create | event.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `eventId` | string | get | Event UUID |
| `title` | string | create | Event title |
| `startDate` | string | no | Start datetime |
| `endDate` | string | no | End datetime |
| `browserUrl` | string | no | Public URL for API-created actions |
| `capacity` | number | no | Max RSVPs |

### `operation = attendance.create | attendance.get | attendance.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `eventId` | string | yes | Parent event |
| `attendanceId` | string | get | Attendance UUID |
| `email` / `phone` | string | create | Inline person (helper) |
| `givenName` / `familyName` | string | no | Inline person |
| `addTags` / `removeTags` | string[] | no | Tag names on the helper |

### `operation = petition.create | petition.get | petition.update | petition.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `petitionId` | string | get/update | Petition UUID |
| `title` | string | create | Petition title |
| `description` | string | no | Body |
| `browserUrl` | string | no | Public URL |

### `operation = signature.create | signature.get | signature.list | signature.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `petitionId` | string | yes | Parent petition |
| `signatureId` | string | get/update | Signature UUID |
| `email` / `phone` | string | create | Inline person |
| `comments` | string | no | Signature comment |
| `addTags` / `removeTags` | string[] | no | Tag names |
| `autoresponse` | boolean | no | Helper `triggers.autoresponse.enabled` |

### `operation = tag.create | tag.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | create | Tag name (required on POST) |
| `tagId` | string | get | Tag UUID |

### `operation = personTag.add | personTag.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `tagId` | string | add | Tag to attach |
| `personId` | string | yes | Person UUID |
| `taggingId` | string | remove | Tagging UUID to delete |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none on INPUT)* | | | Payload arrives from ingress, not from an upstream edge |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status (typically 200) |
| `resource` | object | CRUD success | HAL+JSON person, event, petition, signature, tag, attendance, or tagging |
| `identifiers` | string[] | person/action success | Includes `action_network:{uuid}` |
| `page` / `nextHref` | mixed | list | Pagination; people collections omit `total_pages` / `total_records` |
| `event` | object | `trigger.listen` | Inbound webhook body (signature/person hashes, `idempotency_key`) |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no token, no email/phone values |

## Legal

- ToS / developer: partner programme; keys from https://actionnetwork.org/docs — “API keys and webhooks are considered a partner feature.” Contact Action Network for partner access. The API is **not** for building a replacement UI.
- Rate limits: **4 requests per second**. Queue + exponential backoff. `per_page` max 25. Prefer oData `modified_date` over deep paging (later pages are slower).
- PII: people, emails, phones, postal addresses, petition comments, donation amounts, political/advocacy affiliation. **(a)** Send only fields the workflow needs. **(b)** The customer owns the Action Network account and is the controller of activist data. **(c)** StudioQuack does not accept Action Network’s terms on the customer’s behalf. **(d)** Never log `apiKey`, email, phone, address, or custom-field values — log status, ids, and sizes only.
- Official-API-first: call `actionnetwork.org/api/v2` only. No scrape of the organizer UI. Unauthenticated helper POST is for public forms, not this daemon node.

## Implementation notes

- JDK `HttpClient` only. `Content-Type: application/json`. Header `OSDI-API-Token`. Parse HAL `_links` / `_embedded`; do not use a vendor SDK.
- Timeouts: connect 10s, read 30s. Body cap 1 MiB. Strip `action_network:` if the operator pastes a prefixed identifier.
- Retry: 429 and 5xx, exponential backoff, max 3. Honor the 4 rps ceiling (token bucket). Optional `background_request=true` for heavy writes.
- Errors: HTTP 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch. Never return the API key.
- `trigger.listen`: operator pastes the StudioQuack ingress URL into API & Sync. Webhooks typically arrive within ~5 minutes; payloads include `idempotency_key` (since 2022-12-07) — treat delivery as at-least-once.
