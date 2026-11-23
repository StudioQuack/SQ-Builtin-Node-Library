# Crypto

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/crypto/`
- **Researched:** 2026-08-22
- **Official API:** NIST SP 800-38D AES-GCM, RFC 8439 ChaCha20-Poly1305, RFC 9562 UUID, NIST hash policy (SHA-1 deprecated)
- **Docs:** https://csrc.nist.gov/pubs/sp/800/38/d/final · https://csrc.nist.gov/Projects/Hash-Functions/NIST-Policy-on-Hash-Functions
- **Auth:** none (key/passphrase secrets) — secret binding: `key / passphrase`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_CRYPTO`)

## Scope

Local crypto primitives. Prefer AES-256-GCM. MD5/SHA-1 are **legacy** checksum-compat only, not security (NIST hash policy; SHA-1 disallowed for new signatures). Keys from the secret manager.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `hash` | JDK | MessageDigest | Digest |
| `hmac` | JDK | Mac | HMAC |
| `sign` | JDK | Signature | Sign |
| `encrypt`/`decrypt` | JDK | AES-GCM/ChaCha | Seal |
| `random` | JDK | SecureRandom | Bytes |
| `uuid` | RFC 9562 | v4 | Id |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | hash | no | Verb |
| `algorithm` | string | no | sha256 | no | Digest/sig |
| `key` | password | no | | yes | HMAC/cipher key |
| `cipher` | select | no | aes-256-gcm | no | AEAD |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. Keys are credentials — never log them or write them to OUTPUT.
- Do not use MD5/SHA-1 for integrity of untrusted data. They are legacy checksum-compat only.

## Implementation notes

- SecureRandom for random/uuid.
- AES-GCM and ChaCha20-Poly1305 only for encrypt/decrypt.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
