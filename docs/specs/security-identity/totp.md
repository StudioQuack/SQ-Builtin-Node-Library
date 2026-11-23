# TOTP

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/totp/`
- **Researched:** 2026-08-20
- **Official API:** TOTP (RFC 6238) as HOTP (RFC 4226) with a time-based counter
- **Docs:** [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238.html) (TOTP), [RFC 4226](https://www.rfc-editor.org/rfc/rfc4226.html) (HOTP). Key encoding: [RFC 4648](https://www.rfc-editor.org/rfc/rfc4648.html) Base32 for the configured secret.
- **Auth:** none (local). Shared TOTP key — secret binding: `secret`

## Scope

Local RFC 6238 Time-Based One-Time Password utility. Catalog operations: **`generate`** and **`verify`**. No network. No vendor. No QR / `otpauth` enrollment operation (catalog does not list it; enrollment QR is sensitive and must never be logged even if an operator builds a URI elsewhere).

TOTP = HOTP(K, T) with T = floor((UnixTime − T0) / X) (RFC 6238 §4.2). Default X = **30** seconds, T0 = **0** (Unix epoch). HMAC-SHA-1 is the RFC default; SHA-256 and SHA-512 are allowed (RFC 6238 §1.2 / §5.1). Digit length default **6** (RFC 4226 §5.3: implementations MUST extract at least 6 digits).

**Do not build** a counter-based HOTP operation (moving-factor C as an independent catalog verb). RFC 4226 is cited as the truncation/HMAC building block only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `generate` | *(local)* | RFC 6238 §4 TOTP = HOTP(K, T) | Produce the current OTP for K and now (or a supplied Unix time) |
| `verify` | *(local)* | RFC 6238 §5.2 delay window | Accept/reject a presented OTP against K |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `verify` | no | Chooses the row above |
| `secret` | password | yes | empty | yes | Shared key K. **RFC 4648 Base32** (catalog). Decode to raw bytes for HMAC. Empty → `{ error }`. Never on INPUT. Never logged. |
| `algorithm` | select | no | `SHA1` | no | HMAC hash: `SHA1` (RFC default) \| `SHA256` \| `SHA512` (RFC 6238). |
| `digits` | number | no | `6` | no | Decimal digits. Allow **6**, **7**, **8** only (RFC 4226 §5.3). |
| `period` | number | no | `30` | no | Time-step X in seconds (RFC 6238 §4.1). Default 30 (RFC RECOMMEND). Floor 1, cap 120. |
| `t0` | number | no | `0` | no | T0 Unix time to start counting (RFC 6238 §4.1). Default 0. |
| `window` | number | no | `1` | no | Extra steps checked on **verify** only. RFC 6238 §5.2 RECOMMENDS at most one step of delay. Floor 0, cap **2** (this node will not open a larger attack window). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No shared secret |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | OTP codes are short-lived credentials — do not log. `generate` returns `code` on OUTPUT for the workflow; redaction MUST include it |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = generate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `unixTime` | number | no | Override “now” for tests. Default: current Unix time (UTC). MUST support values past 2038 (RFC 6238 §4.2 64-bit T). |

If `secret` (or `k` / `base32`) appears on INPUT → `{ error }` and no HMAC. Config secret binding only. Do not accept a password list. One code per run.

### `operation = verify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `code` | string | yes | Presented OTP. Digits only; length must equal config `digits`. Never log. |
| `unixTime` | number | no | Override verifier “now”. Default: current Unix time. |

Verifier computes TOTP for T, T±1, … up to `window` (RFC 6238 §5.2). Constant-time compare of the decimal strings (avoid early-exit oracles).

**Replay:** RFC 6238 notes an OTP can be consumed by a third party inside the time-step. This node is **stateless** and does not persist last-used T. Cross-run replay prevention is the operator’s store, not this handler. Do not silently invent a daemon-wide used-OTP cache (multi-replica / multi-run races). Document that in the designer README when built.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `code` | string | `generate` | Decimal OTP, zero-padded to `digits`. Sensitive. |
| `period` | number | `generate` | X (seconds) |
| `remainingSeconds` | number | `generate` | Seconds left in the current step (usability; not a security boundary) |
| `valid` | boolean | `verify` | `true` if a window match |
| `delta` | number | `verify` and `valid=true` | Step offset that matched (0, ±1, …). Useful for clock-drift diagnosis. Do not return the expected code on failure. |
| `error` | string | failure | No secret, no OTP values |

Wrong code → `{ status: "ok", valid: false }` with no `code` and no expected OTP (no oracle). Empty or non-digit `code`, or length ≠ `digits` → `{ error }` (no HMAC).

## Legal

- IETF RFC 6238 (Informational, May 2011), RFC 4226 (Informational, December 2005), RFC 4648 (Base32).
- Shared TOTP secrets are **credentials** equivalent to a password. OS secret manager only. Enrollment QR / `otpauth` URIs (if the operator constructs them outside this node) contain K in plaintext — **never log**.
- OTP codes are short-lived credentials. Do not write them to engine variables; REF-only context. Do not print them in run logs.
- This node does not phone home. No third-party ToS.
- Do not use `generate` in a loop to precompute future codes for offline cracking of a secret you do not own.
- Official-API-first: RFCs are the official surface.

## Implementation notes

- **Local only.** JDK `Mac.getInstance("HmacSHA1"|"HmacSHA256"|"HmacSHA512")`. Dynamic truncation exactly as RFC 4226 §5.3 (offset from last byte, 31-bit P, modulo 10^Digit, zero-pad).
- Base32: RFC 4648 alphabet; ignore padding `=`; reject other alphabets. After decode, empty key → `{ error }`.
- RFC 6238 Appendix A is an example; do not paste it into production as a second implementation — implement from §4–§5.
- Logs: operation, algorithm, digits, period, `valid`, sizes — never `secret`, never `code`, never Base32 fragments.
- No network, no retry.
- Error mapping: bad config / bad Base32 / bad digit length → `{ error }` on OUTPUT, never throw past dispatch.
