# Infisical

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_INFISICAL`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Infisical Public REST API (secrets v4, folders v2)](https://infisical.com/docs/api-reference/overview/introduction) at `https://`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `get-secret` | GET | `{siteHost}/api/v4/secrets/{secretName}` | Official HTTP |
| `list-secrets` | GET | `{siteHost}/api/v4/secrets` | Official HTTP |
| `create-secret` | POST | `{siteHost}/api/v4/secrets/{secretName}` | Official HTTP |
| `update-secret` | PATCH | `{siteHost}/api/v4/secrets/{secretName}` | Official HTTP |
| `delete-secret` | DELETE | `{siteHost}/api/v4/secrets/{secretName}` | Official JSON body (`projectId`, `environment`, optional `secretPath`/`type`) |
| `list-folders` | GET | `{siteHost}/api/v2/folders` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `access_token` | password | yes | yes | Universal Auth access token (or equivalent identity token). Sent as Authorization: Bearer. StudioQuack secret. Never logged. OfficialApiNode does not exchange client id/secret. |
| `siteHost` | string | yes | no | API host without scheme. Cloud default app.infisical.com. Self-host: your Infisical host. |
| `projectId` | string | yes | no | Project id (query/body). |
| `environment` | string | yes | no | Environment slug, e.g. dev. |
| `secretPath` | string | no | no | Folder path for secret ops. Default /. |
| `path` | string | no | no | list-folders query path. Default /. |
| `timeout_seconds` | number | no | no | Default 25 |

## Secret values

GET/access operations still call the official GET (or access) HTTP method. Retrieved secret material must not be logged and should stay in StudioQuack secret bindings (bind OUTPUT `data` into a secret; do not print it, persist it in engine variables, or leave it on a logged edge).

Auth tokens, passwords, and retrieved secret bytes are never logged. Do not put them on INPUT, in engine variables, or on a logged edge.

## How it works

1. `OfficialApiNode` loads this folder because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded. Tenant hosts use `baseUrl` `https://` plus `{host}/…`.
3. Query keys in `queryFrom` resolve from input then config. JSON `bodyFrom` is INPUT-only.
4. JDK HTTP via `DataSupport.exchange`. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from password `configurationParameters`. Never logged, never returned on purpose.
- [Infisical Public REST API (secrets v4, folders v2) docs](https://infisical.com/docs/api-reference/overview/introduction). Terms: https://infisical.com/terms.
- get-secret still calls official GET /api/v4/secrets/{secretName}. Retrieved secretValue must not be logged and should stay in StudioQuack secret bindings. Official DELETE /api/v4/secrets/{secretName} sends a JSON body (`projectId`, `environment`, optional `secretPath`/`type`) — https://infisical.com/docs/api-reference/endpoints/secrets/delete. create-secret / update-secret / delete-secret JSON body is INPUT-only: put `projectId`, `environment`, and `secretValue` (writes) on INPUT (config fills GET query). Universal Auth token only (no client-id exchange in this handler).
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
