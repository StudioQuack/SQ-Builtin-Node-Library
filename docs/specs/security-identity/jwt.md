# JWT

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/jwt/`
- **Researched:** 2026-08-20
- **Official API:** JSON Web Token (JWT) as a JWS — RFC 7519 + RFC 7515 + RFC 7518
- **Docs:** [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html) (JWT), [RFC 7515](https://www.rfc-editor.org/rfc/rfc7515.html) (JWS), [RFC 7518](https://www.rfc-editor.org/rfc/rfc7518.html) (JWA). JWE [RFC 7516](https://www.rfc-editor.org/rfc/rfc7516.html) is **out of v1**. JWT BCP: [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html).
- **Auth:** none (local JOSE). Key material — secret bindings: `hmacSecret` and/or `privateKeyPem` / `publicKeyPem`

## Scope

Local **JWS** encode/decode utility (catalog: sign, verify, decode). No network. No vendor. Compact serialization only (RFC 7515 §3.1 / RFC 7519 §3): `BASE64URL(JOSE Header) || '.' || BASE64URL(payload) || '.' || BASE64URL(signature)`.

**Reject `alg=none`** on sign and verify (RFC 7519 §6 Unsecured JWTs; RFC 7518 §3.6; RFC 8725: libraries SHOULD NOT consume `none` unless explicitly requested — this node never requests it).

**`decode` without verify is unsafe** (catalog). It parses header + claims and MUST set `unverified: true`. It MUST still refuse to imply validity.

**JWE (RFC 7516) is not built in v1.** Catalog is JWS encode/decode only. Nested JWT that is JWE-encrypted → `{ error }`. Do not implement `encrypt` / `decrypt`.

OAuth/OIDC token endpoint and SAML assertion work lives on **SAML/OAuth2 Tokens**. This node does not call token URLs.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `sign` | *(local)* | RFC 7519 §7.1 + RFC 7515 §5.1 | Create a compact JWS JWT from a claims object |
| `verify` | *(local)* | RFC 7519 §7.2 + RFC 7515 §5.2 | Validate signature, `alg`, and registered claims |
| `decode` | *(local)* | RFC 7515 §3.1 split + JSON parse | **Unsafe** inspect header/claims without verifying |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `verify` | no | Chooses the row above |
| `keyType` | select | yes | `passphrase` | no | `passphrase` = HMAC key for HS*. `pem` = RSA/EC key for RS*/PS*/ES*. |
| `alg` | select | yes | `HS256` if `keyType=passphrase`, else `RS256` | no | Allowlist only: `HS256`, `HS384`, `HS512`, `RS256`, `RS384`, `RS512`, `ES256`, `ES384`, `ES512`, `PS256`, `PS384`, `PS512` (RFC 7518 §3.1). **`none` is not a selectable value.** |
| `hmacSecret` | password | no | empty | yes | HMAC key for HS*. Required when `keyType=passphrase` and operation is `sign` or `verify`. RFC 7518: HS256 key SHOULD be ≥ 256 bits. Never on INPUT. Never logged. |
| `privateKeyPem` | password | no | empty | yes | PKCS#8 / PKCS#1 PEM private key for `sign` (and optionally `verify` for RS/PS/ES). Secret. |
| `publicKeyPem` | password | no | empty | yes | SPKI PEM public key (or cert PEM) for `verify` when `keyType=pem`. If omitted, derive public from `privateKeyPem`. Secret. |
| `clockSkewSeconds` | number | no | `60` | no | Leeway for `exp` / `nbf` (RFC 7519 §4.1.4–4.1.5 “small leeway”). Cap 300. |

`keyType` vs `alg` mismatch (HS* with pem, or RS/ES/PS with passphrase) → `{ error }` before crypto. Do not choose the algorithm from the token header alone (RFC 8725 algorithm-confusion): **configured `alg` is the only acceptable `alg`**. Token `alg` MUST equal config `alg` on verify.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No HMAC secrets, no PEM |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Compact JWT on `sign` is sensitive (may authorize). Never log compact tokens or keys |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = sign`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `claims` | object | yes | JWT Claims Set (RFC 7519 §4). JSON object. Cap serialized size **8192** bytes. |
| `headers` | object | no | Extra JOSE Header params (RFC 7515 §4). `alg` is taken from **config**, not from this object — if `headers.alg` is present and differs, `{ error }`. Reject `alg=none`. Optional `kid`, `typ` (default `JWT`). |
| `issuer` | string | no | Sets `iss` if not already in `claims` |
| `subject` | string | no | Sets `sub` |
| `audience` | string or array | no | Sets `aud` (string or array of strings, RFC 7519 §4.1.3) |
| `expiresInSeconds` | number | no | Sets `exp` = now + N if `exp` absent. Floor 1, cap 63072000 (2y). |
| `notBeforeSeconds` | number | no | Sets `nbf` = now − 0 + offset if needed; or absolute via `claims.nbf` |

Registered claims that are present MUST use RFC 7519 types: `exp`/`nbf`/`iat` are NumericDate (seconds since epoch).

### `operation = verify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `token` | string | yes | Compact JWS. Cap **16384** chars. Never log. |
| `issuer` | string | no | If set, `iss` MUST match (RFC 7519 §4.1.1) |
| `audience` | string | no | If set, `aud` MUST contain this value (§4.1.3) or reject |
| `subject` | string | no | If set, `sub` MUST match |

Verify steps (RFC 7519 §7.2 + RFC 7515 §5.2), fail closed:

1. Compact form has exactly two period separators (three parts). JWE five-part form → `{ error }` (not v1).
2. Parse JOSE Header JSON. `alg` MUST equal **config `alg`**. Reject missing `alg`, `none`, `HS256` when `keyType=pem`, etc.
3. Verify JWS signature with the configured key (HMAC or RSA/EC/PSS per RFC 7518).
4. Parse claims JSON. Reject non-object payloads.
5. If `exp` present: now < exp + skew. If `nbf` present: now ≥ nbf − skew.
6. Optional issuer/audience/subject checks when provided.

Do not honor `jku` / `x5u` / `jwk` headers as trust (RFC 7515; RFC 8725). Keys come only from config secret bindings.

### `operation = decode`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `token` | string | yes | Compact JWS (or unsecured three-part with empty signature). Cap **16384** chars. |

Parse Base64URL header and payload only. **Do not verify.** Output `unverified: true` always. If `alg` is `none`, still parse but set `algNone: true` and do **not** set `valid`. This operation is for debugging/inspection in trusted workflows; designer copy MUST label it unsafe.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `token` | string | `sign` | Compact JWS. Sensitive. |
| `header` | object | `verify` / `decode` | JOSE Header (`alg`, `typ`, `kid`, …) |
| `claims` | object | `verify` / `decode` | JWT Claims Set |
| `valid` | boolean | `verify` | `true` only after signature + claim checks |
| `unverified` | boolean | `decode` | Always `true` |
| `algNone` | boolean | `decode` when header `alg` is `none` | Warn the operator |
| `error` | string | failure | No key material, no compact token echo beyond first 8 chars optional — **prefer no token fragment at all** |

Failed verify → `{ error }` (e.g. `invalid signature`, `token expired`, `algorithm not allowed`). Do not return `valid: false` plus claims for verify — fail closed so a careless edge cannot treat claims as trusted. (`decode` is the inspect path.)

## Legal

- IETF RFC 7515, 7518, 7519 (May 2015, Standards Track); RFC 7516 cited only to **exclude** JWE; RFC 8725 (JWT BCP).
- HMAC secrets and PEM keys are **credentials**. OS secret manager bindings only. Never logs, never engine variables.
- Signed JWTs often authorize access (bearer-like). Treat OUTPUT `token` as a secret in the redaction set.
- Claims may be personal data (`sub`, email, names). Minimize.
- Official-API-first: JOSE RFCs are the official surface. Local library; no vendor cloud.

## Implementation notes

- **Local only.** No HTTP. Prefer JDK cryptographic APIs (`Mac`, `Signature`, `KeyFactory`) plus RFC 7515 Base64URL. A well-known JOSE library may be used if it is not a vendor IdP SDK and if it is configured to **reject `none`** and to not fetch `jku`/`x5u`. This spec is not Java source.
- ES512 is ECDSA **P-521** + SHA-512 (RFC 7518), not a 512-bit curve named “P-512”.
- PS* is RSASSA-PSS (RFC 7518 §3.5).
- HMAC key length: reject HS256 secrets shorter than 32 bytes (RFC 7518 §3.2).
- RSA key size: reject modulus smaller than 2048 bits on sign/verify.
- Time: NumericDate is UTC seconds. Support `exp` beyond year 2038 (64-bit).
- Logs: operation, `alg`, `keyType`, outcome, payload size — never keys, never compact JWT, never claim values.
- Error mapping: crypto/parse failures → `{ error }` on OUTPUT, never throw past dispatch.
