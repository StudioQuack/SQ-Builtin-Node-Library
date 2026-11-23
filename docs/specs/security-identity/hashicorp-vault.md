# HashiCorp Vault

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/hashicorp-vault/`
- **Researched:** 2026-08-20
- **Official API:** Vault HTTP API v1 (`/v1/…`) — KV v1, KV v2, Transit, Token, AppRole, Kubernetes, Leases
- **Docs:** https://developer.hashicorp.com/vault/api-docs
- **Auth:** token | approle | kubernetes — secret bindings: `vaultToken`, `roleId`+`secretId`, `kubernetesJwt`

## Scope

One node for a **customer-operated** Vault cluster (self-host or HashiCorp-hosted Dedicated). The handler talks to the Vault HTTP API over TLS using JDK HTTP only. Designer picks `operation`; the handler maps to KV, Transit, token lookup, or lease renew.

**Secret-value rule (all vault nodes):** GET/access never emit KV `data`, Transit plaintext, or token `id` on OUTPUT. Those bytes are written into StudioQuack secret bindings and returned as `secretRef`. PUT/create values are read from password config or a secret-typed input the engine treats as secret. Never log secret material. AppRole is recommended over root tokens.

This node does **not** unseal Vault, manage policies, or wrap the CLI.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `kv-get` | GET | v1: `/v1/{kvMount}/{path}` · v2: `/v1/{kvMount}/data/{path}?version=` | Read a secret; bind payload as `secretRef` |
| `kv-put` | POST | v1: `/v1/{kvMount}/{path}` · v2: `/v1/{kvMount}/data/{path}` | Create/update; body values from SQ secrets |
| `kv-delete` | DELETE | v1: `/v1/{kvMount}/{path}` · v2: `/v1/{kvMount}/data/{path}` | Delete secret (v2: latest version only) |
| `kv-list` | LIST or GET `?list=true` | v1: `/v1/{kvMount}/{path}` · v2: `/v1/{kvMount}/metadata/{path}` | List key names at a folder (not values) |
| `transit-encrypt` | POST | `/v1/{transitMount}/encrypt/{name}` | Encrypt; plaintext from SQ secret binding |
| `transit-decrypt` | POST | `/v1/{transitMount}/decrypt/{name}` | Decrypt; plaintext → `secretRef` |
| `token-lookup` | GET or POST | `/v1/auth/token/lookup-self` or `/v1/auth/token/lookup` | Token metadata; **strip `id`** |
| `renew-lease` | POST | `/v1/sys/leases/renew` | Extend a **secret** lease (`lease_id`). Token leases are **not** this path |

Dropped (not catalogued): KV v2 patch/undelete/destroy/metadata CRUD/recover/snapshots; Transit rewrap/sign/hmac/datakey; `auth/token/renew` (use a dedicated token-renew only if catalog adds it); AppRole/K8s **login as operations**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `kv-get` | no | Chooses the row above |
| `auth` | select | yes | `approle` | no | `token` \| `approle` \| `kubernetes` |
| `baseUrl` | string | yes | empty | no | Vault origin, `https` only. Example: `https://vault.example.com:8200` |
| `namespace` | string | no | empty | no | Vault Enterprise / HCP namespace. Sent as `X-Vault-Namespace` via extraHeaders when non-blank. HCP Dedicated often `admin`. |
| `kvMount` | string | no | `secret` | no | KV secrets-engine mount (no leading/trailing `/`) |
| `kvVersion` | select | no | `2` | no | `1` \| `2`. Must match the mount |
| `transitMount` | string | no | `transit` | no | Transit engine mount |
| `approleMount` | string | no | `approle` | no | AppRole auth mount |
| `kubernetesMount` | string | no | `kubernetes` | no | Kubernetes auth mount |
| `vaultToken` | password | if `auth=token` | empty | yes | Client token. Header `X-Vault-Token` |
| `roleId` | password | if `auth=approle` | empty | yes | AppRole `role_id` |
| `secretId` | password | if `auth=approle` | empty | yes | AppRole `secret_id` |
| `kubernetesRole` | string | if `auth=kubernetes` | empty | no | Role name for `/auth/kubernetes/login` |
| `kubernetesJwt` | password | if `auth=kubernetes` | empty | yes | Service-account JWT (`jwt` field) |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |

TLS is required. `http://` `baseUrl` → `{ error }` with no request. Certificate verification on. `Redirect.NEVER`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Secret-typed fields only for PUT/encrypt plaintext |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never tokens, KV data, or Transit plaintext |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = kv-get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Secret path relative to the mount (no `data/` prefix; handler inserts for v2) |
| `version` | number | no | KV v2 only. Query `version`. Omit → latest |

### `operation = kv-put`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Secret path relative to the mount |
| `data` | object (secret-typed values) | yes | KV map. **Each value** from SQ secret bindings, not logged fields. v1: body is this map. v2: `{ "data": <map>, "options": { "cas": … } }` |
| `cas` | number | no | KV v2 `options.cas` |

### `operation = kv-delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Secret path. v2 DELETE of `/data/` removes the **latest** version only |

### `operation = kv-list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `path` | string | yes | Folder path. Must be a directory; list on a leaf returns 404 |
| `exclude_deleted` | boolean | no | KV v2 query `exclude_deleted` |

### `operation = transit-encrypt`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Transit key name |
| `plaintext` | password / secret-typed | yes | **Not** on a logged field. Handler base64-encodes for Vault `plaintext` |
| `context` | string | no | Base64 context if key derivation is on |
| `key_version` | number | no | Vault `key_version` |
| `nonce` | string | no | Base64 nonce (convergent / legacy keys) |
| `associated_data` | string | no | Base64 AAD for AEAD |

### `operation = transit-decrypt`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Transit key name |
| `ciphertext` | string | yes | Vault ciphertext (`vault:vN:…`). Ciphertext is not treated as a secret binding |
| `context` | string | no | Base64 context |
| `nonce` | string | no | Base64 nonce |
| `associated_data` | string | no | Base64 AAD |

### `operation = token-lookup`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `token` | password / secret-typed | no | If set: POST `/auth/token/lookup` with body `{ "token" }`. If omitted: GET `/auth/token/lookup-self` using the session token |

### `operation = renew-lease`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `lease_id` | string | yes | Vault `lease_id` (e.g. `aws/creds/deploy/…`). **Not** a token |
| `increment` | number | no | Requested extension **seconds** (`increment` on `/sys/leases/renew`) |

## Output fields (by operation)

Common failure fields: `error` (string), `http_status` (number), `retry_after_seconds` (number). Vault error body uses `{ "errors": [ "…" ] }` — copy messages **after** redacting anything that looks like a token or KV value.

### `kv-get` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `secretRef` | string | 2xx | Handle for the KV payload. v1: entire `data` map. v2: inner `data.data` map. **Do not** emit `data` |
| `lease_duration` | number | v1 if present | Advisory TTL from Vault |
| `renewable` | boolean | if present | From Vault envelope |
| `metadata` | object | KV v2 | `created_time`, `deletion_time`, `destroyed`, `version`, `custom_metadata` only |

### `kv-put` (2xx / 204)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `metadata` | object | KV v2 200 | `created_time`, `deletion_time`, `destroyed`, `version`, `custom_metadata` |

### `kv-delete` (2xx / 204)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |

### `kv-list` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `keys` | string[] | 2xx | Vault `data.keys` (names only; folders end with `/`) |

Empty list is HTTP 404 with `{ "errors": [] }` — map to `{ status: "ok", keys: [] }`, not a hard error.

### `transit-encrypt` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `ciphertext` | string | 2xx | Vault `data.ciphertext` (safe to emit) |
| `key_version` | number | if present | From `data` |

### `transit-decrypt` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `secretRef` | string | 2xx | Handle for decoded plaintext. **Do not** emit `plaintext` |

### `token-lookup` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `accessor` | string | 2xx | Token accessor |
| `creation_time` | number | if present | Unix seconds |
| `creation_ttl` | number | if present | |
| `display_name` | string | if present | |
| `expire_time` | string | if present | |
| `num_uses` | number | if present | |
| `orphan` | boolean | if present | |
| `path` | string | if present | Auth path |
| `policies` | string[] | if present | |
| `renewable` | boolean | if present | |
| `ttl` | number | if present | |
| `entity_id` | string | if present | |

**Omit `id`** (that field is the token). Omit `meta` values that may hold usernames if the operator’s policy treats them as PII — **UNVERIFIED** product default: include `meta` keys only, not values, unless docs say otherwise. Safer default: omit `meta` entirely.

### `renew-lease` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `lease_id` | string | 2xx | Echoed lease |
| `renewable` | boolean | 2xx | |
| `lease_duration` | number | 2xx | New TTL seconds |

Vault: token leases **cannot** be renewed on `/sys/leases/renew`. Wrong `lease_id` → `{ error }` from Vault.

## Legal

- HTTP API: https://developer.hashicorp.com/vault/api-docs
- KV v1: https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v1
- KV v2: https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v2
- Transit: https://developer.hashicorp.com/vault/api-docs/secret/transit
- Token: https://developer.hashicorp.com/vault/api-docs/auth/token
- AppRole: https://developer.hashicorp.com/vault/api-docs/auth/approle
- Kubernetes: https://developer.hashicorp.com/vault/api-docs/auth/kubernetes
- Leases: https://developer.hashicorp.com/vault/api-docs/system/leases
- HashiCorp Terms of Service: https://www.hashicorp.com/legal/terms-of-service
- Rate limits: no global public RPM. HTTP **429** for lease quotas / overload; **412** eventual consistency (Enterprise) — retry with backoff. Request size default **32 MiB** server-side; this node caps bodies lower.
- PII: secret payloads and tokens are credentials. Namespace and path names must not encode secrets (Vault list does not filter by policy on key names).
- Official-API-first: no CLI scrape, no UI session cookies.

## Implementation notes

- JDK `HttpClient` only. Headers: `X-Vault-Token`, optional `X-Vault-Namespace`, `X-Vault-Request: true`, `Content-Type: application/json` on POST.
- **Auth:** `token` uses `vaultToken`. `approle`: POST `/v1/auth/{approleMount}/login` `{ "role_id", "secret_id" }` → use `auth.client_token` for this invoke only; never log or output it. `kubernetes`: POST `/v1/auth/{kubernetesMount}/login` `{ "role", "jwt" }`.
- LIST: JDK has no LIST method — use `GET` with `?list=true` (Vault documents this as equivalent).
- Timeouts: connect 10 s, request `timeout_seconds`. Body cap **256 KiB** JSON (Transit plaintext after base64). Response cap **1 MiB**.
- Retry: **no** inner retry loop. Map 429 / 5xx / timeout → `{ error, http_status, retry_after_seconds }` (`Retry-After` or 30). 412 → 5 s.
- TLS verified. Do not implement `insecureSkipVerify`.
- Redact: token, role_id, secret_id, jwt, KV values, Transit plaintext, `client_token`.
