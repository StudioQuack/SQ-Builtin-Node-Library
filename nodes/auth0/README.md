# Auth0

Official **Auth0 Management API v2** (current). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the tenant.

- **Docs:** https://auth0.com/docs/api/management/v2
- **Base:** `https://{domain}/api/v2/…` (`baseUrl` is `https://`; `domain` is host only)
- **Auth:** `Authorization: Bearer {access_token}` — a Management API token the operator **already minted**. This node does not perform the client-credentials exchange. Never log `access_token`.

`listen` is BPMN message-event **ingress**. This node does not register log streams.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-user` | POST | `{domain}/api/v2/users` |
| `get-user` | GET | `{domain}/api/v2/users/{id}` |
| `list-users` | GET | `{domain}/api/v2/users` |
| `update-user` | PATCH | `{domain}/api/v2/users/{id}` |
| `delete-user` | DELETE | `{domain}/api/v2/users/{id}` |
| `create-role` | POST | `{domain}/api/v2/roles` |
| `list-roles` | GET | `{domain}/api/v2/roles` |
| `assign-role` | POST | `{domain}/api/v2/users/{id}/roles` body `{ "roles": ["rol_…"] }` |
| `list-clients` | GET | `{domain}/api/v2/clients` |
| `get-log` | GET | `{domain}/api/v2/logs/{id}` |
| `list-logs` | GET | `{domain}/api/v2/logs` |
| `listen` | ingress | BPMN message start / catch / boundary |

`create-user` requires `connection` (database or passwordless). `password` for database users is a secret at runtime — never place it on the INPUT schema; never log it.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Management API Bearer token |
| `domain` | string | no | Tenant host only (canonical or custom domain host) |
| `timeout_seconds` | number | no | Default 25 |

Least-privilege scopes for the minted token: `create:users`, `read:users`, `update:users`, `delete:users`, `create:roles`, `read:roles`, `read:clients`, `read:logs` (trim to the verbs you actually call).

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `access_token`.

## Legal

- Platform terms: https://auth0.com/legal
- API terms and acceptable use as published on that site (current 2026).
- User profiles and logs are personal data. The customer is the controller of the tenant. StudioQuack does not accept the provider’s terms on the customer’s behalf.
- Official Management API only. Do not scrape the Dashboard.

Never log `access_token`, user passwords, or full log event payloads that contain credentials.
