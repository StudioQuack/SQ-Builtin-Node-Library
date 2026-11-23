# SAML / OAuth2 Tokens

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/saml-oauth2-tokens/`
- **Researched:** 2026-08-20
- **Official API:** OAuth 2.0 (RFC 6749) + Bearer (RFC 6750) + Token Introspection (RFC 7662) + Token Revocation (RFC 7009); JWT/JWS for access/ID tokens (RFC 7519, RFC 7515); OpenID Connect Core 1.0; OASIS SAML 2.0 Assertions
- **Docs:** [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749.html), [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750.html), [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662.html), [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009.html), [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html), [RFC 7515](https://www.rfc-editor.org/rfc/rfc7515.html), [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636.html) (PKCE, optional on authorize/exchange), [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html), [OASIS SAML 2.0 Core](https://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf) (`sstc-saml-core-2.0-os`). JWT BCP: [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html).
- **Auth:** OAuth confidential client (RFC 6749 §2.3.1) — secret binding: `clientSecret`. SAML validation uses configured IdP certificate (not a client secret).

## Scope

**Spec utilities, not a vendor.** One node that (1) builds an OAuth 2.0 authorization-code URL, (2) talks to operator-configured HTTPS token / introspection / revocation endpoints, and (3) decodes or validates a SAML 2.0 `<Assertion>`.

Signatures on JWT access tokens / OIDC ID Tokens are checked **only** with the configured `jwksUrl` or `idpCert`. XML signatures on SAML assertions are checked **only** with configured `idpCert`. **Do not skip TLS.** **Reject `alg=none`** (RFC 7519 §6 Unsecured JWTs; RFC 7518 §3.6; RFC 8725; OIDC Core §2: ID Tokens MUST NOT use `none` except a registration case this node does not implement).

**Do not duplicate the JWT node.** Compact JWS **sign** / general **verify** / unsafe **decode** live on `jwt`. This node may verify a JWT **access_token** or OIDC **id_token** returned from the token endpoint (or supplied for local JWT-shaped access-token checks as part of introspect fallback) using the same reject-`none` / configured-keys rules.

**Not built:** RFC 6749 implicit grant (§4.2), resource-owner password grant (§4.3), device authorization (RFC 8628), SAML AuthnRequest/Response protocol client, SAML encryption (Core §6) decrypt, JWE nested ID Tokens (fail closed; do not decrypt).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `oauth-authorize-url` | *(local)* | RFC 6749 §4.1.1 query on `authUrl` | Build `response_type=code` authorization URI. No network. |
| `oauth-exchange-code` | POST | RFC 6749 §4.1.3 token endpoint (`tokenUrl`) | `grant_type=authorization_code` |
| `oauth-client-credentials` | POST | RFC 6749 §4.4.2 token endpoint | `grant_type=client_credentials` (confidential client) |
| `oauth-refresh` | POST | RFC 6749 §6 token endpoint | `grant_type=refresh_token` |
| `oauth-introspect` | POST | RFC 7662 §2.1 (`introspectionUrl`) | Active-state of an access or refresh token |
| `oauth-revoke` | POST | RFC 7009 §2.1 (`revocationUrl`) | Invalidate access or refresh token |
| `saml-decode-assertion` | *(local)* | OASIS SAML 2.0 Core §2.3 | Parse Assertion XML. **Unsafe** without `saml-validate-assertion`. |
| `saml-validate-assertion` | *(local)* | SAML Core §2.3, §2.5, §5 | Verify XML signature with `idpCert` + Conditions / audience / time. |

Token, introspection, revocation, and JWKS URLs: **HTTPS only** (RFC 6749 §3.1 / §3.2; RFC 6750 TLS mandatory; RFC 7662 §2; RFC 7009 §2). `http://` → `{ error }`, no request.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `oauth-authorize-url` | no | Chooses the row above |
| `clientId` | string | no | empty | no | RFC 6749 `client_id`. Required for all OAuth ops except SAML-only. |
| `clientSecret` | password | no | empty | yes | Confidential-client secret. HTTP Basic `client_id:client_secret` (RFC 6749 §2.3.1) on token / introspect / revoke. Never on INPUT. Never logged. Required for token/introspect/revoke. |
| `tokenUrl` | string | no | empty | no | Absolute HTTPS token endpoint. Required for exchange / client-credentials / refresh. |
| `authUrl` | string | no | empty | no | Absolute HTTPS authorization endpoint. Required for `oauth-authorize-url`. |
| `introspectionUrl` | string | no | empty | no | Absolute HTTPS RFC 7662 endpoint. Required for `oauth-introspect`. |
| `revocationUrl` | string | no | empty | no | Absolute HTTPS RFC 7009 endpoint. Required for `oauth-revoke`. **Catalog gap:** not listed on the catalog row; required to implement revoke. |
| `jwksUrl` | string | no | empty | no | Absolute HTTPS JWKS. Used to validate JWT `access_token` / `id_token` when present. |
| `idpCert` | password | no | empty | yes | PEM X.509 of the IdP signing cert (SAML XML-DSig; optional static JWT RS/ES/PS verify if no JWKS). Secret binding — PEM is key material. Never logged. |
| `redirectUri` | string | no | empty | no | Default `redirect_uri` (RFC 6749 §3.1.2). HTTPS, or loopback. |
| `timeout_seconds` | number | no | `20` | no | Connect + read timeout for HTTPS calls. Floor 1, cap 60. |

At least one of `jwksUrl` or `idpCert` is required for `saml-validate-assertion` (`idpCert` required) and for optional JWT access/ID token signature checks.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No `clientSecret`, no PEM |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Access/refresh/ID tokens **are** the product of token ops and appear on OUTPUT for downstream nodes; they **must never be logged**. Never echo `clientSecret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = oauth-authorize-url`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `redirectUri` | string | no | Overrides config. RFC 6749 §4.1.1 `redirect_uri`. |
| `scope` | string | no | Space-delimited (RFC 6749 §3.3). Include `openid` for OIDC (OIDC Core §3.1.2). |
| `state` | string | no | CSRF binding (RFC 6749 §10.12). If omitted, the handler **generates** a high-entropy opaque `state` and returns it. |
| `nonce` | string | no | OIDC nonce (OIDC Core §3.1.2.1). Optional; generate if `scope` contains `openid` and nonce omitted. |
| `codeChallenge` | string | no | RFC 7636 `code_challenge` (S256). Optional PKCE. |
| `codeChallengeMethod` | string | no | `S256` (only). `plain` is **rejected**. |

Handler sets `response_type=code` and `client_id`. Does **not** set `response_type=token` / `id_token`.

### `operation = oauth-exchange-code`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `code` | string | yes | Authorization code (RFC 6749 §4.1.3). Never log. |
| `redirectUri` | string | no | MUST match the authorize request if that request included `redirect_uri`. |
| `codeVerifier` | string | no | RFC 7636 `code_verifier` when PKCE was used. Never log. |

Body: `application/x-www-form-urlencoded; charset=UTF-8`. Client auth: HTTP Basic. `Cache-Control: no-store` on the response MUST be honored by not writing tokens to disk/logs.

### `operation = oauth-client-credentials`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `scope` | string | no | RFC 6749 §4.4.2 `scope` |

### `operation = oauth-refresh`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `refreshToken` | string | yes | RFC 6749 §6 `refresh_token`. Never log. Never on config. |
| `scope` | string | no | MUST NOT exceed originally granted scope |

### `operation = oauth-introspect`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `token` | string | yes | RFC 7662 `token`. Never log. |
| `tokenTypeHint` | string | no | `access_token` \| `refresh_token` (RFC 7009 registry) |
| `issuer` | string | no | Expected `iss` when locally verifying a JWT access token (JWKS/`idpCert` path) |
| `audience` | string | no | Expected `aud` for that local JWT check |

If `introspectionUrl` is empty **and** `token` is a compact JWS **and** `jwksUrl`/`idpCert` is set: the handler MAY locally verify the JWT (`exp`/`nbf`/`iss`/`aud`) and return a synthetic `{ active }` — this is the allowed JWT-validate overlap. If the token is opaque, missing introspection URL → `{ error }`. Do not call a guessed `/introspect` path.

### `operation = oauth-revoke`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `token` | string | yes | RFC 7009 `token`. Never log. |
| `tokenTypeHint` | string | no | `access_token` \| `refresh_token` |

RFC 7009: invalid token still returns HTTP 200 (do not treat as error). HTTP 401 on client auth failure is an error.

### `operation = saml-decode-assertion`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `assertion` | string | yes | SAML 2.0 Assertion: raw XML, or Base64, or deflate+Base64 (HTTP-Redirect style). Cap **256 KiB** decoded. |

**Unsafe:** parse only. Do not treat `valid=true`. Output MUST include `unverified: true`. Analogous to JWT `decode`.

### `operation = saml-validate-assertion`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `assertion` | string | yes | Same encodings as decode |
| `audience` | string | no | Expected AudienceRestriction (SAML Core §2.5.1.4). If config/input audience set, mismatch → invalid. |
| `recipient` | string | no | Expected SubjectConfirmationData `Recipient` if present |
| `clockSkewSeconds` | number | no | Default `60`. Applied to `NotBefore` / `NotOnOrAfter` (Core §2.5.1.2) |

Validation MUST: (1) require XML Signature over the Assertion (Core §5) using **configured `idpCert` only** — ignore untrusted `KeyInfo` as the sole trust anchor; (2) reject wrapping/multiple-Assertion tricks (verify the signed Assertion ID that was referenced); (3) evaluate Conditions time window; (4) evaluate AudienceRestriction when `audience` is provided. EncryptedAssertion without decrypt support → `{ error }` (SAML Core §6 not in v1).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `authorizationUrl` | string | `oauth-authorize-url` | Full URI (query encoded). Do not log if it contains a `code` (it should not). |
| `state` | string | `oauth-authorize-url` | CSRF state to persist until callback |
| `nonce` | string | `oauth-authorize-url` | When generated or passed through |
| `access_token` | string | token ops | RFC 6749 §5.1. **Sensitive.** OUTPUT only; never logs |
| `token_type` | string | token ops | Typically `Bearer` (RFC 6750) |
| `expires_in` | number | token ops | Seconds |
| `refresh_token` | string | token ops when issued | Sensitive |
| `scope` | string | token ops when present | Granted scope |
| `id_token` | string | OIDC token response | Compact JWT. If `jwksUrl`/`idpCert` set, verify before success; `alg=none` → `{ error }`. Do not copy verify/sign APIs from the JWT node. |
| `active` | boolean | `oauth-introspect` | RFC 7662 required member |
| `introspection` | object | `oauth-introspect` when `active=true` | `scope`, `client_id`, `username`, `token_type`, `exp`, `iat`, `nbf`, `sub`, `aud`, `iss`, `jti` as returned. Cap. |
| `revoked` | boolean | `oauth-revoke` HTTP 200 | `true` (RFC 7009 does not return token state) |
| `unverified` | boolean | `saml-decode-assertion` | Always `true` |
| `assertion` | object | SAML ops | Normalized: `id`, `version`, `issuer`, `issueInstant`, `subject`, `conditions`, `audience`, `authnStatements`, `attributes`. No raw XML in logs. |
| `valid` | boolean | `saml-validate-assertion` | Signature + conditions |
| `error` | string | failure | No secrets / tokens |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 / 30 |

Token error body (RFC 6749 §5.2): map `invalid_client`, `invalid_grant`, `unauthorized_client`, `invalid_scope` to `{ error, error_code }` without echoing the secret.

## Legal

- IETF RFCs 6749, 6750, 7009, 7662, 7515, 7519, 7636, 8725 — Standards Track / BCP. No vendor ToS for the protocol.
- OpenID Connect Core 1.0 (OIDF). OASIS SAML 2.0 Core (`sstc-saml-core-2.0-os`, 15 March 2005).
- Access tokens, refresh tokens, ID tokens, authorization codes, SAML assertions, and `clientSecret` are **credentials**. Never log. Minimize who receives OUTPUT.
- Assertions and ID Tokens are personal data (subject identifiers, emails, groups). GDPR: purpose limitation; do not write them into engine variables (REF-only).
- Do not disable TLS verification. Do not send tokens to `http://` endpoints.
- Official-API-first: RFCs and OASIS are the official surface. This node is not a product wrapper around a commercial IdP admin API (those are separate catalog rows).

## Implementation notes

- OAuth HTTPS: JDK `HttpClient` only, TLS verified, `Redirect.NEVER`. Timeouts = `timeout_seconds`. Body cap **100_000** bytes on token/introspect responses.
- Client authentication: `Authorization: Basic` base64url(`clientId:clientSecret`) per RFC 6749 §2.3.1. Do not put `client_secret` in the form body unless a specific AS requires it; prefer Basic. Never log the header.
- Content-Type: `application/x-www-form-urlencoded;charset=UTF-8`. Accept `application/json` on token/introspect.
- `alg` on any JWT this node verifies: allowlist intersection of `{RS,ES,PS}256/384/512` (and `HS*` only if a MAC secret were configured — **this node has no HMAC secret**; HS* JWT access tokens cannot be verified here). Reject `none`, empty `alg`, and algorithm/key-type mismatch (RFC 8725).
- JWKS fetch: HTTPS GET `jwksUrl`, cache in-memory with cap 16 keys / TTL 300 s per daemon process (optional). Pin to configured URL; no redirect to another host.
- SAML: XML parser with DTD/XXE **disabled**. Exclusive C14N as in SAML Core §5.4. Trust anchor = `idpCert` only.
- Redact logs: all token strings, codes, verifiers, PEM, assertion XML, attribute values. Log operation, HTTPS host, status, sizes.
- Do not auto-retry `invalid_grant` / `invalid_client`.
- Error mapping: HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
