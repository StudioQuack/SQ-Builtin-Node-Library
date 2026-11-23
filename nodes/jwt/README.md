# JWT

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/jwt/`
- **Researched:** 2026-08-22
- **Official API:** RFC 7519 JWT, RFC 7515 JWS, RFC 7518 JWA (updated by RFC 9864), RFC 8725 BCP
- **Docs:** https://www.rfc-editor.org/rfc/rfc7519 · https://www.rfc-editor.org/rfc/rfc8725 · https://www.iana.org/assignments/jose
- **Auth:** secret / privateKey / publicKey — secret binding: `secret / private_key`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_JWT`)

## Scope

Compact JWS sign, verify, and decode. Verify is required for trust decisions. decode skips signature checks and must not authorize.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `sign` | JWS | RFC 7515 | Mint |
| `verify` | JWS | RFC 7515 | Trust |
| `decode` | JWS | RFC 7519 | Unsafe read |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | sign | no | Verb |
| `alg` | select | no | HS256 | no | JWA |
| `secret` | password | no | | yes | HMAC |
| `private_key` | password | no | | yes | Sign |
| `public_key` | password | no | | yes | Verify |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- Official JWS. Keys are credentials — secret manager only, never logged.
- `none` is not offered (RFC 8725). Offered `alg` values are a subset of RFC 7518.
- Do not put PATs or passwords in JWT claims unless the workflow is designed for that.

## Implementation notes

- Handler: {@code JwtNode} (`CodecHandlers`). JDK Signature / MAC. Clock skew configurable.
- Agent / INPUT inference: {@code claims} only → sign; {@code token} only → verify; explicit {@code operation} wins.
- HS* sign/verify require {@code secret} in config (secret manager). Empty tool calls without signing material return a usage error, not a misleading sign-secret error.
- {@code decode} skips verification (unsafe for authorization); needs only {@code token}.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
