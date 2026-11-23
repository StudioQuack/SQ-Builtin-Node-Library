# Bitwarden

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_BITWARDEN`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Bitwarden Public API (/public) + Secrets Manager REST](https://bitwarden.com/help/public-api/) at `https://`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `list-collections` | GET | `{apiHost}/public/collections` | Official HTTP |
| `get-collection` | GET | `{apiHost}/public/collections/{id}` | Official HTTP |
| `update-collection` | PUT | `{apiHost}/public/collections/{id}` | Official HTTP |
| `delete-collection` | DELETE | `{apiHost}/public/collections/{id}` | Official HTTP |
| `list-events` | GET | `{apiHost}/public/events` | Official HTTP |
| `create-group` | POST | `{apiHost}/public/groups` | Official HTTP |
| `get-group` | GET | `{apiHost}/public/groups/{id}` | Official HTTP |
| `list-groups` | GET | `{apiHost}/public/groups` | Official HTTP |
| `update-group` | PUT | `{apiHost}/public/groups/{id}` | Official HTTP |
| `delete-group` | DELETE | `{apiHost}/public/groups/{id}` | Official HTTP |
| `get-group-members` | GET | `{apiHost}/public/groups/{id}/member-ids` | Official HTTP |
| `update-group-members` | PUT | `{apiHost}/public/groups/{id}/member-ids` | Official HTTP |
| `create-member` | POST | `{apiHost}/public/members` | Official HTTP |
| `get-member` | GET | `{apiHost}/public/members/{id}` | Official HTTP |
| `list-members` | GET | `{apiHost}/public/members` | Official HTTP |
| `update-member` | PUT | `{apiHost}/public/members/{id}` | Official HTTP |
| `delete-member` | DELETE | `{apiHost}/public/members/{id}` | Official HTTP |
| `get-member-groups` | GET | `{apiHost}/public/members/{id}/group-ids` | Official HTTP |
| `update-member-groups` | PUT | `{apiHost}/public/members/{id}/group-ids` | Official HTTP |
| `get-secret` | GET | `{apiHost}/secrets/{id}` | Official HTTP |
| `list-secrets` | GET | `{apiHost}/organizations/{organizationId}/secrets` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_token` | password | yes | yes | Bearer for Public API (org token after client_credentials) or SM machine-account token. StudioQuack secret. Never logged. OfficialApiNode does not POST identity.bitwarden.com. Reject personal user.* keys. |
| `apiHost` | string | yes | no | API host without scheme. Cloud US default api.bitwarden.com. EU: api.bitwarden.eu. Self-host: your.domain.com/api (path already includes /api). |
| `organizationId` | string | no | no | Org UUID for list-secrets (SM). |
| `timeout_seconds` | number | no | no | Default 25 |

## Secret values

GET/access operations still call the official GET (or access) HTTP method. Retrieved secret material must not be logged and should stay in StudioQuack secret bindings (bind OUTPUT `data` into a secret; do not print it, persist it in engine variables, or leave it on a logged edge).

Auth tokens, passwords, and retrieved secret bytes are never logged. Do not put them on INPUT, in engine variables, or on a logged edge.

## How it works

1. `OfficialApiNode` loads this folder because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded. Tenant hosts use `baseUrl` `https://` plus `{host}/…`.
3. Query keys in `queryFrom` resolve from input then config. JSON `bodyFrom` is INPUT-only.
4. JDK HTTP via `DataSupport.exchange`. HTTP 2xx → `status=ok`. Non-2xx raise a BPMN error.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [Bitwarden Public API (/public) + Secrets Manager REST docs](https://bitwarden.com/help/public-api/). Terms: https://bitwarden.com/terms/.
- Org Public API + Secrets Manager only. This is not a personal vault dump (no /ciphers, no Vault Management API). SM docs: https://bitwarden.com/help/secrets-manager-sdk/ (help/secrets-manager-api/ is 404). get-secret still calls official GET /secrets/{id}; retrieved value must not be logged and should stay in StudioQuack secret bindings. Bind an org bearer (scope api.organization) or SM machine token as access_token.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
