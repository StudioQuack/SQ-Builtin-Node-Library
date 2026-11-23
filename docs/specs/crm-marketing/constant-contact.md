# Constant Contact

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/constant-contact/`
- **Researched:** 2026-08-20
- **Official API:** Constant Contact V3 REST (`https://api.cc.email/v3`)
- **Docs:** https://developer.constantcontact.com/api_guide/v3_technical_overview.html · reference: https://developer.constantcontact.com/api_reference/index.html
- **Auth:** OAuth 2.0 only (`Authorization: Bearer {access_token}`) — secret bindings: `clientSecret`, `accessToken`, `refreshToken`. API keys as a standalone auth method are **retired**.

## Scope

One node for Constant Contact V3: contacts, lists, list membership (bulk activity), email campaigns, schedule, and test-send. TLS 1.2+. JSON only. UUID resource ids.

**Webhooks:** V3 **partner webhooks** cover **billing/account** topics only (tier change, cancel, disable) — not contact subscribe or email open. `trigger.listen` therefore registers a **partner** subscription when the app is a Tech Partner; for a normal OAuth integration it returns `{ error }` explaining that contact/campaign delivery webhooks do not exist and the workflow should poll `contact.list` with `updated_after`. Do not scrape the UI.

V3 has **no transactional SMTP**. Catalog `email.send` maps to **test send** of a campaign activity. Production send is `campaign.schedule`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/v3/contacts` | Create contact (email and/or SMS channel) |
| `contact.get` | GET | `/v3/contacts/{contact_id}` | Fetch one |
| `contact.list` | GET | `/v3/contacts` | Filter/page (`status`, `updated_after`, `lists`, `limit`, `cursor`) |
| `contact.update` | PUT | `/v3/contacts/{contact_id}` | Full replace-style update per schema |
| `contact.delete` | DELETE | `/v3/contacts/{contact_id}` | Delete (V3 contact delete semantics) |
| `list.create` | POST | `/v3/contact_lists` | Create a list (max 1000 lists/account) |
| `list.add` | POST | `/v3/activities/add_list_memberships` | Bulk add contacts to lists (async activity) |
| `campaign.create` | POST | `/v3/emails` | Create email campaign |
| `campaign.get` | GET | `/v3/emails/{campaign_id}` | Campaign details |
| `campaign.schedule` | POST | `/v3/emails/activities/{campaign_activity_id}/schedules` | Schedule (or send-at) a campaign activity |
| `email.send` | POST | `/v3/emails/activities/{campaign_activity_id}/tests` | Test/preview send to specified addresses |
| `trigger.listen` | PUT | `/v3/partner/webhooks/subscriptions/{topic_id}` | Tech Partner billing topics only |

Dropped: V2 API-key auth.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.create` | no | Chooses the row above |
| `clientId` | string | yes | | no | V3 application (API key / client id) |
| `clientSecret` | password | yes | | yes | OAuth client secret |
| `accessToken` | password | yes | | yes | Bearer access token |
| `refreshToken` | password | no | | yes | Refresh token (server flow) |
| `include` | string | no | | no | Contact subresources (`custom_fields,list_memberships,…`) |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create` / `contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | update | UUID |
| `emailAddress` | string | usual | Primary email |
| `firstName` | string | no | Given name |
| `lastName` | string | no | Family name |
| `jobTitle` | string | no | Title |
| `companyName` | string | no | Company |
| `createSource` | string | create | e.g. `Account` / `Contact` per current schema |
| `listMemberships` | string[] | no | Up to 50 list UUIDs |
| `customFields` | object[] | no | `{ custom_field_id, value }` |
| `permissionToSend` | string | recommended | Explicit permission field if present on schema — customer’s CAN-SPAM duty |

Minimize custom fields; do not send government IDs.

### `operation = contact.get` / `contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | string | yes | UUID |

### `operation = contact.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `status` | string | no | e.g. `active`, `unsubscribed` |
| `updatedAfter` | string | no | ISO-8601 — use for polling instead of missing contact webhooks |
| `lists` | string | no | List UUID filter |
| `limit` | number | no | Page size |
| `cursor` | string | no | Next-page token from `_links.next` |

### `operation = list.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Unique list name |
| `description` | string | no | Description |

### `operation = list.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `source` | object | yes | Mutually exclusive source object per bulk-activity docs (contact ids, list ids, or import) |
| `listIds` | string[] | yes | Destination lists |

Poll `GET /v3/activities/{activity_id}` is **not** a catalog verb; if the POST returns an activity id, put it on output and let BPMN wait/retry via another `list.add` status check **or** a follow-up get using `activityId` on input of a documented optional poll — keep one extra input field:

| `activityId` | string | no | If set, GET `/v3/activities/{activity_id}` instead of POST (status poll) |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Campaign name |
| `emailCampaignActivities` | object[] | yes | Activity payload per current POST `/emails` schema (from, subject, html) |

### `operation = campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignId` | string | yes | Campaign UUID |

### `operation = campaign.schedule`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignActivityId` | string | yes | Activity UUID |
| `scheduledDate` | string | yes | ISO-8601 UTC send time |

Contact lists for the activity must already be set via `PUT /v3/emails/activities/{id}` (not a separate catalog verb). If lists are missing, the schedule call fails — surface `{ error }`.

### `operation = email.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaignActivityId` | string | yes | Activity UUID |
| `emailAddresses` | string[] | yes | Test recipients (personal addresses only) |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topicId` | number | yes | `1` tier up, `2` tier down, `3` cancelled, `4` disabled |
| `hookUri` | string | yes | HTTPS callback (≤512 chars) |

If the token is not a Tech Partner app, return `{ error: "V3 partner webhooks are billing-only and require a Tech Partner application. Contact/campaign events: poll contact.list with updated_after." }`.

## Output fields (by operation)

### `operation = contact.create` / `contact.get` / `contact.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `contactId` | string | 200/201 | UUID |
| `contact` | object | 200 | Contact (minimize `include`) |

### `operation = contact.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `contacts` | object[] | 200 | Page |
| `nextCursor` | string | if more | From `_links.next` |

### `operation = contact.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 204 | No JSON body |

### `operation = list.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `listId` | string | 201 | New list |

### `operation = list.add`

| Field | Type | When | Purpose |
|---|---|---|---|
| `activityId` | string | POST | Bulk activity id |
| `activityStatus` | string | poll | Job status |

### `operation = campaign.create` / `campaign.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `campaignId` | string | 200/201 | UUID |
| `campaign` | object | get | Details |

### `operation = campaign.schedule`

| Field | Type | When | Purpose |
|---|---|---|---|
| `schedule` | object | 201 | Schedule record |

### `operation = email.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 201 | Test queued |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `topicId` | number | 200 | Partner topic |
| `error` | string | non-partner | Billing webhooks only |

Failures: `{ error }` flattened from `error_key` / `error_message`.

## Legal

- Technical overview: https://developer.constantcontact.com/api_guide/v3_technical_overview.html
- Partner webhooks: https://developer.constantcontact.com/api_guide/partner_webhook_overview.html
- Lists: https://v3.developer.constantcontact.com/api_guide/lists_overview.html
- Physical address in account is required to send (CAN-SPAM footer). US CAN-SPAM and CASL/GDPR consent are the **customer’s** duty. StudioQuack does not accept Constant Contact ToS.
- Contacts and campaign HTML are PII/content. Minimize `include` subresources. Never log tokens or email lists.
- Rate limits: standard vs Tech Partner (higher). Honor 429.

## Implementation notes

- JDK HTTP only. Base `https://api.cc.email/v3`. Dates ISO-8601; API returns UTC.
- Refresh tokens via Constant Contact OAuth token endpoint when `401`; do not put refresh traffic on INPUT.
- Timeouts 10s/30s; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. Other 4xx → `{ error }`.
- File import endpoint `activities/contacts_file_import` needs unspecified Content-Type — **out of first cut** (no `file` catalog op). If added later, use type `file` (ref-id), never inline bytes.
