# Linear

- **Status:** planned
- **Catalog:** `docs/catalog/productivity-collaboration.md` (also indexed under developer-tools — **one node**, this file is canonical)
- **Folder when built:** `nodes/linear/`
- **Researched:** 2026-08-20
- **Official API:** Linear GraphQL API (single unversioned schema) at `https://api.linear.app/graphql`
- **Docs:** https://developers.linear.app/docs · https://linear.app/developers/graphql · schema explorer https://studio.apollographql.com/public/Linear-API/variant/current/home · webhooks https://linear.app/developers/webhooks · attachments https://linear.app/developers/attachments · rate limits https://linear.app/developers/rate-limiting · OAuth https://linear.app/developers/oauth-2-0-authentication
- **Auth:** `oauth2` or personal API key — secret binding: `api_key` (PAT) or `oauth_client_secret` + `refresh_token`

## Scope

One node for Linear issue work-management over the **official GraphQL API** (the same schema Linear uses internally). The designer picks `operation`; the handler POSTs a fixed query/mutation document to `https://api.linear.app/graphql`. Do not ship the Linear TypeScript SDK. Do not scrape linear.app HTML. Do not call undocumented internal endpoints.

`issue_add_link` maps to Linear **issue attachments** (`attachmentCreate`): an external URL attached to an issue (idempotent on `(issueId, url)`). It is a link, not a file upload. File bytes are not a catalog verb here.

`listen` uses **official Linear webhooks** (HTTPS POST). Register via `webhookCreate` (admin / OAuth `admin` scope) or accept deliveries already configured in Linear Settings → API. Verify `Linear-Signature` HMAC-SHA256 of the **raw** body.

Dropped from this spec: none of the catalog seed verbs. (Developer-tools extra verbs `project_create`, `project_get`, `label_add` are **out of scope** here — do not implement them on this node unless the productivity catalog is updated.)

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `issue_create` | POST | GraphQL `issueCreate` | Create an issue on a team |
| `issue_get` | POST | GraphQL `issue(id:)` | Fetch one issue by UUID or identifier (`ENG-123`) |
| `issue_list` | POST | GraphQL `issues` (filter + `first`/`after`) | Paginated issue list / search |
| `issue_update` | POST | GraphQL `issueUpdate` | Patch title, state, assignee, labels, due date, … |
| `issue_delete` | POST | GraphQL `issueDelete` | Trash (30-day recovery) or permanent delete |
| `issue_add_link` | POST | GraphQL `attachmentCreate` | Attach an external URL to an issue |
| `comment_add` | POST | GraphQL `commentCreate` | Add a markdown comment on an issue |
| `listen` | POST | GraphQL `webhookCreate` (register) + inbound HTTPS | Subscribe to Issue/Comment/IssueAttachment events |

All GraphQL calls: `POST https://api.linear.app/graphql` with JSON `{ "query", "variables" }`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `issue_get` | no | Chooses the row above |
| `auth_type` | select | yes | `api_key` | no | `api_key` \| `oauth2` |
| `api_key` | password | if `auth_type=api_key` | empty | yes | Personal API key. Header `Authorization: {api_key}` **without** `Bearer`. Never on INPUT. |
| `oauth_client_id` | string | if `auth_type=oauth2` | empty | no | OAuth app client id |
| `oauth_client_secret` | password | if `auth_type=oauth2` | empty | yes | OAuth client secret |
| `refresh_token` | password | if `auth_type=oauth2` | empty | yes | Refresh token; mint access token per invoke (do not persist access tokens) |
| `oauth_actor` | select | no | `user` | no | `user` (default) or `app` (`actor=app` authorization). `app` requires the OAuth app to request it. |
| `default_team_id` | string | no | empty | no | Default Linear team UUID for `issue_create` / `issue_list` when input omits `team_id` |
| `webhook_signing_secret` | password | `listen` | empty | yes | HMAC secret from the webhook detail page. Verify `Linear-Signature`. |
| `timeout_seconds` | number | no | `25` | no | Connect + read timeout. Floor 1, cap 120. |

OAuth token URL: `https://api.linear.app/oauth/token` (authorization code + refresh). Access token header: `Authorization: Bearer {access_token}`. Scopes: `read` always; `write` or `issues:create` + `comments:create` for mutations; `admin` only if `listen` must create webhooks via API.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Incoming webhook body for `listen` when the engine is correlating an event. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo API keys, OAuth tokens, or signing secrets |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = issue_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` | string | if config `default_team_id` blank | Linear team UUID (`issueCreate.input.teamId`) |
| `title` | string | yes | Issue title. Cap 512 chars. |
| `description` | string | no | Markdown body. Cap 256 KiB. |
| `assignee_id` | string | no | User UUID |
| `state_id` | string | no | Workflow state UUID. Omitted → team Backlog (or Triage if enabled). |
| `priority` | number | no | Linear priority 0–4 (0 = none) |
| `label_ids` | array of string | no | Label UUIDs |
| `project_id` | string | no | Project UUID |
| `due_date` | string | no | `YYYY-MM-DD` |
| `parent_id` | string | no | Parent issue UUID / identifier for a sub-issue |
| `create_as_user` | string | no | Display name when OAuth `actor=app` |
| `display_icon_url` | string | no | Avatar URL when OAuth `actor=app` |

### `operation = issue_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issue_id` | string | yes | UUID or identifier (`ENG-123`) |

### `operation = issue_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `team_id` | string | no | Restrict to one team |
| `assignee_id` | string | no | Filter assignee |
| `state_id` | string | no | Filter workflow state |
| `project_id` | string | no | Filter project |
| `query` | string | no | Title/identifier search (`filter.title.containsIgnoreCase` or `searchIssues` if using search) |
| `include_archived` | boolean | no | Default `false`. Pass GraphQL `includeArchived` |
| `first` | number | no | Page size 1–50 (Linear default 50). Cap **50**. |
| `after` | string | no | Cursor from previous `page_info.end_cursor` |

Use GraphQL `issues(filter: { team: { id: { eq } }, … }, first, after, includeArchived)` with selection `nodes { id identifier title url state { id name } assignee { id name } priority createdAt updatedAt }` plus `pageInfo { hasNextPage endCursor }`. Do not request default-50 nested connections (comments, children) — complexity explodes.

### `operation = issue_update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issue_id` | string | yes | UUID or identifier |
| `title` | string | no | New title |
| `description` | string | no | Replace markdown body |
| `state_id` | string | no | New workflow state |
| `assignee_id` | string | no | New assignee UUID; empty string → unassign if schema allows `null` |
| `priority` | number | no | 0–4 |
| `label_ids` | array of string | no | Replace labels (`labelIds`) |
| `project_id` | string | no | Move to project |
| `due_date` | string | no | `YYYY-MM-DD` or empty to clear |

At least one patch field besides `issue_id` is required.

### `operation = issue_delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issue_id` | string | yes | UUID or identifier |
| `delete_permanently` | boolean | no | Default `false`. `true` skips the 30-day recovery window — **workspace admin only**. |

### `operation = issue_add_link`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issue_id` | string | yes | Issue UUID |
| `url` | string | yes | External HTTPS URL (idempotency key with issue id) |
| `title` | string | yes | Attachment title shown in Linear |
| `subtitle` | string | no | Secondary line |
| `icon_url` | string | no | PNG/JPG icon URL |
| `metadata` | object | no | String/number map stored on the attachment. Cap 16 KiB JSON. |

### `operation = comment_add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `issue_id` | string | yes | Issue UUID or identifier |
| `body` | string | yes | Markdown. Cap 256 KiB. |
| `create_as_user` | string | no | OAuth `actor=app` display name |
| `display_icon_url` | string | no | OAuth `actor=app` avatar URL |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | select | no | `register` (default) \| `ingest`. `register` calls `webhookCreate`. `ingest` verifies an inbound Linear POST already delivered to StudioQuack ingress. |
| `callback_url` | string | `register` | Public HTTPS URL (StudioQuack message-event ingress). No localhost. |
| `team_id` | string | no | Scope webhook to one team. If omitted, set `allPublicTeams: true`. |
| `resource_types` | array of string | no | Default `["Issue","Comment","IssueAttachment"]`. Allowed: Issue, Comment, IssueLabel, Project, Cycle, Reaction, IssueAttachment, Document, Initiative, User, Customer, CustomerNeed (verify live schema). |
| `webhook_id` | string | no | Existing webhook UUID when `mode=ingest` and the operator already registered out of band |
| `raw_body` | string | `ingest` | Raw JSON body of the Linear POST (must be the exact bytes hashed) |
| `linear_signature` | string | `ingest` | Value of header `Linear-Signature` |
| `linear_timestamp_ms` | number | no | `Linear-Timestamp` header (ms). Reject if skew > 60 s. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx GraphQL with `success` / data | `ok` |
| `issue_id` | string | create/get/update/delete | UUID |
| `identifier` | string | create/get/update | e.g. `ENG-123` |
| `url` | string | create/get/update | Linear issue URL |
| `title` | string | create/get/update | Title |
| `issue` | object | get/update | Compact issue (id, identifier, title, description, state, assignee, priority, labels, project, dueDate, url, createdAt, updatedAt) |
| `issues` | array | `issue_list` | Compact issues |
| `page_info` | object | `issue_list` | `{ has_next_page, end_cursor }` |
| `attachment_id` | string | `issue_add_link` | Attachment UUID |
| `comment_id` | string | `comment_add` | Comment UUID |
| `webhook_id` | string | `listen` register | Created webhook UUID |
| `event` | object | `listen` ingest | `{ action, type, created_at, url, data, actor, organization_id }` |
| `error` | string | failure | Validation, GraphQL `errors[].message`, HTTP 4xx/5xx. Never tokens. |
| `http_status` | number | HTTP error | Linear HTTP status (GraphQL rate limit is often **400** with `extensions.code=RATELIMITED`) |
| `retry_after_seconds` | number | 429 / RATELIMITED / 5xx | From `Retry-After` if present; else 60 on RATELIMITED |

GraphQL HTTP 200 with `errors[]` and partial `data` is a **failure** unless the requested root field is present and `success !== false`. Map `success: false` on mutations to `{ error }`.

## Legal

- Terms of Service: https://linear.app/terms (effective 2026-06-09, Linear Orbit, Inc.)
- Privacy: https://linear.app/privacy
- Developer / API: https://linear.app/developers · OAuth app review required for **public** apps distributed to other workspaces
- Rate limits (published 2026-08-20): leaky-bucket. **API key:** 2,500 requests/hour/user and 3,000,000 complexity points/hour. **OAuth app:** 5,000 requests/hour and 2,000,000 complexity points/hour. Unauthenticated: 600 req/h and 100,000 complexity/h per IP. Single-query complexity cap **10,000**. Headers: `X-RateLimit-Requests-*`, `X-RateLimit-Complexity-*`, `X-Complexity`. Over limit → HTTP 400 + `errors[].extensions.code = RATELIMITED` (not always 429).
- PII: issue titles, descriptions, comments, assignee names/emails, and webhook `actor.email` leave the customer sandbox toward Linear. Minimize. Do not log bodies. GDPR: customer is controller of workspace content; Linear is the processor they contracted.
- Official-API-first: GraphQL + documented webhooks only. No scrape.

## Implementation notes

- JDK `HttpClient` only. `Content-Type: application/json`. Body size cap **1 MiB** request / **2 MiB** response; fail closed if exceeded.
- Timeouts: default 25 s. No retry on 4xx except RATELIMITED / 429. Retry **once** on 502/503/504 and RATELIMITED with jitter; honor `Retry-After` / `X-RateLimit-Requests-Reset` (UTC epoch ms).
- Empty `api_key` / missing OAuth refresh → `{ error }` **without** HTTP.
- PAT header is `Authorization: <key>` (no Bearer). OAuth access token uses Bearer. Mixing them causes 401.
- Introspection is allowed by Linear; do **not** introspect on every invoke. Pin operation documents in the handler.
- Pagination: always pass `first` (max 50). Never nest unbounded connections.
- Webhook ingest: HMAC-SHA256 hex of raw body with `webhook_signing_secret`; `timingSafeEqual` against `Linear-Signature`. Reject replay if `|now - webhookTimestamp| > 60s`. Respond 200 to Linear only after verification (ingress layer). Linear retries failed deliveries 3 times (1 min, 1 h, 6 h); consumer must answer within 5 s.
- Linear webhook source IPs (verify live docs before allowlisting): 35.231.147.226, 35.243.134.228, 35.196.141.51, 34.140.253.14, 34.38.87.206, 34.62.119.29, 34.134.222.122, 35.222.25.142, 34.60.255.158.
- Logs: operation, issue identifier, HTTP/GraphQL status, complexity header — never keys, never markdown bodies.
- Suggested `issueCreate` selection: `success issue { id identifier title url }`.
- Suggested `issueDelete` selection: `success`.
- Suggested `attachmentCreate` / `commentCreate`: `success attachment { id url }` / `success comment { id }`.
