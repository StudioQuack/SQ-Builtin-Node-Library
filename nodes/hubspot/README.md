# HubSpot

Official HubSpot CRM + **Marketing Hub** REST at `https://api.hubapi.com`. Handler: `OfficialApiNode` via `api_spec.json`. **One node** — lists, forms, transactional email, and workflow enroll live here (not a sibling Marketing node).

- **Docs:** https://developers.hubspot.com/docs/api-reference/overview
- **Auth overview:** https://developers.hubspot.com/docs/apps/developer-platform/build-apps/authentication/overview
- **Service Keys:** https://developers.hubspot.com/docs/apps/developer-platform/build-apps/authentication/account-service-keys (public beta 2026-02-10)
- **Base:** `https://api.hubapi.com`
- **API:** CRM object ops use date-based `/crm/objects/2026-03/...` (https://developers.hubspot.com/docs/api-reference/latest/overview). Lists **2026-03** (v1 Lists sunset **2026-04-30**); Marketing forms v3; transactional single-send v3; workflow enroll v2. https://developers.hubspot.com/changelog/introducing-date-based-api-versioning
- **Auth:** Bearer `access_token` — **Service Key** or private-app / static token. **Legacy `hapikey` is dead** — never put the key on the query string.

## Operations

CRM objects (`contacts`, `companies`, `deals`, `tickets`) plus notes for `engagement.*`. Lists use `/crm/lists/2026-03/{listId}/memberships/add-and-remove` (object body `recordIdsToAdd` / `recordIdsToRemove` — OfficialApiNode cannot POST a raw JSON array).

| operation | HTTP | Path |
|---|---|---|
| `contact.upsert` | POST | `/crm/objects/2026-03/contacts/batch/upsert` |
| `contact.get` | GET | `/crm/objects/2026-03/contacts/{contactId}` |
| `contact.list` / `contact.recent` | GET | `/crm/objects/2026-03/contacts` |
| `contact.search` | POST | `/crm/objects/2026-03/contacts/search` |
| `contact.delete` | DELETE | `/crm/objects/2026-03/contacts/{contactId}` |
| `list.add` / `list.remove` | PUT | `/crm/lists/2026-03/{listId}/memberships/add-and-remove` |
| `company.*` / `deal.*` / `ticket.*` | CRUD | `/crm/objects/2026-03/{companies\|deals\|tickets}` |
| `engagement.*` | CRUD | `/crm/objects/2026-03/notes` |
| `form.getFields` | GET | `/marketing/v3/forms/{formId}` |
| `form.submit` | POST | `https://api.hsforms.com/submissions/v3/integration/secure/submit/{portalId}/{formId}` (authenticated submit; needs INPUT `portalId` + `formId`, body `fields`, optional `context` / `legalConsentOptions`) |
| `email.send` | POST | `/marketing/v3/transactional/single-email/send` |
| `email.get` | GET | `/marketing/v3/emails/{emailId}` |
| `workflow.enroll` | POST | `/automation/v2/workflows/{workflowId}/enrollments/contacts/{email}` |
| `workflow.unenroll` | DELETE | same path |
| `trigger.listen` | n/a | **ingress** — HubSpot **webhooks need a project-based app** (CLI developer platform). Service Keys **cannot** subscribe to webhooks. Does not open a socket. |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (includes Marketing Hub) |
| `access_token` | password | yes | Service Key or private-app token (Bearer) |
| `timeout_seconds` | number | no | Default 25 |

## Create / update fields

Company, deal, and ticket writes accept purpose-oriented HubSpot property names (merged into `properties` via `bodyMap`). Keep `properties` for custom fields.

| Object | Purpose fields |
|---|---|
| company | `name`, `domain`, `city`, `industry`, `phone`, `state`, `website`, `lifecyclestage` |
| deal | `dealname`, `amount`, `dealstage`, `pipeline`, `closedate` |
| ticket | `subject`, `content`, `hs_pipeline`, `hs_pipeline_stage`, `hs_ticket_priority` |
| note (`engagement.create`) | `hs_note_body`, `hs_timestamp` |
| `email.send` | `to`, `from`, `cc`, `bcc`, `replyTo` → `message.*` |

`contact.upsert` maps `email` → `inputs[0].id` and `firstname`/`lastname`/`phone`/`company`/`website`/`lifecyclestage` → `inputs[0].properties.*`. Set `idProperty=email`. `properties` on create/update is the custom-field envelope (merged with purpose fields). On GET/search `properties` is the comma-separated property-name list.

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Developer terms: https://legal.hubspot.com/hs-developer-terms
- Customer owns the HubSpot account. Contacts, emails, and form fields are PII. Caller owns GDPR/CCPA consent and CAN-SPAM/CASL for sends.
- Official API only. Do not scrape the CRM UI.
