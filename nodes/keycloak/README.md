# Keycloak

Official **Keycloak Admin REST API** (latest `docs-api`, verified against 26.7.1) plus realm **OpenID Connect** token and introspection endpoints. Handler: `OfficialApiNode` via `api_spec.json`.

This node talks to a **customer-hosted** realm only. Do **not** point `baseHost` / `realm` at the StudioQuack control-plane IdP.

- **Docs:** https://www.keycloak.org/docs-api/latest/rest-api/index.html
- **OIDC:** https://www.keycloak.org/securing-apps/oidc-layers
- **Base:** `https://{baseHost}/admin/realms/{realm}/…` and `https://{baseHost}/realms/{realm}/protocol/openid-connect/…` (`baseUrl` is `https://`)
- **Auth:** `Authorization: Bearer {access_token}` (realm-management roles). Never log `access_token`, credential `value`, client secrets, or refresh tokens.

Path placeholders use `{userId}` / `{clientUuid}` because the engine substitutes `[A-Za-z0-9_]+` only (official docs write `{user-id}` / `{client-uuid}`).

`listen` is BPMN message-event **ingress**. This node does not subscribe to Admin events over HTTP.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-user` | POST | `{baseHost}/admin/realms/{realm}/users` |
| `get-user` | GET | `{baseHost}/admin/realms/{realm}/users/{userId}` |
| `list-users` | GET | `{baseHost}/admin/realms/{realm}/users` |
| `update-user` | PUT | `{baseHost}/admin/realms/{realm}/users/{userId}` |
| `delete-user` | DELETE | `{baseHost}/admin/realms/{realm}/users/{userId}` |
| `reset-password` | PUT | `{baseHost}/admin/realms/{realm}/users/{userId}/reset-password` |
| `create-group` | POST | `{baseHost}/admin/realms/{realm}/groups` |
| `list-groups` | GET | `{baseHost}/admin/realms/{realm}/groups` |
| `create-client` | POST | `{baseHost}/admin/realms/{realm}/clients` |
| `get-client` | GET | `{baseHost}/admin/realms/{realm}/clients/{clientUuid}` |
| `list-clients` | GET | `{baseHost}/admin/realms/{realm}/clients` |
| `create-role` | POST | `{baseHost}/admin/realms/{realm}/roles` |
| `assign-role` | POST | `{baseHost}/admin/realms/{realm}/users/{userId}/role-mappings/realm` |
| `token-client-credentials` | POST | `{baseHost}/realms/{realm}/protocol/openid-connect/token` |
| `token-refresh` | POST | `{baseHost}/realms/{realm}/protocol/openid-connect/token` |
| `token-introspect` | POST | `{baseHost}/realms/{realm}/protocol/openid-connect/token/introspect` |
| `listen` | ingress | BPMN message start / catch / boundary |

**Engine limits (do not hide):** `OfficialApiNode` JSON-encodes POST/PUT/PATCH bodies (even when `contentType` is `application/x-www-form-urlencoded`) and always attaches Bearer `access_token`. OIDC token/introspect therefore may 400 until the engine supports form bodies; `assign-role` officially wants a **JSON array** of `RoleRepresentation`, while the engine emits a map (`{ "roles": [ … ] }`).

Admin credentials are realm-god-mode. Least privilege + rotate.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Admin Bearer token |
| `baseHost` | string | no | Host without scheme |
| `realm` | string | no | Customer realm name |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `access_token`.

## Legal

- Keycloak is Apache License 2.0: https://www.keycloak.org/license
- Project site: https://www.keycloak.org/
- The operator’s own deployment, realm users, and clients are personal data they control. StudioQuack does not operate this IdP.
- Official Admin REST + OIDC only. Do not scrape the Admin Console.

Never log `access_token`, `reset-password` values, client `secret`, `refresh_token`, or introspection `token`.
