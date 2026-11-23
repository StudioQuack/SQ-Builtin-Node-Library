# HashiCorp Vault

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HASHICORP_VAULT`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Vault HTTP API v1 (KV v2 + Transit + token + leases)](https://developer.hashicorp.com/vault/api-docs) at `https://`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `vault_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `kv-get` | GET | `{vaultHost}/v1/{kvMount}/data/{secretPath}` | Official HTTP |
| `kv-put` | POST | `{vaultHost}/v1/{kvMount}/data/{secretPath}` | Official HTTP |
| `kv-delete` | DELETE | `{vaultHost}/v1/{kvMount}/data/{secretPath}` | Official HTTP |
| `kv-list` | GET | `{vaultHost}/v1/{kvMount}/metadata/{secretPath}` | GET `?list=true` (no LIST method) |
| `transit-encrypt` | POST | `{vaultHost}/v1/{transitMount}/encrypt/{keyName}` | Official HTTP |
| `transit-decrypt` | POST | `{vaultHost}/v1/{transitMount}/decrypt/{keyName}` | Official HTTP |
| `token-lookup` | GET | `{vaultHost}/v1/auth/token/lookup-self` | Official HTTP |
| `renew-lease` | POST | `{vaultHost}/v1/sys/leases/renew` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `vault_token` | password | yes | yes | Vault client token. Sent as X-Vault-Token (no prefix). StudioQuack secret binding. Never logged. Never on INPUT. |
| `vaultHost` | string | yes | no | Vault host without scheme, e.g. vault.example.com:8200. TLS only (https://). |
| `namespace` | string | no | no | Optional Vault Enterprise / HCP namespace. Sent as `X-Vault-Namespace` when non-blank; blank omits the header. |
| `kvMount` | string | no | no | KV secrets-engine mount (no slashes). Default secret. |
| `transitMount` | string | no | no | Transit engine mount. Default transit. |
| `list` | string | no | no | kv-list query. Default `true`. |
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
- [Vault HTTP API v1 (KV v2 + Transit + token + leases) docs](https://developer.hashicorp.com/vault/api-docs). Terms: https://www.hashicorp.com/legal/terms-of-service.
- Customer-operated Vault over TLS. kv-get still performs official GET; retrieved KV data and Transit plaintext must not be logged and should stay in StudioQuack secret bindings. kv-list uses GET ?list=true (equivalent to LIST). Do not unseal, manage policies, or wrap the CLI.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
