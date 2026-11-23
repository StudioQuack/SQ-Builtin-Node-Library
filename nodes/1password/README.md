# 1Password

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_1PASSWORD`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [1Password Connect Server API 1.8.1](https://developer.1password.com/docs/connect/api-reference) at `https://`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `connect_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `list-vaults` | GET | `{connectHost}/v1/vaults` | Official HTTP |
| `get-vault` | GET | `{connectHost}/v1/vaults/{vaultUuid}` | Official HTTP |
| `list-items` | GET | `{connectHost}/v1/vaults/{vaultUuid}/items` | Official HTTP |
| `get-item` | GET | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}` | Official HTTP |
| `create-item` | POST | `{connectHost}/v1/vaults/{vaultUuid}/items` | Official HTTP |
| `replace-item` | PUT | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}` | Official HTTP |
| `patch-item` | PATCH | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}` | Official HTTP |
| `delete-item` | DELETE | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}` | Official HTTP |
| `list-files` | GET | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}/files` | Official HTTP |
| `get-file` | GET | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}/files/{fileUuid}` | Official HTTP |
| `get-file-content` | GET | `{connectHost}/v1/vaults/{vaultUuid}/items/{itemUuid}/files/{fileUuid}/content` | OUTPUT type `file` |
| `list-activity` | GET | `{connectHost}/v1/activity` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `connect_token` | password | yes | yes | Connect access token. Sent as Authorization: Bearer. StudioQuack secret. Never logged. Vault-scoped; customer-deployed Connect only. |
| `connectHost` | string | yes | no | Connect host without scheme, e.g. connect.internal:8443. TLS only. |
| `inline_content` | string | no | no | File JSON inline. Default `false`. |
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
- [1Password Connect Server API 1.8.1 docs](https://developer.1password.com/docs/connect/api-reference). Terms: https://1password.com/legal/terms-of-service.
- Customer-deployed Connect only — not a personal vault dump. get-item still calls official GET; fields[].value, passwordDetails, and file content must not be logged and should stay in StudioQuack secret bindings. get-file-content OUTPUT uses type file. Keep inline_content false. Do not scrape 1password.com.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
