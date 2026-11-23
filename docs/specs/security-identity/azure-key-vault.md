# Azure Key Vault

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/azure-key-vault/`
- **Researched:** 2026-08-20
- **Official API:** Azure Key Vault data-plane REST, API version **2025-07-01** (Learn still indexes some pages under view `rest-keyvault-*-7.4`, which resolve to 2025-07-01)
- **Docs:** https://learn.microsoft.com/en-us/rest/api/keyvault/
- **Auth:** entraClientCredentials | managedIdentity — secret bindings: `clientSecret`; managed identity uses IMDS (no SQ secret)

## Scope

One node for a **customer** Azure Key Vault (or compatible vault URL). Data-plane calls go to `{vaultUrl}` with `api-version=2025-07-01`. This is **not** the ARM vault-management API (create vault, access-policy CRUD on the resource).

**Secret-value rule:** `get-secret` returns metadata + `secretRef` — never SecretBundle `value`. `set-secret` `value` comes from SQ secret bindings. `delete-secret` may return `value` on DeletedSecretBundle — **strip it**. `decrypt` plaintext → `secretRef`. `encrypt` ciphertext may be emitted. `get-key` returns the **public** JWK (`n`,`e`,`kty`,…) — omit private fields `d`,`p`,`q`,`dp`,`dq`,`qi`,`k` if ever present. `get-certificate` may emit `cer` (public X.509); do not fetch the backing secret’s private key.

Purge-protection / soft-delete: DELETE is recoverable when the vault recovery level is not `Purgeable`. This node does **not** implement purge or recover.

RBAC vs access policies: the vault’s permission model is an Azure-side choice. The app needs data-plane RBAC (e.g. Key Vault Secrets User / Crypto User) **or** an access policy with `secrets/get` etc. Handler cannot switch models.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `get-secret` | GET | `{vaultUrl}/secrets/{name}/{version}?api-version=2025-07-01` | Latest if `version` omitted |
| `set-secret` | PUT | `{vaultUrl}/secrets/{name}?api-version=2025-07-01` | Create or new version |
| `delete-secret` | DELETE | `{vaultUrl}/secrets/{name}?api-version=2025-07-01` | Soft-delete entire secret (not one version) |
| `list-secrets` | GET | `{vaultUrl}/secrets?api-version=2025-07-01&maxresults=` | Metadata only (`id`, attributes, tags) |
| `get-key` | GET | `{vaultUrl}/keys/{name}/{version}?api-version=2025-07-01` | Public key material |
| `encrypt` | POST | `{vaultUrl}/keys/{name}/{version}/encrypt?api-version=2025-07-01` | Encrypt one block; plaintext from SQ secret |
| `decrypt` | POST | `{vaultUrl}/keys/{name}/{version}/decrypt?api-version=2025-07-01` | Decrypt → `secretRef` |
| `get-certificate` | GET | `{vaultUrl}/certificates/{name}/{version}?api-version=2025-07-01` | Certificate bundle (public `cer`) |

Dropped: purge, recover, list-deleted, wrap/unwrap, sign/verify, backup/restore, ARM vault CRUD, Managed HSM-only paths.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get-secret` | no | Chooses the row above |
| `auth` | select | yes | `entraClientCredentials` | no | `entraClientCredentials` \| `managedIdentity` |
| `vaultUrl` | string | yes | empty | no | Vault origin, `https` only. Example: `https://myvault.vault.azure.net` (no trailing slash) |
| `tenantId` | string | if entra | empty | no | Directory tenant |
| `clientId` | string | if entra | empty | no | App registration id |
| `clientSecret` | password | if entra | empty | yes | App client secret |
| `managedIdentityClientId` | string | no | empty | no | User-assigned identity client id (IMDS `client_id`) |
| `azureAuthorityHost` | string | no | `https://login.microsoftonline.com` | no | Sovereign clouds (US Gov, China) |
| `tokenScope` | string | no | `https://vault.azure.net/.default` | no | Data-plane scope |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

Token: POST `{azureAuthorityHost}/{tenantId}/oauth2/v2.0/token` with `grant_type=client_credentials`, `client_id`, `client_secret`, `scope={tokenScope}`. Managed identity: GET `http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://vault.azure.net` with header `Metadata: true`. **Only works on Azure compute.** If IMDS is unreachable → `{ error }` (do not fall back to HTTP vault). Header `Authorization: Bearer {access_token}`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No secret `value`, no client secret |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = get-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretName` | string | yes | Path `secret-name` |
| `secretVersion` | string | no | Omit for latest |

### `operation = set-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretName` | string | yes | Pattern `^[0-9a-zA-Z-]+$` |
| `value` | password / secret-typed | yes | Body `value`. Never a logged field |
| `contentType` | string | no | e.g. `text/plain` |
| `tags` | object | no | String map |
| `enabled` | boolean | no | `attributes.enabled` |
| `exp` | number | no | `attributes.exp` Unix |
| `nbf` | number | no | `attributes.nbf` Unix |

### `operation = delete-secret`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `secretName` | string | yes | |

### `operation = list-secrets`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `maxresults` | number | no | 1–25 (Azure max) |
| `nextLink` | string | no | Full `nextLink` URL from a prior page. Must be same `vaultUrl` host — reject other hosts (SSRF) |

### `operation = get-key`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyName` | string | yes | |
| `keyVersion` | string | no | Omit for latest |

### `operation = encrypt` | `decrypt`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyName` | string | yes | |
| `keyVersion` | string | yes | Azure path marks version required; empty segment for latest is **UNVERIFIED** — require an explicit version **or** allow empty and omit the segment (prefer omit-segment for latest, same as GET key) |
| `alg` | string | yes | `JsonWebKeyEncryptionAlgorithm`: prefer `RSA-OAEP-256`. Avoid `RSA1_5` / `RSA-OAEP` (SHA-1) unless operator opts in |
| `value` | password (encrypt) or string (decrypt) | yes | Encrypt: plaintext from SQ secret, base64url. Decrypt: ciphertext string (not a vault secret value) |
| `iv` | string | no | Base64url IV (symmetric) |
| `aad` | string | no | Base64url AAD |
| `tag` | string | no | Base64url tag (decrypt GCM) |

### `operation = get-certificate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `certificateName` | string | yes | |
| `certificateVersion` | string | no | Omit for latest |

## Output fields (by operation)

Azure errors: `{ "error": { "code", "message" } }`. Map to `{ error, http_status }`. 429 throttling is documented.

### `get-secret` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | 2xx | Secret identifier URL |
| `contentType` | string | if present | |
| `kid` | string | if present | Backing key id |
| `managed` | boolean | if present | |
| `attributes` | object | if present | `enabled`, `created`, `updated`, `exp`, `nbf`, `recoveryLevel`, `recoverableDays` |
| `tags` | object | if present | |
| `secretRef` | string | 2xx | Handle for `value`. **Do not** emit `value` |

### `set-secret` (2xx)

Same as get-secret metadata + `secretRef` **omitted** (do not round-trip the value). Include `id`, `attributes`, `contentType`, `tags`.

### `delete-secret` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | 2xx | |
| `recoveryId` | string | if present | Soft-delete locator |
| `deletedDate` | number | if present | |
| `scheduledPurgeDate` | number | if present | |
| `attributes` | object | if present | Includes `recoveryLevel` (purge-protection signal) |

**Strip `value`.**

### `list-secrets` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `value` | array | 2xx | `SecretItem[]`: `id`, `contentType`, `attributes`, `tags`, `managed` — **no secret bytes** |
| `nextLink` | string | if present | Opaque next page URL |

### `get-key` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `key` | object | 2xx | Public JWK: `kid`, `kty`, `key_ops`, `n`, `e`, `crv`, `x`, `y` as present. **Drop** `d`,`p`,`q`,`dp`,`dq`,`qi`,`k`,`key_hsm` |
| `attributes` | object | if present | |
| `tags` | object | if present | |
| `managed` | boolean | if present | |

### `encrypt` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `kid` | string | 2xx | |
| `value` | string | 2xx | Ciphertext (base64url) — not vault secret plaintext |
| `iv` | string | if present | |
| `tag` | string | if present | |
| `aad` | string | if present | |

### `decrypt` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `kid` | string | 2xx | |
| `secretRef` | string | 2xx | Handle for decoded plaintext. **Do not** emit `value` |
| `iv` | string | if present | |
| `tag` | string | if present | |

### `get-certificate` (2xx)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | 2xx | |
| `kid` | string | if present | |
| `sid` | string | if present | Backing secret id (do not GET it) |
| `x5t` | string | if present | Thumbprint |
| `cer` | string | if present | Public cert bytes (base64) |
| `contentType` | string | if present | |
| `attributes` | object | if present | |
| `policy` | object | if present | Management policy (no private key) |
| `tags` | object | if present | |

## Legal

- REST index: https://learn.microsoft.com/en-us/rest/api/keyvault/
- Get/Set/Delete/List secrets, Get key, Encrypt, Get certificate: Learn pages under `rest/api/keyvault/` (version **2025-07-01**)
- Service limits: https://learn.microsoft.com/en-us/azure/key-vault/general/service-limits — secrets “all other transactions” **4000 / 10 s / vault / region**; CREATE secret **300 / 10 s** shared with import cert/key; subscription-wide 5× vault limit. HTTP **429**.
- Microsoft Online / Azure legal: operator’s Microsoft Customer Agreement / Online Services Terms
- Google-style APIs Terms do not apply. Azure ToS bind the Azure customer.
- PII: secret values and certificate subjects may be personal data. Residency follows the vault’s Azure region.
- Official-API-first: no Portal scrape.

## Implementation notes

- JDK HTTP. TLS required on `vaultUrl`. `Redirect.NEVER`.
- Timeouts: connect 10 s, request `timeout_seconds`. Body cap **256 KiB**. Response cap **1 MiB**.
- Retry: no inner loop. 429 → `{ error, http_status, retry_after_seconds }` from `Retry-After` (Azure throttling guidance) or 10.
- Never log `clientSecret`, IMDS tokens, secret `value`, decrypt plaintext.
- `nextLink` host must equal `vaultUrl` host.
