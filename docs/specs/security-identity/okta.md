# Okta

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/okta/`
- **Researched:** 2026-08-20
- **Official API:** Okta Admin Management API (`/api/v1`, org-scoped REST). Canonical surface is the Management OpenAPI (Users, Groups, Applications, User Factors, Event Hooks).
- **Docs:** https://developer.okta.com/docs/api/openapi/okta-management/management/tag/User/ and https://developer.okta.com/docs/reference/core-okta-api/
- **Auth:** `sswsToken` (header `Authorization: SSWS {apiToken}`) or optional `oauth2` (header `Authorization: Bearer {accessToken}`, scoped OAuth for management) — secret binding: `ssws_token` / `oauth_client_secret`

## Scope

Calls the **official Okta Management API** on the operator’s org (`https://{orgUrl}/api/v1/…`) to manage users, groups, app listings, enrolled MFA factors, and Event Hook subscriptions. `orgUrl` is the **org** host (for example `example.okta.com` or `example.oktapreview.com`), **not** the Admin Console host (`*-admin.okta.com`). Reject `-admin` hostnames before any HTTP call.

This node does not wrap Okta Inline Hooks as a general engine, does not scrape the Admin Console, and does not ship a vendor SDK. Lifecycle verbs that are not in the catalog (activate / deactivate / suspend / unlock) are omitted — they are not listed as “later”.

`listen` registers an Okta **Event Hook** (`POST /api/v1/eventHooks`) whose `channel.config.uri` is the operator’s StudioQuack message-event ingress URL. Inbound Okta POSTs are not a separate catalog node; they are delivered through the StudioQuack engine's message-event ingress.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `create-user` | POST | `/api/v1/users` | Create a user (`profile` required). Query `activate`, optional `nextLogin=changePassword`. |
| `get-user` | GET | `/api/v1/users/{id}` | Fetch one user by id, login, or unambiguous login shortname. |
| `list-users` | GET | `/api/v1/users` | Page users (`search`, `q`, `filter`, `limit`, `after`). |
| `update-user` | POST | `/api/v1/users/{id}` | Partial profile/credentials update (Okta’s documented partial-update verb is POST, not PATCH). |
| `delete-user` | DELETE | `/api/v1/users/{id}` | First call on a non-`DEPROVISIONED` user **deactivates**. A second DELETE permanently removes a `DEPROVISIONED` user. Irreversible. |
| `create-group` | POST | `/api/v1/groups` | Create a group (`profile.name` required). |
| `list-groups` | GET | `/api/v1/groups` | Page groups (`q`, `filter`, `search`, `limit`, `after`). |
| `add-user-to-group` | PUT | `/api/v1/groups/{groupId}/users/{userId}` | Add an existing user to a group. |
| `remove-user-from-group` | DELETE | `/api/v1/groups/{groupId}/users/{userId}` | Remove a user from a group. |
| `list-apps` | GET | `/api/v1/apps` | Page application instances (`q`, `filter`, `limit`, `after`). |
| `list-factors` | GET | `/api/v1/users/{userId}/factors` | List enrolled MFA factors for one user. |
| `listen` | POST (+ verify/activate) | `/api/v1/eventHooks` then `POST /api/v1/eventHooks/{eventHookId}/lifecycle/verify` and `/lifecycle/activate` | Subscribe Okta events to an HTTPS endpoint. Official Event Hooks API. |

Retired / unofficial verbs are omitted.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get-user` | no | Chooses the row above. |
| `auth` | select | yes | `sswsToken` | no | `sswsToken` \| `oauth2`. |
| `org_url` | string | yes | — | no | Org host or origin, **without** `-admin`. Normalize to `https://{host}` (no trailing slash). |
| `ssws_token` | password | if `sswsToken` | — | **yes** | API token. Header `Authorization: SSWS …`. Org-admin equivalent — least privilege + rotate. Never on INPUT/OUTPUT. |
| `oauth_client_id` | string | if `oauth2` | — | no | OIDC client for management scopes. |
| `oauth_client_secret` | password | if `oauth2` | — | **yes** | Client secret. Token POST to `https://{org}/oauth2/v1/token` (or the org authorization server). Never logged. |
| `oauth_scopes` | string | if `oauth2` | `okta.users.read okta.users.manage okta.groups.manage okta.apps.read okta.eventHooks.manage` | no | Space-separated Okta management scopes. Trim to least privilege for the selected ops. |
| `timeout_seconds` | number | no | `30` | no | Connect + read timeout per call (token + management). `<= 0` → 30. |
| `max_response_bytes` | number | no | `8388608` | no | Cap on response body (8 MiB). Over → `{ error }` without parsing. |

No `file` parameters. This node does not transfer files.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No API tokens. End-user passwords (create/update) use type `password` and are never logged. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never includes `ssws_token`, OAuth secrets, or `credentials.password.value`. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = create-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile.firstName` | string | yes (default schema) | Given name. |
| `profile.lastName` | string | yes (default schema) | Family name. |
| `profile.email` | string | yes | Primary email (RFC 5322). |
| `profile.login` | string | yes | Unique username (`login`). |
| `profile.mobilePhone` | string | no | Mobile phone. |
| `profile.secondEmail` | string | no | Recovery email. |
| `profile.displayName` | string | no | Display name. |
| `profile.department` | string | no | Department. |
| `profile.title` | string | no | Job title. |
| `profile.employeeNumber` | string | no | Org employee id. |
| `activate` | boolean | no | Query `activate`. Default true in Okta if omitted — send explicitly. |
| `nextLogin` | string | no | `changePassword` with `activate=true` expires the password. |
| `groupIds` | string[] | no | Up to 20 group ids at create time. |
| `credentials.password.value` | password | no | Cleartext password. Never log. Omit to create without a password. |
| `type.id` | string | no | Non-default user type id. |

### `operation = get-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | User id, login, or unambiguous shortname. |

### `operation = list-users`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `search` | string | no | SCIM filter search (preferred for large orgs). |
| `q` | string | no | Simple query (name/email). Mutually exclusive with `search` in practice — if both set, send `search` only. |
| `filter` | string | no | Filter expression as documented on List Users. |
| `limit` | number | no | Page size. Clamp to Okta’s documented max (typically 200). |
| `after` | string | no | Cursor from the previous `Link: rel="next"` / `after`. |

### `operation = update-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | User id or login. |
| `profile` | object | no | Partial profile (same keys as create). At least one of `profile` or `credentials` required. |
| `credentials.password.value` | password | no | Set password. Never log. |

### `operation = delete-user`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | User id or login. |
| `sendEmail` | boolean | no | Query `sendEmail` (deactivation notice to admins). |
| `permanent` | boolean | no | If true and the first DELETE only deactivated, immediately send the second DELETE. Default false (safer). |

### `operation = create-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile.name` | string | yes | Group name. |
| `profile.description` | string | no | Description. |

### `operation = list-groups`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | no | Starts-with name search. |
| `filter` | string | no | Filter expression. |
| `search` | string | no | SCIM search. |
| `limit` | number | no | Page size. |
| `after` | string | no | Cursor. |

### `operation = add-user-to-group` / `remove-user-from-group`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `groupId` | string | yes | Group id (`00g…`). |
| `userId` | string | yes | User id (`00u…`). |

### `operation = list-apps`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | no | Name/label search. |
| `filter` | string | no | Filter (for example `status eq "ACTIVE"`). |
| `limit` | number | no | Page size. |
| `after` | string | no | Cursor. |

### `operation = list-factors`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `userId` | string | yes | User id. |

### `operation = listen`

Official subscribe path: **`POST https://{orgUrl}/api/v1/eventHooks`**.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Event hook name. |
| `events.items` | string[] | yes | Event types that are **event-hook-eligible** (Event Types catalog). Example: `user.lifecycle.create`. `events.type` is sent as `EVENT_TYPE`. |
| `channel.config.uri` | string | yes | HTTPS URL of StudioQuack message-event ingress. Must be publicly reachable for Okta. |
| `channel.config.authScheme.type` | string | no | Header auth scheme type (typically `HEADER`). |
| `channel.config.authScheme.key` | string | no | Header name Okta will send (for example `Authorization` or `X-SQ-API-KEY`). |
| `channel.config.authScheme.value` | password | no | Header value. StudioQuack secret binding; never logged. |
| `description` | string | no | Hook description. |
| `verify_and_activate` | boolean | no | Default true. After create, call verify then `POST /api/v1/eventHooks/{id}/lifecycle/activate`. |

Okta one-time verification: Okta sends **GET** to `channel.config.uri` with header `x-okta-verification-challenge`. Ingress must respond with JSON `{ "verification": "<challenge>" }`. Unverified hooks receive no events (`verificationStatus=UNVERIFIED`). Org cap: **25** active verified event hooks.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok`. Absent when `error` is set. |
| `http_status` | number | always on HTTP | Provider status (200, 201, 204, 4xx, 5xx). |
| `id` | string | user/group/hook create or get | Resource id (`00u…` / `00g…` / `who…`). |
| `user` | object | user get/create/update | `id`, `status`, `created`, `activated`, `lastLogin`, `lastUpdated`, `passwordChanged`, `transitioningToStatus`, `profile` (no password values), `type`. Strip `credentials.password.value` / `recovery_question.answer` if present. |
| `users` | array | `list-users` | Page of user summaries (`id`, `status`, `profile.login`, `profile.email`, `profile.firstName`, `profile.lastName`). |
| `group` | object | `create-group` | `id`, `created`, `lastUpdated`, `type`, `profile.name`, `profile.description`. |
| `groups` | array | `list-groups` | Same shape as `group`. |
| `apps` | array | `list-apps` | `id`, `name`, `label`, `status`, `signOnMode`, `created`, `lastUpdated`. |
| `factors` | array | `list-factors` | `id`, `factorType`, `provider`, `vendorName`, `status`, `created`, `lastUpdated`, `profile` (no secrets). |
| `event_hook` | object | `listen` | `id`, `name`, `status`, `verificationStatus`, `created`, `lastUpdated`, `events`, `channel.config.uri` (never `authScheme.value`). |
| `after` | string | list ops with a next page | Cursor parsed from `Link` `rel="next"`. |
| `error` | string | failure | Actionable, no token. Prefer Okta `errorSummary` + `errorCode` when present. |
| `error_id` | string | Okta error JSON | Okta `errorId` (support correlation). |

`add-user-to-group` / `remove-user-from-group` / successful `delete-user` (204): `{ status: "ok", http_status: 204 }`.

## Legal

- Developer / integrator terms: https://developer.okta.com/terms/
- Customer use of a paid Okta org is also governed by the operator’s Okta subscription agreement (Okta legal: https://www.okta.com/legal/).
- Rate limits: bucketed per org, method, and subscription. Exceeding a bucket returns **HTTP 429**. Headers: `X-Rate-Limit-Limit`, `X-Rate-Limit-Remaining`, `X-Rate-Limit-Reset` (Unix seconds). SSWS tokens are typically capped at a configurable share of the org bucket (default 50%). Docs: https://developer.okta.com/docs/reference/rate-limits/
- Create-user legal disclaimer (Okta): activation emails must not be used to spam; the operator is responsible for recipients.
- PII: user `profile` (name, email, phone, employeeNumber, addresses) is personal data. Event Hook payloads include identity events. Minimize fields; do not log profile bodies. GDPR/HIPAA: the customer is the controller of directory data; StudioQuack does not accept Okta’s terms on their behalf.
- Official-API-first: Management REST only. No Admin Console scrape.

## Implementation notes

- JDK `HttpClient` only. No Okta SDK.
- Headers: `Accept: application/json`, `Content-Type: application/json` on writes, `User-Agent: StudioQuack/1.0`, plus `Authorization`.
- Timeouts: `timeout_seconds` on every call including OAuth token POST. Size cap: `max_response_bytes`.
- Retry: on **429**, wait until `X-Rate-Limit-Reset` (cap wait at 60s) once; on **5xx**/timeout, one retry with jitter. Do not retry 4xx other than 429.
- Errors: never throw past dispatch. Map 4xx/5xx/timeout/parse failure to `{ error }` on OUTPUT. Interpolate `errorSummary` only — never the `Authorization` header.
- SSWS is org-admin equivalent. Prefer scoped OAuth (`okta.users.read` vs `okta.users.manage`). Rotate tokens; store only as daemon secret bindings.
- `delete-user`: document two-step deactivate-then-delete in the node README when built. `permanent=true` is explicit.
- `listen`: after POST, if `verify_and_activate`, POST verify then activate. If verification fails because ingress has not echoed the challenge, return `{ error }` with `verificationStatus`. Do not poll events.
- Pagination: parse `Link` header; expose `after`. Do not auto-walk all pages in one invoke.
- Logs: shapes, ids, status codes, sizes only — never tokens, passwords, or full profiles.
