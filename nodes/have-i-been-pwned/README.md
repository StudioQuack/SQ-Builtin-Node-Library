# Have I Been Pwned

Official [HIBP API v3](https://haveibeenpwned.com/API/v3) plus the [Pwned Passwords range API](https://haveibeenpwned.com/API/v3#PwnedPasswords). **Never send a full password.** Hash locally (UTF-8 → SHA-1 hex uppercase) and send only the first five characters as `prefix`. Paid breach/paste/domain APIs require a **32-character hexadecimal** `hibp-api-key` (Core / Pro / High RPM).

- **Docs:** https://haveibeenpwned.com/API/v3
- **Base:** `https:// (host is in each operation path)`
- **Auth:** header `hibp-api-key`, configKey `hibp_api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `breached-account` | GET | `haveibeenpwned.com/api/v3/breachedAccount/{account}` |
| `breached-domain` | GET | `haveibeenpwned.com/api/v3/breachedDomain/{domain}` |
| `paste-account` | GET | `haveibeenpwned.com/api/v3/pasteAccount/{account}` |
| `pwned-password` | GET | `api.pwnedpasswords.com/range/{prefix}` |
| `subscription-status` | GET | `haveibeenpwned.com/api/v3/subscription/status` |
| `breached-account-range` | GET | `haveibeenpwned.com/api/v3/breachedaccount/range/{prefix}` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `hibp_api_key` | password | yes | hibp-api-key header (32-char hex) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Host difference

HIBP v3 lives at `https://haveibeenpwned.com/api/v3`. Pwned Passwords lives at `https://api.pwnedpasswords.com`. This package uses `baseUrl` `https://` and puts the host in each path so OfficialApiNode can reach both.

## Legal

- Terms: https://haveibeenpwned.com/TermsOfUse
- Account/paste/domain/subscription-status search requires a paid 32-character hexadecimal subscription key. Identify a real user-agent; do not build a substitute breach database.
- Emails are personal data. Minimize; have a lawful basis.
- `breached-account-range` is the k-anonymity email search (`prefix` = first 6 SHA-1 hex chars). Stealer-log APIs (`/stealerLogsByEmail`, `/stealerLogsByWebsiteDomain`, `/stealerLogsByEmailDomain`) are Pro+ and require a verified domain — not in this node.

## Engine gaps

- HIBP requires a descriptive `User-Agent`. OfficialApiNode does not set custom headers beyond auth. The JVM default UA may receive HTTP 403.
- `pwned-password` still sends `hibp-api-key` (global auth). Do not put the password on INPUT — only `prefix`.
