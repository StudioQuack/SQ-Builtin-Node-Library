# Mailcheck

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/mailcheck/`
- **Researched:** 2026-08-20
- **Official API:** UserCheck email lookup (MailCheck.ai rebrand) `GET /email/{email}`
- **Docs:** https://www.usercheck.com/docs/api/email-endpoint · Rebrand: https://www.usercheck.com/rebrand · Quickstart: https://www.usercheck.com/docs/get-started/quickstart
- **Auth:** api_key — secret binding: `api_key` (header `Authorization: Bearer`)

## Scope

One node that checks whether an address is **disposable** and whether the domain has **MX**. Catalog scope is MX/disposable only — do not build Gates, webhooks, blocklists, or domain-only lookup as extra operations. MailCheck.ai rebranded to **UserCheck**; official host is `https://api.usercheck.com`. `https://api.mailcheck.ai` is forwarded **for a limited time** — new work must call `api.usercheck.com`. This is not inbox delivery verification and not SMTP probe.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `email.check` | GET | `/email/{email}` | MX + disposable (and related signals). Query `include_mx=true` (default). |

Base origin: `https://api.usercheck.com`. Path-encode the email.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `email.check` | no | Only `email.check` |
| `api_key` | password | yes | empty | yes | UserCheck API key. Bearer. Never on INPUT. |
| `include_mx` | boolean | no | `true` | no | Default MX lookup. `false` omits `mx` / `mx_records`. |
| `timeout_seconds` | number | no | `20` | no | Floor 1 s. |

Unauthenticated free tier is 60 req/h; signed-up free plan is 1,000 credits/month and 120 req/h (rebrand page). Always send the key.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `email` | No secrets |
| `output` | OUTPUT | object — `{ status, mx, disposable, … }` or `{ error }` | Do not log the email |

FUNCTION ports: none.

## What this node does not call

- `GET /domain/{domain}` — domain-only. Catalog is email MX/disposable.
- Gates (`/gates`) — dashboard allow/block engine.
- Blocklist admin, webhooks, credits admin.

Those exist on UserCheck; they are out of scope.

## Input fields (by operation)

### `operation = email.check`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Address to check. Trim; must contain `@`. Empty → `{ error }` no HTTP. |
| `include_mx` | boolean | no | Overrides config. Default true. |
| `wait` | string | no | Pro only: comma-separated `domain_age_in_days`, `domain_authority`. Omit on free plans. |

Path-encode the address (`@` → `%40`). Do not put the email in query string except `include_mx` / `wait`.

### HTTP mapping

`GET https://api.usercheck.com/email/{urlencoded-email}?include_mx=true`

400 body: `{ "status": 400, "error": "The email address is invalid." }`

429 body: `{ "status": 429, "error": "Too many requests" }`

MX `true` does **not** mean the mailbox exists; it means the domain publishes MX. Disposable providers often have valid MX (UserCheck quickstart: Mailinator).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `mx` | boolean | when `include_mx` not false | Domain has MX (`null` if DNS incomplete) |
| `disposable` | boolean | 2xx | Disposable domain **or** disposable address on a legit domain |
| `domain` | string | 2xx | Normalized domain (punycode) |
| `normalized_email` | string | 2xx | Identity key (tags stripped). **Send mail to submitted `email`, not this.** |
| `mx_records` | array | when MX included | `{ hostname, priority }` |
| `public_domain` | boolean | 2xx | Gmail/Outlook-class |
| `relay_domain` | boolean | 2xx | Forwarding/relay |
| `role_account` | boolean | 2xx | `admin@` / `support@` style |
| `spam` | boolean | 2xx | Spam-associated domain |
| `did_you_mean` | string | when present | Typo suggestion |
| `signals` | object | 2xx | Full provider JSON (capped). Do not log. |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | 400 invalid email, 429 rate limit |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` if present; else 60 on 429 |

Do not expose Pro-only fields (`domain_authority`, `blocklisted`, `disposable_provider`) as required outputs; pass through inside `signals` when present.

## Legal

- Rebrand / migrate: https://www.usercheck.com/rebrand
- Email endpoint: https://www.usercheck.com/docs/api/email-endpoint
- Caller supplies the address (typically from a form they operate). Checking an email is PII minimization: store `disposable`/`mx` booleans, not extra profile enrichment, unless the workflow needs them.
- Customer owns the UserCheck account. Never log the API key or the raw email.
- Official-API-first: no SMTP open-relay probes, no scrape of mailbox providers.

Rate limits (rebrand page, 2026-08-20): unauthenticated 60/h; free keyed 120/h and 1,000/month; Pro 10,000/h and 500,000/month. Confirm live in the dashboard.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- `GET https://api.usercheck.com/email/{urlencoded-email}?include_mx=true|false`
- Header `Authorization: Bearer {api_key}`. Empty key → `{ error }` no HTTP.
- Response cap **100_000** bytes (payload is small). Timeout default 20 s.
- 400 `{ "status": 400, "error": "The email address is invalid." }` → `{ error }` no retry.
- 429 `{ "status": 429, "error": "Too many requests" }` → `{ error, http_status: 429, retry_after_seconds }`. Do not auto-retry inside the handler.
- Handler logs `disposable` and `mx` booleans only — never the address string, the Bearer token, or `mx_records` hostnames.

Credits: each email lookup consumes plan credits (see https://www.usercheck.com/docs/get-started/credits). Free keyed plan: 1,000/month. Do not batch-check harvested lists without a lawful basis.

`alias` in the JSON is **deprecated** by UserCheck; compare `email` vs `normalized_email` instead.

**UNVERIFIED:** remaining lifetime of `api.mailcheck.ai` forwarding. Do not depend on it. Live credit prices may change — read the credits page at implement time.
