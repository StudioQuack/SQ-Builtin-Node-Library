# Close

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/close/`
- **Researched:** 2026-08-20
- **Official API:** Close REST API v1 (`https://api.close.com/api/v1`)
- **Docs:** https://developer.close.com/api/overview
- **Auth:** `api_key` HTTP Basic (key as username, empty password) **or** OAuth 2.0 — secret bindings: `apiKey`, `clientSecret`, `accessToken`

## Scope

One node for Close CRM: leads (companies), contacts (people), opportunities, logged activities (call / email / SMS), tasks, custom activities, custom objects, org memberships, and webhooks. `trigger.listen` creates a Close webhook subscription whose URL is the StudioQuack message-event ingress. Call **recordings and activity bodies are PII** — never log them. JDK HTTP only; experimental OpenAPI at https://api.close.com/api/openapi.json is incomplete — implement from the HTML reference.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `lead.create` | POST | `/api/v1/lead/` | Create a lead (company) |
| `lead.get` | GET | `/api/v1/lead/{id}/` | Fetch one lead |
| `lead.list` | GET | `/api/v1/lead/` | List/filter (`_skip`, `_limit`, `_fields`) |
| `lead.update` | PUT | `/api/v1/lead/{id}/` | Partial update |
| `lead.delete` | DELETE | `/api/v1/lead/{id}/` | Delete |
| `contact.create` | POST | `/api/v1/contact/` | Person on a lead |
| `contact.get` | GET | `/api/v1/contact/{id}/` | Fetch contact |
| `contact.list` | GET | `/api/v1/contact/` | List contacts |
| `contact.update` | PUT | `/api/v1/contact/{id}/` | Partial update |
| `opportunity.create` | POST | `/api/v1/opportunity/` | Deal on a lead |
| `opportunity.get` | GET | `/api/v1/opportunity/{id}/` | Fetch |
| `opportunity.list` | GET | `/api/v1/opportunity/` | List |
| `opportunity.update` | PUT | `/api/v1/opportunity/{id}/` | Partial update |
| `call.create` | POST | `/api/v1/activity/call/` | Log an external call |
| `call.get` | GET | `/api/v1/activity/call/{id}/` | Fetch call activity |
| `email.create` | POST | `/api/v1/activity/email/` | Create/send email activity |
| `sms.create` | POST | `/api/v1/activity/sms/` | Create SMS activity |
| `task.create` | POST | `/api/v1/task/` | Create task |
| `task.get` | GET | `/api/v1/task/{id}/` | Fetch task |
| `activity.list` | GET | `/api/v1/activity/` | Mixed activity feed (`_type`, lead filter) |
| `customActivity.create` | POST | `/api/v1/activity/custom/` | Instance of a custom activity type |
| `customObject.create` | POST | `/api/v1/custom_object/` | Custom object instance |
| `customObject.update` | PUT | `/api/v1/custom_object/{id}/` | Update instance |
| `membership.add` | POST | `/api/v1/membership/` | Add user to org (**OAuth + Manage Organization**; not API-key) |
| `membership.remove` | DELETE | `/api/v1/membership/{id}/` | Remove membership |
| `trigger.listen` | POST | `/api/v1/webhook/` | Subscribe to event-log actions |

Trailing slashes are required. PUT is patch-style (send only changed fields).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `lead.get` | no | Chooses the row above |
| `auth` | select | yes | `apiKey` | no | `apiKey` or `oauth2` |
| `apiKey` | password | if apiKey | | yes | Close API key (Basic username) |
| `clientId` | string | if oauth2 | | no | OAuth app id |
| `clientSecret` | password | if oauth2 | | yes | OAuth secret |
| `accessToken` | password | if oauth2 | | yes | Bearer token |
| `fields` | string | no | | no | `_fields` projection (comma-separated); prefer this to cut PII |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never include API key or `signature_key` after first store |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = lead.create` / `lead.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update | `lead_…` |
| `name` | string | create | Lead/company name |
| `url` | string | no | Website |
| `description` | string | no | Notes |
| `statusId` | string | no | Lead status |
| `contacts` | object[] | create optional | Nested contacts (emails/phones) — minimize |

### `operation = lead.get` / `lead.delete` / `contact.get` / `call.get` / `task.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Resource id |

### `operation = lead.list` / `contact.list` / `opportunity.list` / `activity.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | no | Search/filter (query or JSON body per Close filter docs) |
| `skip` | number | no | `_skip` |
| `limit` | number | no | `_limit` (respect per-resource max) |
| `leadId` | string | no | Scope activities/contacts |

### `operation = contact.create` / `contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update | `cont_…` |
| `leadId` | string | create | Parent lead |
| `name` | string | no | Person name |
| `emails` | object[] | no | `{ email, type }` |
| `phones` | object[] | no | `{ phone, type }` |
| `title` | string | no | Job title |

### `operation = opportunity.create` / `opportunity.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update | `oppo_…` |
| `leadId` | string | create | Parent lead |
| `statusId` | string | no | Pipeline status |
| `value` | number | no | Amount (minor units per Close) |
| `valuePeriod` | string | no | e.g. `one_time` |
| `note` | string | no | Internal note |

### `operation = call.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `leadId` | string | yes | Lead |
| `direction` | string | yes | `inbound` / `outbound` |
| `duration` | number | no | Seconds |
| `note` | string | no | Disposition note — do not log |
| `phone` | string | no | Number (PII) |

Do **not** fetch or store call recording URLs in logs.

### `operation = email.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `leadId` | string | yes | Lead |
| `contactId` | string | no | Contact |
| `status` | string | yes | e.g. `draft` / `outbox` / `sent` per current Emails reference |
| `subject` | string | no | Subject |
| `bodyHtml` | string | no | Body (PII) |
| `to` | object[] | no | Recipients |

### `operation = sms.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `leadId` | string | yes | Lead |
| `text` | string | yes | SMS body (PII) |
| `status` | string | yes | Per current SMS reference |
| `localPhone` | string | no | From number |

### `operation = task.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `leadId` | string | yes | Lead |
| `text` | string | yes | Task text |
| `date` | string | no | Due date |
| `assignedTo` | string | no | `user_…` |

### `operation = customActivity.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `leadId` | string | yes | Lead |
| `customActivityTypeId` | string | yes | Type id |
| `fields` | object | no | Custom field map |

### `operation = customObject.create` / `customObject.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | update | Instance id |
| `customObjectType` | string | create | Type |
| `leadId` | string | no | Association |
| `fields` | object | no | Values |

### `operation = membership.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | User email |
| `roleId` | string | yes | `admin` / `superuser` / `user` / `restricteduser` or role id |

### `operation = membership.remove`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Membership id |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | HTTPS ingress |
| `events` | object[] | yes | `{ object_type, action }` pairs from the event-type list |
| `verifySsl` | boolean | no | Default true |

Store `signature_key` from the create response in the daemon secret store; never return it on later outputs.

## Output fields (by operation)

### `operation = lead.create` / `lead.get` / `lead.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | `lead_…` |
| `data` | object | 200 | Lead (`_fields` applied) |

### `operation = lead.list` / `contact.list` / `opportunity.list` / `activity.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `dataList` | object[] | 200 | Page |
| `hasMore` | boolean | if present | Pagination |

### `operation = lead.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | Deleted |

### `operation = contact.create` / `contact.get` / `contact.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | `cont_…` |
| `data` | object | 200 | Contact |

### `operation = opportunity.create` / `opportunity.get` / `opportunity.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | `oppo_…` |
| `data` | object | 200 | Opportunity |

### `operation = call.create` / `call.get` / `email.create` / `sms.create` / `task.create` / `task.get` / `customActivity.create`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | Activity/task id |
| `data` | object | 200 | Resource — do not log bodies/recordings |

### `operation = customObject.create` / `customObject.update`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | Instance id |
| `data` | object | 200 | Instance |

### `operation = membership.add` / `membership.remove`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | string | 200 | Membership id |
| `error` | string | 403 | If API key used: OAuth required |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `subscriptionId` | string | 201 | `whsub_…` |

Store `signature_key` in the secret store; never return it on later outputs. Failures: `{ error }`.

## Legal

- Docs: https://developer.close.com/api/overview · webhooks: https://developer.close.com/api/resources/webhooks
- Rate limits: https://developer.close.com/api/overview/rate-limits — per endpoint-group, per API key, org cap 3× key. Honor `RateLimit` / `Retry-After`. Max **40** webhook subscriptions per org (higher only for Close-listed automation platforms — do not special-case those).
- Leads, contacts, emails, SMS, and call recordings are PII. The **customer** is the controller. StudioQuack does not accept Close ToS.
- Verify webhook HMAC (`close-sig-hash` / `close-sig-timestamp`) on ingress. Membership create is OAuth-only.

## Implementation notes

- JDK HTTP only. Basic `apiKey:` or `Authorization: Bearer`. JSON `Content-Type`.
- Pagination: `_skip`/`_limit` with documented skip ceilings — for large exports use date windows, not deep skip.
- Timeouts 10s/30s; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3 using `rate_reset`. Other 4xx → `{ error }`.
- Never log tokens, email bodies, SMS text, or recording URLs.
