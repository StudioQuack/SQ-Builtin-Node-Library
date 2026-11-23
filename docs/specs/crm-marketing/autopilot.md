# Autopilot

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/autopilot/`
- **Researched:** 2026-08-20
- **Official API:** **Autopilot REST API v1** at `https://api2.autopilothq.com/v1`. This is **not** the Ortto API (`https://api.ap3api.com/v1` and regional `api.au` / `api.eu` hosts). Autopilot rebranded to Ortto; both surfaces still exist. **This node must never call Ortto endpoints.**
- **Docs:** Autopilot developer getting started http://developers.autopilothq.com/getting-started/ · Autopilot API blueprint http://docs.autopilot.apiary.io/ · Autopilot-branded contact call still published by Ortto KB: https://help.ortto.com/a-449-autopilot-capture-a-form-via-api (`POST https://api2.autopilothq.com/v1/contact`, header `autopilotapikey`) · Autopilot API overview (Ortto KB, Autopilot-titled): https://help.ortto.com/a-376-autopilot-how-to-use-autopilots-api
- **Auth:** `api_key` — secret binding: `apiKey` (header `autopilotapikey`)

## Scope

One node for **legacy Autopilot Journeys REST**, for customers who still hold an Autopilot API key (Settings → Autopilot API, historically at `login.autopilothq.com`). Contacts upsert by email via `POST /v1/contact`. Custom fields use typed keys `string--Name`, `integer--Name`, `float--Name`, `boolean--Name`.

**Do not** use Ortto `X-Api-Key` / `/v1/people/get` / `/v1/person/merge`. If the operator only has an Ortto CDP key and no Autopilot key, this node must return `{ error }` telling them this node is Autopilot-only (a future Ortto node is a separate catalog row).

Autopilot-only is **not dead** as of 2026-08-20: `https://api2.autopilothq.com/v1/contacts` responds (HTTP 405 on HEAD, `Allow: GET, POST`). The Autopilot developer site still serves, and Ortto’s Autopilot KB still documents `api2.autopilothq.com`. New Ortto-native accounts should not use this node.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.upsert` | POST | `/v1/contact` | Create or update by `Email`; optional journey `trigger` on the same body |
| `contact.get` | GET | `/v1/contact/{emailOrId}` | Fetch one contact |
| `contact.list` | GET | `/v1/contacts` | Page contacts (`bookmark` cursor) |
| `contact.delete` | DELETE | `/v1/contact/{emailOrId}` | Delete a contact |
| `list.create` | POST | `/v1/list` | Create a static list |
| `list.list` | GET | `/v1/lists` | List all lists |
| `list.add` | POST | `/v1/list/{listId}/contact/{contactId}` | Add contact to list |
| `list.remove` | DELETE | `/v1/list/{listId}/contact/{contactId}` | Remove from list |
| `list.contains` | GET | `/v1/list/{listId}/contact/{contactId}` | Membership check (404 → not on list) |
| `list.contacts` | GET | `/v1/list/{listId}/contacts` | Contacts on a list |
| `journey.add` | POST | `/v1/trigger/{triggerId}/contact/{contactId}` | Enroll in a journey trigger |
| `trigger.listen` | inbound POST | StudioQuack ingress | Journey “webhook” actions the operator points at ingress |

List/trigger paths match the Autopilot v1 REST shape documented from the Autopilot API site (Apiary). Implementers must confirm each path against http://docs.autopilot.apiary.io/ at build time if the blueprint host is slow; **do not substitute Ortto paths**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.upsert` | no | Chooses the row above |
| `apiKey` | password | yes | | **yes** | Header `autopilotapikey` |

No Ortto region selector. Host is always `https://api2.autopilothq.com`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo `autopilotapikey` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.upsert`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | `contact.Email` |
| `firstName` / `lastName` | string | no | `FirstName` / `LastName` |
| `custom` | object | no | Map of `string--Field` → value (or unprefixed names the handler prefixes) |
| `sessionId` | string | no | `_autopilot_session_id` for form association (server-side only) |
| `triggerId` | string | no | Optional journey trigger on the upsert body |

### `operation = contact.get | contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | Email or Autopilot contact id |

### `operation = contact.list | list.contacts`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bookmark` | string | no | Pagination cursor |
| `listId` | string | list.contacts | List id |

### `operation = list.create | list.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | create | List title |

### `operation = list.add | list.remove | list.contains`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `listId` | string | yes | List id |
| `contactId` | string | yes | Email or contact id |

### `operation = journey.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `triggerId` | string | yes | Journey API trigger id |
| `contactId` | string | yes | Email or contact id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none on INPUT)* | | | Inbound journey webhook POST |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP 200 |
| `resource` | object | CRUD | Contact or list JSON |
| `contactId` | string | upsert/get | Autopilot id when returned |
| `exists` | boolean | `list.contains` | true on 200, false on 404 |
| `lists` / `contacts` | array | list ops | Collections |
| `bookmark` | string | paged list | Next cursor |
| `event` | object | listen | Webhook body |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no key, no email |

## Legal

- Autopilot API terms follow the Autopilot/Ortto account the key belongs to. Ortto ToS (https://ortto.com/terms-of-service/) applies to unified accounts; this node still only calls **Autopilot REST**, not Ortto CDP.
- Rate limits are **not published** on developers.autopilothq.com. Honor `429`. Do not copy Ortto API rate-limit numbers onto this node.
- Marketing contacts are PII. **(a)** Minimize fields. **(b)** Customer owns the Autopilot/Journeys account and is the controller. **(c)** StudioQuack does not accept Autopilot or Ortto ToS on their behalf. **(d)** Never log `apiKey`, emails, or custom field values.
- Official-API-first: `api2.autopilothq.com/v1` only. No UI scrape. **No Ortto API merge.**

## Implementation notes

- JDK `HttpClient` only. Headers: `autopilotapikey`, `Content-Type: application/json`.
- Timeouts: connect 10s, read 30s. Body cap 1 MiB.
- Retry: 429/5xx, max 3. Treat unknown 404 on `list.contains` as `{ exists: false }`, not as a thrown error.
- Errors: other 4xx/5xx → `{ error }` on OUTPUT. If the host is unreachable or returns HTML login for Ortto, fail with a message that Ortto CDP keys are the wrong product.
- `trigger.listen`: Autopilot has journey webhook *actions*, not a global REST webhook catalog. Operator pastes the StudioQuack ingress URL into the journey. There is no REST “create webhook” on this API.
