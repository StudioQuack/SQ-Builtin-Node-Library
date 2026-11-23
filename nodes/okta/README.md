# Okta

Official **Okta Admin Management API** (`/api/v1`, org-scoped REST). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Okta org.

- **Docs:** https://developer.okta.com/docs/api/openapi/okta-management/management/tag/User/
- **Core reference:** https://developer.okta.com/docs/reference/core-okta-api/
- **Base:** `https://{orgUrl}/api/v1/…` (`baseUrl` is `https://`; `orgUrl` is host only)
- **Auth:** header `Authorization: SSWS {ssws_token}` (StudioQuack secret). Still valid; Okta recommends scoped OAuth 2.0 Bearer for new integrations (this node does not send Bearer). Never log `ssws_token` or passwords.

`orgUrl` must **not** be the Admin Console host (`*-admin`). Example: `example.okta.com`.

`listen` is BPMN message-event **ingress**. This node does not POST Event Hooks.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-user` | POST | `{orgUrl}/api/v1/users` |
| `get-user` | GET | `{orgUrl}/api/v1/users/{userId}` |
| `list-users` | GET | `{orgUrl}/api/v1/users` query `q`, `search`, `filter`, `limit`, `after` |
| `update-user` | POST | `{orgUrl}/api/v1/users/{userId}` (partial update is POST, not PATCH) |
| `delete-user` | DELETE | `{orgUrl}/api/v1/users/{userId}` (first call deactivates; second permanently deletes `DEPROVISIONED`) |
| `create-group` | POST | `{orgUrl}/api/v1/groups` |
| `list-groups` | GET | `{orgUrl}/api/v1/groups` |
| `add-user-to-group` | PUT | `{orgUrl}/api/v1/groups/{groupId}/users/{userId}` |
| `remove-user-from-group` | DELETE | `{orgUrl}/api/v1/groups/{groupId}/users/{userId}` |
| `list-apps` | GET | `{orgUrl}/api/v1/apps` |
| `list-factors` | GET | `{orgUrl}/api/v1/users/{userId}/factors` |
| `listen` | ingress | BPMN message start / catch / boundary |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `ssws_token` | password | yes | SSWS API token |
| `orgUrl` | string | no | Org host, no scheme, no `-admin` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `ssws_token`.

## Legal

- Developer / integrator terms: https://developer.okta.com/terms/
- Customer subscription: https://www.okta.com/legal/
- Rate limits: https://developer.okta.com/docs/reference/rate-limits/ — HTTP 429 with `X-Rate-Limit-*`
- Directory `profile` is personal data. The customer is the controller. StudioQuack does not accept Okta’s terms on the customer’s behalf.
- SSWS is org-admin equivalent and unscoped. Okta recommends OAuth 2.0 Bearer (https://developer.okta.com/docs/api/openapi/okta-management/guides/overview). Rotate; bind only as a daemon secret.
- Official Management REST only. Do not scrape the Admin Console.

Never log `ssws_token`, `credentials.password`, or recovery answers.
