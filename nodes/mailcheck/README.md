# Mailcheck

Official [UserCheck](https://api.usercheck.com/) REST (Mailcheck.ai rebrand). Handler: `OfficialApiNode` via `api_spec.json`. MX / disposable / domain signals only.

- **Docs:** https://api.usercheck.com/ · Rebrand: https://www.usercheck.com/rebrand
- **Base:** `https://api.usercheck.com`
- **Auth:** Bearer `api_key` (secret). Customer owns the UserCheck account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `email.check` | GET | `/email/{email}` |
| `domain.check` | GET | `/domain/{domain}` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`.

## Legal

- Customer is the controller of any address they submit. Minimize: send only the email/domain being checked.
- Provider terms: https://www.usercheck.com (current ToS on that site).
- Official API only. Do not scrape mailboxes or the dashboard.
