# Jira

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/jira/`
- **Researched:** 2026-08-20
- **Official API:** Jira Cloud platform REST API **v3** (`/rest/api/3/`). Jira Software Agile REST **1.0** for `sprint_move` only (`/rest/agile/1.0/`).
- **Docs:** https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro
- **Auth:** `oauth2` (3LO) | `basic` (email + API token) — secret binding: `apiToken` / `accessToken`

## Scope

One node for Jira Cloud issues, comments, attachments (type `file`), users, Jira Software sprint move, and webhook `listen`. Jira **Data Center** is allowed as a **secondary** target via `baseUrl` (REST 2/3 as the server documents). Do not scrape HTML. Do not use the **legacy** JQL search resource `GET/POST /rest/api/3/search` (removed on Cloud). Use **enhanced search** `POST /rest/api/3/search/jql`.

`user_create` remains **legacy-retained**: Atlassian still documents `POST /rest/api/3/user` as kept for compatibility and slated for deprecation; keep the verb with that warning. Prefer Atlassian admin user-provisioning outside this node for new orgs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `issue_create` | POST | `/rest/api/3/issue` | Create an issue |
| `issue_get` | GET | `/rest/api/3/issue/{issueIdOrKey}` | Get one issue |
| `issue_update` | PUT | `/rest/api/3/issue/{issueIdOrKey}` | Update fields |
| `issue_delete` | DELETE | `/rest/api/3/issue/{issueIdOrKey}` | Delete an issue |
| `issue_search` | POST | `/rest/api/3/search/jql` | JQL search (enhanced; `nextPageToken`) |
| `issue_transition` | POST | `/rest/api/3/issue/{issueIdOrKey}/transitions` | Transition workflow |
| `comment_add` | POST | `/rest/api/3/issue/{issueIdOrKey}/comment` | Add comment (ADF body on v3) |
| `comment_get` | GET | `/rest/api/3/issue/{issueIdOrKey}/comment/{id}` | Get one comment |
| `comment_update` | PUT | `/rest/api/3/issue/{issueIdOrKey}/comment/{id}` | Update comment |
| `attachment_add` | POST | `/rest/api/3/issue/{issueIdOrKey}/attachments` | Upload file (multipart; type `file`) |
| `attachment_get` | GET | `/rest/api/3/attachment/content/{id}` | Download attachment → type `file` |
| `user_get` | GET | `/rest/api/3/user` | Get user by `accountId` |
| `user_create` | POST | `/rest/api/3/user` | **Legacy** create user (Administer Jira; not OAuth-app friendly) |
| `sprint_move` | POST | `/rest/agile/1.0/sprint/{sprintId}/issue` | Move issues into a sprint |
| `listen` | POST | `/rest/webhooks/1.0/webhook` (basic/other integrations) or `/rest/api/3/webhook` (OAuth 3LO apps) | Register webhook; inbound maps onto `output` |

Dropped: `GET/POST /rest/api/3/search` and `/searchId`. Do not scrape issue HTML.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `issue_get` | no | Chooses the row above |
| `site` | string | Cloud yes | | no | `https://{site}.atlassian.net` or full origin |
| `cloudId` | string | when `oauth2` | | no | Cloud id for `https://api.atlassian.com/ex/jira/{cloudId}` |
| `authType` | select | yes | `basic` | no | `basic` email+apiToken; `oauth2` 3LO Bearer |
| `email` | string | when `basic` | | no | Atlassian account email |
| `apiToken` | password | when `basic` | | yes | Atlassian API token |
| `accessToken` | password | when `oauth2` | | yes | OAuth access token |
| `baseUrl` | string | no | | no | Data Center origin (e.g. `https://jira.example.com`). When set, Cloud `site`/`cloudId` ignored |
| `timeoutSeconds` | number | no | `30` | no | Connect + request timeout |
| `insecureTls` | boolean | no | `false` | no | Lab-only for Data Center TLS |
| `webhookSecret` | password | no | | yes | HMAC secret for `X-Hub-Signature` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No API tokens |
| `output` | OUTPUT | object — `{ status: "ok", … }` or `{ error }` | Never secrets |

FUNCTION ports: none. Attachments: type `file` on input and output.

## Input fields (by operation)

### `operation = issue_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `projectKey` | string | yes | Project key |
| `summary` | string | yes | Summary |
| `issuetype` | string | yes | Type name or id |
| `description` | string | no | Plain text; handler wraps ADF on Cloud v3 |
| `fields` | object | no | Extra fields (priority, labels, customfield_*) |

### `operation = issue_get` / `issue_update` / `issue_delete` / `issue_transition`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issueIdOrKey` | string | yes | e.g. `PROJ-1` |
| `fields` | object | yes | `issue_update` field map |
| `transitionId` | string | yes | `issue_transition` |
| `comment` | string | no | Optional transition comment |
| `deleteSubtasks` | boolean | no | `issue_delete` query |

### `operation = issue_search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `jql` | string | yes | JQL |
| `maxResults` | number | no | Page size (Cloud enhanced search documents up to 5000; still cap reasonably, default 50, hard cap 100 unless operator raises) |
| `nextPageToken` | string | no | Cursor — **not** `startAt` |
| `fields` | array | no | Field list |
| `reconcileIssues` | array | no | Ids for read-after-write |

Cloud: **POST** `/rest/api/3/search/jql` JSON body. Data Center (`baseUrl`): if `/search/jql` returns 404, fall back to `POST /rest/api/2/search` with `startAt` — mark logs `dc_search_fallback`. Never call Cloud `/rest/api/3/search`.

### `operation = comment_add` / `comment_get` / `comment_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issueIdOrKey` | string | yes | Issue |
| `commentId` | string | yes | get/update |
| `body` | string | yes | add/update — wrap as ADF on Cloud v3 |

### `operation = attachment_add` / `attachment_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issueIdOrKey` | string | yes | `attachment_add` |
| `file` | file | yes | `attachment_add` platform file list `[{kind, mime_type, source, name, data}]`; one multipart part per item |
| `filename` | string | no | Override name |
| `attachmentId` | string | yes | `attachment_get` |

`attachment_add` requires header `X-Atlassian-Token: no-check` and `multipart/form-data`.

### `operation = user_get` / `user_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accountId` | string | yes | `user_get` |
| `emailAddress` | string | yes | `user_create` |
| `displayName` | string | no | `user_create` |
| `products` | array | no | `user_create` product access |

`user_create`: fail closed with a clear `{ error }` if the site returns 410/404 (resource removed). Document that Connect/Forge cannot call it; basic org-admin only.

### `operation = sprint_move`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `sprintId` | number | yes | Target sprint |
| `issues` | array | yes | Issue keys/ids |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | string | no | `register` or `map` |
| `ingressUrl` | string | yes when `register` | Message-event ingress |
| `name` | string | yes | Webhook name |
| `events` | array | no | Default `jira:issue_created`, `jira:issue_updated`, `comment_created` |
| `jqlFilter` | string | no | Issue-related JQL filter |
| `secret` | password | no | Prefer `webhookSecret`; required for HMAC |

OAuth apps: `POST /rest/api/3/webhook` (dynamic; refresh before ~30 days). Other integrations: `POST /rest/webhooks/1.0/webhook`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `error` | string | failure | Never tokens |
| `httpStatus` | number | HTTP error | Status |
| `issue` | object | issue mutate/get | `id`, `key`, `self`, `fields.summary`, `fields.status.name` |
| `issues` | array | `issue_search` | Issue summaries |
| `nextPageToken` | string | search | Next page; omit if last |
| `comment` | object | comment ops | `id`, `author.accountId`, `body` (ADF or text) |
| `attachment` | object | `attachment_add` | `id`, `filename`, `mimeType`, `size` |
| `file` | file | `attachment_get` | Downloaded content ref |
| `user` | object | user ops | `accountId`, `displayName`, `emailAddress` if permitted |
| `sprintId` | number | `sprint_move` | Echo |
| `webhook` | object | `listen` register | `id`, `url` (no secret), `events` |
| `webhookEvent` | string | inbound | e.g. `jira:issue_created` |
| `issueKey` | string | inbound | Issue key if present |
| `payload` | object | inbound | Sanitized JSON |

## Legal

- [Atlassian Cloud Terms](https://www.atlassian.com/legal/cloud-terms-of-service), [Developer Terms](https://developer.atlassian.com/platform/marketplace/atlassian-developer-terms/)
- REST v3 intro: https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro
- Enhanced JQL: https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-search/ — Cloud KB: legacy `/rest/api/3/search` **removed**; use `/rest/api/3/search/jql` — https://confluence.atlassian.com/jirakb/run-jql-search-query-using-jira-cloud-rest-api-1289424308.html
- Users (legacy create): https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-users/
- Webhooks: https://developer.atlassian.com/cloud/jira/platform/webhooks/
- Agile sprint: https://developer.atlassian.com/cloud/jira/software/rest/api-group-sprint/
- Rate limits: Jira Cloud uses **cost-based** rate limiting. Headers `X-RateLimit-NearLimit` when approaching; HTTP **429** with `Retry-After`. Retry 429/5xx only. Pagination: enhanced search uses `nextPageToken` (no random `startAt` access).
- Attachments and issue fields may contain PII. Operator is the site admin / token owner.
- Official-API-first. No issue navigator HTML scrape.

## Implementation notes

- JDK HTTP only. No Atlassian Java SDK.
- Cloud basic: `https://{site}.atlassian.net` + `Authorization: Basic base64(email:apiToken)`. Cloud OAuth: `https://api.atlassian.com/ex/jira/{cloudId}` + `Authorization: Bearer`. Data Center: `baseUrl` + same basic or PAT as the server documents.
- v3 descriptions/comments: send Atlassian Document Format (`type: doc` …). Accept plain string on input and wrap a single paragraph ADF.
- Timeouts 30s. Cap **8 MiB**. `attachment_get` writes to file store. `attachment_add` reads the platform file list (ContentBlocks) and emits one multipart part per item.
- Retry 429/5xx + jitter; honor `Retry-After`. No retry other 4xx.
- TLS verify on; `insecureTls` lab-only for Data Center.
- Success `{ status: "ok", … }`; `{ error }` otherwise. Never throw past dispatch. Never log tokens.
- `listen`: register pointing at StudioQuack message-event ingress. Verify inbound `X-Hub-Signature: sha256={hex}` HMAC of raw body (Atlassian test vector: secret `It's a Secret to Everybody`, payload `Hello World!` → `a4771c39fbe90f317c7824e83ddef3caae9cb3d976c214ace1f2937e133263c9`). Reject unsigned. Map onto `output` and BPMN message correlation — not a second node.
