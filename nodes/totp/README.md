# TOTP

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/totp/`
- **Researched:** 2026-08-22
- **Official API:** RFC 6238 TOTP (RFC 4226 HOTP base)
- **Docs:** https://www.rfc-editor.org/rfc/rfc6238
- **Auth:** shared secret — secret binding: `secret`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_TOTP`)

## Scope

Computes a time-based OTP from a shared secret. Codes and the secret never appear in logs.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `generate` | in-process | RFC 6238 | One code |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | generate | no | Verb |
| `secret` | password | yes | | yes | Base32 secret |
| `algorithm` | select | no | sha1 | no | HMAC |
| `digits` | number | no | 6 | no | Length |
| `period` | number | no | 30 | no | Step |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- The secret is a second-factor credential. Secret manager only.
- Never log or return the secret. Avoid logging the code.

## Implementation notes

- HMAC-SHA-1 default (RFC 6238 authenticator interop); SHA-256 preferred for new issuers. RFC 6238 also specifies HMAC-SHA-512 — not offered on this node.
- Output code only on the port; redact if echoed in errors.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
