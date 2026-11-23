# HubSpot

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/hubspot/`
- **Researched:** 2026-08-20
- **Official API:** HubSpot CRM Objects **v3** (`/crm/v3/objects/{contacts|companies|deals|tickets}`) + Lists **`/crm/lists/2026-03`** + Forms submit **v3** + Transactional email **`/marketing/transactional/2026-03`** + Automation **v2 enroll** / **v4 flows**. Host `https://api.hubapi.com`. Forms POST also `https://api.hsforms.com`.
- **Docs:** https://developers.hubspot.com/docs/api-reference/crm-contacts-v3/guide · Lists https://developers.hubspot.com/docs/api-reference/latest/crm/lists/guide · Email https://developers.hubspot.com/docs/api-reference/latest/marketing/transactional-emails/guide · Webhooks https://developers.hubspot.com/docs/api-reference/latest/webhooks/guide
- **Auth:** Bearer **private-app access token** (single portal) **or** OAuth **public app** tokens (multi-portal). Secret binding: `access_token`. OAuth: `client_id`, `client_secret`, refresh token. **Legacy `hapikey` query is dead** (retired 2022). Do not send `?hapikey=`.

## Scope

**One node** for CRM + Marketing Hub surfaces in the catalog (lists, forms, transactional email, workflow enroll). Not a second “Marketing Hub” node.

**Auth choice:** Private app / static token = one HubSpot account, scopes picked in UI. OAuth public app = Marketplace / multi-account; webhooks are **app-level**.

**Webhooks (still true 2026-08-20):** a public app has **one** `targetUrl`. Subscriptions apply to every install. Private-app webhooks: **UI only** (not the Webhooks REST API). New developer-platform apps use project `webhooks` config or Webhooks Journal (`2026-03`) with client-credentials — out of default private-token path. `trigger.listen` = verify HubSpot signature on SQ ingress; do not expect per-workflow URLs on one public app.

**Lists v1 sunset 2026-04-30.** Use Lists `2026-03` memberships (`MANUAL` / `SNAPSHOT` only for add/remove).

**Do not build:** hapikey; v1 recently_updated lists; scraping app.hubspot.com; a separate Marketing Hub node.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `contact.upsert` | POST | `/crm/v3/objects/contacts/batch/upsert` | `idProperty` typically `email` |
| `contact.get` | GET | `/crm/v3/objects/contacts/{id}` | Query `properties` |
| `contact.list` | GET | `/crm/v3/objects/contacts` | `limit`, `after` |
| `contact.search` | POST | `/crm/v3/objects/contacts/search` | Filter groups |
| `contact.delete` | DELETE | `/crm/v3/objects/contacts/{id}` | |
| `contact.recent` | POST | `/crm/v3/objects/contacts/search` | Sort `lastmodifieddate` DESC (replaces dead v1 recent) |
| `list.add` | PUT | `/crm/lists/2026-03/{listId}/memberships/add` | Body: JSON array of record id strings. `MANUAL`/`SNAPSHOT` only |
| `list.remove` | PUT | `/crm/lists/2026-03/{listId}/memberships/remove` | Body: JSON array of record ids |
| `company.create` | POST | `/crm/v3/objects/companies` | |
| `company.get` / `list` / `search` / `update` / `delete` / `recent` | * | `/crm/v3/objects/companies` analog of contacts | |
| `deal.*` | * | `/crm/v3/objects/deals` | Same verb set as companies except no catalog `deal.upsert` |
| `engagement.create` | POST | `/crm/v3/objects/{notes\|calls\|emails\|meetings\|tasks}` | Legacy Engagements v1 retired — use CRM objects |
| `engagement.get` / `list` / `delete` | GET/GET/DELETE | `/crm/v3/objects/{type}/{id}` | |
| `ticket.create` / `get` / `list` / `update` / `delete` | * | `/crm/v3/objects/tickets` | |
| `form.getFields` | GET | `/forms/v2/forms/{form_guid}` | Field definitions (legacy forms v2 still listed) |
| `form.submit` | POST | `https://api.hsforms.com/submissions/v3/integration/secure/submit/{portalId}/{formGuid}` | Authenticated submit (higher limits than public CORS URL) |
| `email.send` | POST | `/marketing/transactional/2026-03/single-email/send` | Transactional add-on + `transactional-email` scope |
| `email.get` | GET | `/marketing/emails/2026-03/{emailId}` | Marketing Emails v3. Single-send poll uses transactional status API with `statusId` from `email.send` |
| `workflow.enroll` | POST | `/automation/v2/workflows/{workflowId}/enrollments/contacts/{email}` | Contact workflows; scope `automation` |
| `workflow.unenroll` | DELETE | `/automation/v2/workflows/{workflowId}/enrollments/contacts/{email}` | |
| `trigger.listen` | n/a | App/private-app webhook target = SQ ingress | CRM object events; HMAC v3 |

Company/deal/ticket verbs follow the same HTTP pattern as contacts (`create` = POST collection, `update` = PATCH `/{id}`). DYNAMIC lists reject membership add/remove.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | |
| `auth_type` | select | yes | `private_app` | no | `private_app` \| `oauth` |
| `access_token` | password | yes | empty | yes | Bearer |
| `client_id` / `client_secret` | string/password | oauth | | secret on secret | Refresh |
| `portal_id` | string | forms | empty | no | Hub ID for `form.submit` |
| `timeout_seconds` | number | no | `25` | no | HubSpot webhook timeout is **5 s** on ingress |

Scopes (operator must grant): `crm.objects.contacts.read/write`, companies, deals, tickets; `crm.lists.read/write`; `forms`; `automation`; `transactional-email` (add-on).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Properties maps are PII |
| `output` | OUTPUT | object or `{ error }` | Never echo Bearer |

FUNCTION ports: none.

## Input fields (by operation)

### CRM object writes (`*.create`, `*.update`, `contact.upsert`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update/delete/get | HubSpot record id |
| `id_property` | string | upsert no | Default `email` for contacts |
| `properties` | object | create/upsert/update yes | Property name → value (e.g. `email`, `firstname`) |
| `associations` | array | no | `{ to: { id }, types: [...] }` as CRM v3 |
| `inputs` | array | upsert yes | Batch upsert inputs if not using single `properties` shortcut (handler may wrap one input) |

### CRM reads (`*.get`, `*.list`, `*.search`, `*.recent`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | get/delete | |
| `properties` | array | no | Property names |
| `limit` | number | no | Max 100 |
| `after` | string | no | Paging cursor |
| `filter_groups` | array | search | CRM search filters |
| `query` | string | search no | |
| `sorts` | array | recent default | recent: `[{ "propertyName": "lastmodifieddate", "direction": "DESCENDING" }]` |

### `list.add` / `list.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `list_id` | string | yes | ILS list id |
| `record_ids` | array | yes | CRM record ids of the list’s `objectTypeId` |

### `engagement.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `engagement_type` | select | yes | `notes` \| `calls` \| `emails` \| `meetings` \| `tasks` |
| `properties` | object | yes | Type-specific (`hs_note_body`, `hs_timestamp`, …) |
| `associations` | array | no | Usually contact/deal ids |

### `form.getFields`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_guid` | string | yes | |

### `form.submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `form_guid` | string | yes | |
| `portal_id` | string | yes unless config | |
| `fields` | array | yes | `{ name, value }[]` |
| `context` | object | no | `hutk`, `ipAddress`, `pageUri` as documented |
| `legal_consent_options` | object | when form requires | GDPR consent payload |

### `email.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | number | yes | Published transactional email content id |
| `to` | string | yes | |
| `from` | string | no | `"Name <addr>"` |
| `send_id` | string | no | Dedup |
| `contact_properties` | object | no | Upserts contact |
| `custom_properties` | object | no | HubL `{{ custom.NAME }}` |

### `email.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_id` | string | one of | Path `/marketing/emails/2026-03/{emailId}` |
| `status_id` | string | one of | Poll transactional send status (guide: statusId from single-send response) |

### `workflow.enroll` / `workflow.unenroll`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `workflow_id` | string | yes | |
| `email` | string | yes | Contact email (v2 contact enrollments) |

### `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | array | yes | Batch of events (up to 100) |
| `signature` | string | yes | `X-HubSpot-Signature-v3` (or v1/v2 if documented for that app) |
| `timestamp` | string | v3 | `X-HubSpot-Request-Timestamp` |

## Output fields (by operation)

### CRM CRUD / search / recent / engagements / tickets

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | single | Record id |
| `properties` | object | 2xx | Requested properties |
| `results` | array | list/search/batch | |
| `paging` | object | list | `next.after` |
| `error` | string | failure | HubSpot `message` / `category` |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | `Retry-After` or 10 |

### `list.add` / `list.remove`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `record` | object | 2xx | Membership result body |

### `form.getFields`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `form` | object | 2xx | Name, `formFieldGroups`, etc. |

### `form.submit`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `inline_message` | string | when present | |
| `errors` | array | 400 | Field errors |

### `email.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `status_id` | string | 2xx | Poll `email.get` |
| `send_result` | string | 2xx | Provider status enum |

### `workflow.enroll` / `unenroll`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx/204 | `ok` |

### `trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | signatures ok | `ok` |
| `events` | array | | `{ eventId, subscriptionType, objectId, portalId, propertyName, propertyValue }` |
| `error` | string | bad HMAC | |

## Legal

- Contacts: https://developers.hubspot.com/docs/api-reference/crm-contacts-v3/guide
- Lists 2026-03 (v1 sunset 2026-04-30): https://developers.hubspot.com/docs/api-reference/latest/crm/lists/guide
- Transactional send: https://developers.hubspot.com/docs/api-reference/latest/marketing/transactional-emails/guide
- Marketing emails GET: https://developers.hubspot.com/docs/api-reference/latest/marketing/marketing-emails/guide
- Lists add: https://developers.hubspot.com/docs/api-reference/latest/crm/lists/memberships/add-to-list
- Webhooks: https://developers.hubspot.com/docs/api-reference/latest/webhooks/guide
- Private app webhooks: https://developers.hubspot.com/docs/apps/legacy-apps/private-apps/create-and-edit-webhook-subscriptions-in-private-apps
- Auth overview: https://developers.hubspot.com/docs/apps/developer-platform/build-apps/authentication/overview
- Developer Terms: https://legal.hubspot.com/hubspot-developer-terms
- Forms submit creates/updates **contacts** only. Transactional email bypasses subscription types — still CAN-SPAM identity rules. GDPR: operator is controller of CRM PII. Never log tokens or property bags.
- Typical private-app burst limits (~110/10s historically) — honor 429. Webhook POSTs do not count against REST burst.

## Implementation notes

- JDK HTTP. `Authorization: Bearer`. JSON. `Redirect.NEVER`. Timeout 25 s. Response cap 2 MiB.
- PATCH for CRM update (`/crm/v3/objects/{type}/{id}`). Upsert wraps a one-element `inputs` array when the workflow sends a single contact.
- `form.submit`: host `api.hsforms.com`, still Bearer on the **secure** path.
- Signature v3: HMAC-SHA256 over `method + uri + body + timestamp` per current webhook guide; reject timestamps > 5 minutes.
- 4xx/5xx → `{ error }` on OUTPUT. No inner retry. Missing `transactional-email` add-on → 403 `MISSING_SCOPES` mapped to `{ error }` (not a retry).
