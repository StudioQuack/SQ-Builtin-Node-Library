# Azure Key Vault

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AZURE_KEY_VAULT`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Azure Key Vault data-plane REST (api-version 7.6)](https://learn.microsoft.com/en-us/rest/api/keyvault/secrets/get-secret/get-secret?view=rest-keyvault-secrets-7.6) at `https://`.

Inbound provider webhooks are **not** handled by this node. `listen` is `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event delivered through the StudioQuack engine's message-event ingress.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

FUNCTION ports: none. `jobType` is `custom-script-executor`. Outbound HTTPS only.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `get-secret` | GET | `{vaultHost}/secrets/{secretName}` | Official HTTP |
| `set-secret` | PUT | `{vaultHost}/secrets/{secretName}` | Official HTTP |
| `delete-secret` | DELETE | `{vaultHost}/secrets/{secretName}` | Official HTTP |
| `list-secrets` | GET | `{vaultHost}/secrets` | Official HTTP |
| `get-key` | GET | `{vaultHost}/keys/{keyName}` | Official HTTP |
| `encrypt` | POST | `{vaultHost}/keys/{keyName}/encrypt` | Official HTTP |
| `decrypt` | POST | `{vaultHost}/keys/{keyName}/decrypt` | Official HTTP |
| `get-certificate` | GET | `{vaultHost}/certificates/{certificateName}` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | get-secret | no | Official API operation. EXACT: get-secret, set-secret, delete-secret, list-secrets, get-key, encrypt, decrypt, get-certificate, listen. listen is BPMN ingress, not a socket. |
| `access_token` | password | yes |  | yes | Entra access token for https://vault.azure.net/.default. Sent as Authorization: Bearer. StudioQuack secret. Never logged. OfficialApiNode does not perform client-credentials or ... |
| `vaultHost` | string | yes |  | no | Vault host without scheme, e.g. myvault.vault.azure.net. |
| `api-version` | string | no | 7.6 | no | Key Vault REST api-version query. Default 7.6 (latest stable dotted version). 7.4 still works. Date-based versions such as 2025-07-01 are also documented on Learn. |
| `secretName` | string | no |  | no | Secret name for get/set/delete-secret. |
| `tags` | object | no |  | no | Request-body parameter `tags` for operation: set-secret. |
| `contentType` | string | no |  | no | Request-body parameter `contentType` for operation: set-secret. |
| `attributes` | object | no |  | no | Request-body parameter `attributes` for operation: set-secret. |
| `value` | password | no |  | yes | set-secret / encrypt plaintext from a StudioQuack secret binding. Decrypt uses ciphertext string. |
| `maxresults` | number | no |  | no | Query-string parameter `maxresults` for operation: list-secrets. |
| `keyName` | string | no |  | no | Key name for get-key / encrypt / decrypt. |
| `alg` | string | no |  | no | JsonWebKeyEncryptionAlgorithm, prefer RSA-OAEP-256. |
| `aad` | string | no |  | no | Request-body parameter `aad` for operations: encrypt, decrypt. |
| `iv` | string | no |  | no | Request-body parameter `iv` for operations: encrypt, decrypt. |
| `tag` | string | no |  | no | Request-body parameter `tag` for operation: decrypt. |
| `certificateName` | string | no |  | no | Certificate name for get-certificate. |
| `timeout_seconds` | number | no | 25 | no | Connect + read timeout. Default 25. Keep at or below the daemon invoke budget (~300s). |

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
- [Azure Key Vault data-plane REST (api-version 7.6) docs](https://learn.microsoft.com/en-us/rest/api/keyvault/secrets/get-secret/get-secret?view=rest-keyvault-secrets-7.6). Terms: https://www.microsoft.com/licensing/terms/.
- api-version is sent from queryFrom (config default 7.6). 7.4 still works. Learn also documents date-based 2025-07-01. get-secret still calls official GET; SecretBundle value must not be logged and should stay in StudioQuack secret bindings. Strip delete-secret value if returned. Data-plane only — not ARM vault CRUD. No purge/recover.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
