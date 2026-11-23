# Have I Been Pwned

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/have-i-been-pwned/`
- **Researched:** 2026-08-20
- **Official API:** Have I Been Pwned API v3 (+ Pwned Passwords range API)
- **Docs:** https://haveibeenpwned.com/API/v3
- **Auth:** `hibp_api_key` (header `hibp-api-key`) required except `pwned-password`; `user_agent` required on **all** calls

## Scope

One node for HIBP **v3** account/domain/paste lookups and **Pwned Passwords** k-anonymity checks. Emails sent to `breached-account` / `paste-account` are **personal data** disclosed to HIBP.

`pwned-password` **must** hash locally and call `GET https://api.pwnedpasswords.com/range/{first-5-hex}`. Never send the password, never send the full SHA-1/NTLM hash, never log the password or full hash.

Do not wrap stealer-log APIs, bulk hash corpus download, or a competing “breach search engine”. Domain search requires a **verified** domain on the operator’s HIBP dashboard.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `breached-account` | GET | `/breachedAccount/{account}` | Breaches for one email (URL-encoded). Paid key. |
| `breached-domain` | GET | `/breachedDomain/{domain}` | Aliases + breach names for a **verified** domain. Paid key. |
| `paste-account` | GET | `/pasteAccount/{account}` | Pastes for one email. Paid key. |
| `pwned-password` | GET | `https://api.pwnedpasswords.com/range/{prefix}` | Password appearance count via k-anonymity. **No API key.** |

HIBP origin: `https://haveibeenpwned.com/api/v3`. Paths follow the documented mixed case (`breachedAccount`, `breachedDomain`, `pasteAccount`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `breached-account` | no | Chooses the row above |
| `hibp_api_key` | password | yes except `pwned-password` | empty | yes | Header `hibp-api-key`. Catalog: `hibpApiKey`. Never on INPUT, never logged. |
| `user_agent` | string | yes | `StudioQuack` | no | Header `user-agent`. HIBP **requires** a descriptive UA; missing → HTTP 403. Catalog: `userAgent`. |
| `truncate_response` | boolean | no | `true` | no | Default for `breached-account` (`truncateResponse`). `true` → breach **names** only. |
| `include_unverified` | boolean | no | `true` | no | Default `includeUnverified` for `breached-account`. |
| `add_padding` | boolean | no | `true` | no | `pwned-password`: send `Add-Padding: true` so response size does not leak. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `password` is secret; never persisted to logs |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo password, full hash, or API key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = breached-account`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account` | string | yes | Email (or username/phone as HIBP accepts). Trim; URL-encode path. Empty → `{ error }` no HTTP. |
| `truncate_response` | boolean | no | Override config. Query `truncateResponse`. |
| `include_unverified` | boolean | no | Override config. Query `includeUnverified`. |
| `domain` | string | no | Query `domain` — filter to one breached site domain. |

### `operation = breached-domain`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | yes | Verified domain to search. Unverified → HTTP 403. |

### `operation = paste-account`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account` | string | yes | Email. Trim; URL-encode. |

### `operation = pwned-password`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `password` | password | yes | Plain password **only on INPUT**. Handler: UTF-8 → SHA-1 hex uppercase → send **first 5 characters** as `{prefix}`. Never put this on config. Never log. |
| `mode` | string | no | Omit for SHA-1 suffixes. `ntlm` → query `mode=ntlm` (still 5-char prefix of the **NTLM** hash, computed locally). |

Do **not** accept a caller-supplied full hash to “skip hashing” unless it is used only locally to derive the 5-char prefix and the full value is discarded; never put a full hash on the wire.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` — including HIBP **404** “not found” for account/paste (not an error) |
| `pwned` | boolean | account/paste/password | `true` if HIBP 200 with hits; `false` if 404 (account/paste) or password suffix not in range list |
| `breaches` | array | `breached-account` 200 | Names or full breach models per `truncateResponse` |
| `domain_aliases` | object | `breached-domain` 200 | Map alias → breach name[] as returned |
| `pastes` | array | `paste-account` 200 | Paste models (`Source`, `Id`, `Title`, `Date`, `EmailCount`) |
| `count` | number | `pwned-password` | Appearance count for the matched suffix; `0` if absent. Padded rows with count `0` are ignored. |
| `error` | string | failure | Actionable; never include password/hash/key |
| `http_status` | number | HTTP error | Provider status (401/403/429/503). Do **not** copy 404 here when mapped to `pwned: false`. |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `retry-after` / `Retry-After` |

## Legal

- API: https://haveibeenpwned.com/API/v3
- Terms: https://haveibeenpwned.com/TermsOfUse (updated March 2026)
- Acceptable use (API page): identify a real user-agent; do not impersonate other UAs; do not build a substitute breach database; do not query emails/domains to **disadvantage** victims or solicit them.
- Account/paste/domain search: **paid** subscription key. Test key `00000000000000000000000000000000` only against `@hibp-integration-tests.com`.
- Rate limits: plan-specific on breaches/pastes; HTTP 429 + `retry-after`. Domain search has no published RPM but “query infrequently”; abuse may be throttled. Pwned Passwords: **no published rate limit** (2026-08-20).
- Emails are personal data. Minimize; have a lawful basis; do not log addresses in full if a hash/length will do.
- k-anonymity **email** range API (`GET /breachedaccount/range/{6-char}`) is **not** a catalog operation. If added later: discard non-matching suffixes immediately (ToS § Paid Services).
- Pwned Passwords: no attribution required. Still never send the secret off-host except the 5-char prefix.
- TLS 1.2+ only (HIBP). Official-API-first: no scrape of haveibeenpwned.com HTML.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers (HIBP v3): `hibp-api-key` (when required), `user-agent: {user_agent}`, `Accept: application/json`.
- Headers (`pwned-password`): `user-agent` required; `Add-Padding: true` when config says so; **no** API key.
- Empty UA → `{ error }` no HTTP. Empty key on paid ops → `{ error }` no HTTP.
- `pwned-password`: if `password` empty → `{ error }` no HTTP. Compute digest in-memory; wipe local copies after prefix extract. Match suffix **case-insensitive**. Do not log prefix either if avoidable (log “range request” + status only).
- Map HIBP **404** on `breached-account` / `paste-account` / `breached-domain` to `{ status: "ok", pwned: false, breaches|pastes|domain_aliases: [] }` — not `{ error }`.
- Body cap **1_000_000** bytes. No auto-retry. 429 → surface `retry_after_seconds`.
- Logs: operation, HTTP status, list lengths — never password, never full hash, never `hibp_api_key`, never raw email if redaction can keep domain-only.
- **UNVERIFIED APIs:** none of the four catalog ops (HIBP v3 + Pwned Passwords range live 2026-08-20). Catalog `pwned-password` **must** stay on the 5-char range API (not a full-hash or plaintext POST).
- **DO NOT BUILD:** stealer-log APIs; `GET /breachedaccount/range/{6}` as a separate operation; domain-verification generate/verify; subscription status; all-breaches / latest-breach / data-classes; Pwned Passwords corpus download; any path that sends the full password or full hash.
