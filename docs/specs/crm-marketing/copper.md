# Copper

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/copper/`
- **Researched:** 2026-08-20
- **Official API:** Copper Developer API v1 (`https://api.copper.com/developer_api/v1`)
- **Docs:** https://developer.copper.com/ · requests/auth: https://developer.copper.com/introduction/requests.html · OAuth: https://developer.copper.com/introduction/oauth/flow.html · users: https://developer.copper.com/account-and-users/list-users.html · customer sources: https://developer.copper.com/leads/list-customer-sources.html
- **Auth:** API token headers **or** OAuth 2.0 Bearer (`scope=developer/v1/all`) — secret bindings: `apiKey`, `clientSecret`, `accessToken`

## Scope

One node for Copper CRM (Google Workspace-native). People, companies, leads, opportunities, projects, tasks, customer sources, users, and webhooks. List operations use **POST search** (not GET collection). Host is `api.copper.com` — do **not** call `api.prosperworks.com` (redirects were scheduled to end). `trigger.listen` POSTs `/developer_api/v1/webhooks`. Professional/Business (or trial) required for API/webhooks per Copper help. JDK HTTP only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `company.create` | POST | `/developer_api/v1/companies` | Create company (`name` required; `email_domain` unique) |
| `company.get` | GET | `/developer_api/v1/companies/{id}` | Fetch |
| `company.list` | POST | `/developer_api/v1/companies/search` | Search/page |
| `company.update` | PUT | `/developer_api/v1/companies/{id}` | Update |
| `company.delete` | DELETE | `/developer_api/v1/companies/{id}` | Delete |
| `person.create` | POST | `/developer_api/v1/people` | Create person (`name` required; email unique) |
| `person.get` | GET | `/developer_api/v1/people/{id}` | Fetch |
| `person.list` | POST | `/developer_api/v1/people/search` | Search/page |
| `person.update` | PUT | `/developer_api/v1/people/{id}` | Update |
| `person.delete` | DELETE | `/developer_api/v1/people/{id}` | Delete |
| `lead.create` | POST | `/developer_api/v1/leads` | Create lead |
| `lead.get` | GET | `/developer_api/v1/leads/{id}` | Fetch |
| `lead.list` | POST | `/developer_api/v1/leads/search` | Search |
| `lead.update` | PUT | `/developer_api/v1/leads/{id}` | Update |
| `lead.delete` | DELETE | `/developer_api/v1/leads/{id}` | Delete |
| `opportunity.create` | POST | `/developer_api/v1/opportunities` | Create opportunity |
| `opportunity.get` | GET | `/developer_api/v1/opportunities/{id}` | Fetch |
| `opportunity.list` | POST | `/developer_api/v1/opportunities/search` | Search |
| `opportunity.update` | PUT | `/developer_api/v1/opportunities/{id}` | Update |
| `opportunity.delete` | DELETE | `/developer_api/v1/opportunities/{id}` | Delete |
| `project.create` | POST | `/developer_api/v1/projects` | Create project |
| `project.get` | GET | `/developer_api/v1/projects/{id}` | Fetch |
| `project.list` | POST | `/developer_api/v1/projects/search` | Search |
| `project.update` | PUT | `/developer_api/v1/projects/{id}` | Update |
| `project.delete` | DELETE | `/developer_api/v1/projects/{id}` | Delete |
| `task.create` | POST | `/developer_api/v1/tasks` | Create task |
| `task.get` | GET | `/developer_api/v1/tasks/{id}` | Fetch |
| `task.list` | POST | `/developer_api/v1/tasks/search` | Search |
| `task.update` | PUT | `/developer_api/v1/tasks/{id}` | Update |
| `task.delete` | DELETE | `/developer_api/v1/tasks/{id}` | Delete |
| `customerSource.list` | GET | `/developer_api/v1/customer_sources` | List lead/customer sources |
| `user.list` | POST | `/developer_api/v1/users/search` | List/search users |
| `trigger.listen` | POST | `/developer_api/v1/webhooks` | Subscribe `type` + `event` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `person.get` | no | Chooses the row above |
| `auth` | select | yes | `apiKey` | no | `apiKey` or `oauth2` |
| `apiKey` | password | if apiKey | | yes | `X-PW-AccessToken` |
| `userEmail` | string | if apiKey | | no | Token owner email (`X-PW-UserEmail`) — not a password, but treat as identifying |
| `clientId` | string | if oauth2 | | no | OAuth client id |
| `clientSecret` | password | if oauth2 | | yes | OAuth secret |
| `accessToken` | password | if oauth2 | | yes | Bearer |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

Always send `X-PW-Application: developer_api` and `Content-Type: application/json` on API-key calls. OAuth calls use `Authorization: Bearer` against the same `/developer_api/v1` paths.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo access token |

FUNCTION ports: none.

## Input fields (by operation)

### Create/update (company / person / lead / opportunity / project / task)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | number | update/get/delete | Copper numeric id |
| `name` | string | create (most types) | Display name |
| `emails` | object[] | person/lead | `{ email, category }` — unique per person |
| `emailDomain` | string | company | Unique domain key |
| `phoneNumbers` | object[] | no | `{ number, category }` |
| `address` | object | no | street/city/state/postal/country — minimize |
| `assigneeId` | number | no | Owner user id |
| `companyId` | number | person | Related company |
| `details` | string | no | Description |
| `tags` | string[] | no | Tags |
| `customFields` | object[] | no | `{ custom_field_definition_id, value }` |
| `pipelineId` / `pipelineStageId` | number | opportunity | Pipeline (names per current opportunity schema) |
| `monetaryValue` | number | opportunity | Amount |
| `dueDate` | number | task | Unix timestamp |

Send only fields present on that resource’s official property table. Do not invent extra keys.

### List/search (`*.list`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pageSize` | number | no | Default 20; Copper max typically 200 |
| `pageNumber` | number | no | Page |
| `sortBy` | string | no | Field name |
| `sortDirection` | string | no | `asc` / `desc` |
| `name` | string | no | Name filter |
| `assigneeIds` | number[] | no | Owners (`-2` = unassigned where documented) |
| `tags` | string[] | no | Tag filter |

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | HTTPS URL (required) |
| `type` | select | yes | `lead` / `person` / `company` / `opportunity` / `project` / `task` (help also documents `activity_log`) |
| `event` | select | yes | `new` / `update` / `delete` |
| `secret` | object | no | Key/value echoed on delivery for verification — store in secrets, do not log |
| `customFieldComputedValues` | boolean | no | Expand dropdown option ids |

Notifications may lag (Copper documents up to ~15 minutes). Account caps: 100 subscriptions; notification rate caps documented on webhook overview.

## Output fields (by operation)

### Create / get / update (any CRM object)

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | number | 200 | Record id |
| `record` | object | 200 | Full JSON — log id only |

### `operation = *.list` (search)

| Field | Type | When | Purpose |
|---|---|---|---|
| `records` | object[] | 200 | Search hits |

### `operation = *.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | Deleted |

### `operation = customerSource.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `customerSources` | object[] | 200 | `{ id, name }` |

### `operation = user.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `users` | object[] | 200 | `{ id, name, email }` — do not log emails |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `subscriptionId` | number | 200 | Webhook id |

Failures: `{ error }`.

## Legal

- Developer API: https://developer.copper.com/
- Webhooks: https://developer.copper.com/webhooks/create-new-subscription.html
- Change policy: linked from https://developer.copper.com/
- Rate limit: **180 requests/minute** rolling; bulk endpoints **3 rps**. HTTP 429.
- People names, emails, phones, and addresses are PII. The **customer** is the controller. StudioQuack does not accept Copper ToS.
- Webhook `target` must be HTTPS. Deliveries are not always retried — ingress must be reliable.

## Implementation notes

- JDK HTTP only. TLS 1.2+.
- Search POSTs carry JSON bodies; GET/DELETE have empty bodies.
- Timeouts 10s/30s; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. Duplicate person email or company domain → 4xx `{ error }` (unique-key conflict).
- Never log `X-PW-AccessToken`, Bearer tokens, or email arrays.
