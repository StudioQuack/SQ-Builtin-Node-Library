# Cloudflare Turnstile

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/cloudflare-turnstile/`
- **Researched:** 2026-08-20
- **Official API:** Cloudflare Turnstile Siteverify API v0
- **Docs:** https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- **Auth:** none — secret binding: `secret` (Siteverify body field `secret`)

## Scope

Server-side validation of a **Turnstile** widget token. One operation: `verify`. Calls `POST https://challenges.cloudflare.com/turnstile/v0/siteverify`. The widget secret stays on the daemon (never frontend, never INPUT, never logs). The sitekey is public and is not sent to Siteverify.

**This is not the Cloudflare DNS/zone node.** No `/client/v4/zones`, no DNS records, no WAF custom rules. Turnstile can run on sites that are not proxied through Cloudflare.

Client-side widget HTML is out of scope; this node only validates a token the operator already collected (form field `cf-turnstile-response` or equivalent).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `verify` | POST | `https://challenges.cloudflare.com/turnstile/v0/siteverify` | Confirm token authenticity, expiry, and single-use |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `verify` | no | Only `verify` |
| `secret` | password | yes | empty | yes | Widget secret from the Cloudflare dashboard. Sent as Siteverify body field `secret`. |
| `idempotency_key` | string | no | empty | no | Default UUID string for retries (catalog: `idempotencyKey`). Input overrides. Cloudflare: safe retry of the **same** token. |
| `timeout_seconds` | number | no | `15` | no | Connect + request timeout. Floor 1 s. Tokens expire in **300 s**. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Token + optional IP / idempotency / expected action+host |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never include `secret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = verify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `response` | string | yes | Token from the widget (max **2048** chars). Trim. Empty / too long → `{ error }` no HTTP. |
| `remote_ip` | string | no | Visitor IP (`remoteip`) |
| `idempotency_key` | string | no | UUID for this validation. Overrides config. Use the same value if retrying transport failure. |
| `expected_action` | string | no | Must match JSON `action` when set |
| `expected_hostname` | string | no | Must match JSON `hostname` when set |
| `expected_cdata` | string | no | Must match JSON `cdata` when set |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | JSON parsed | `ok` even when `success` is false |
| `success` | boolean | 200 | Provider `success` |
| `passed` | boolean | 200 | `success` and optional action/hostname/cdata checks |
| `challenge_ts` | string | when present | ISO 8601 challenge time |
| `hostname` | string | when present | Hostname that served the widget |
| `action` | string | when present | Client `action` |
| `cdata` | string | when present | Client `cData` |
| `error_codes` | array | when present | Provider `error-codes` |
| `ephemeral_id` | string | Enterprise | `metadata.ephemeral_id` when present — still not a secret key |
| `error` | string | transport / parse | Network failure only |
| `http_status` | number | HTTP error / transport | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 30 default |

Siteverify error codes (docs 2026-08-20): `missing-input-secret`, `invalid-input-secret`, `missing-input-response`, `invalid-input-response`, `bad-request`, `timeout-or-duplicate`, `internal-error`.

## Legal

- Siteverify: https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- Get started: https://developers.cloudflare.com/turnstile/get-started/
- Customer terms: https://www.cloudflare.com/en-gb/terms/ (Self-Serve Subscription Agreement) and applicable Service-Specific Terms. Website-only ToS (https://www.cloudflare.com/website-terms/) does **not** replace the subscription agreement for dashboard products.
- Server-side validation is **mandatory**. Skipping Siteverify leaves the form unprotected (forged/expired/replayed tokens).
- Tokens: max 2048 chars; valid **300 seconds**; **single-use**. Replay/expiry → `timeout-or-duplicate`.
- Secret keys are private. Dummy/test sitekeys/secrets from Cloudflare testing docs are for non-production; production secrets reject dummy tokens.
- Visitor IP, TLS fingerprint, and User-Agent may be processed by Cloudflare for bot detection. Operators should disclose Turnstile in their privacy notice (especially invisible widgets).
- **UNVERIFIED:** no numeric QPS published on the Siteverify page fetched 2026-08-20. Do not tight-loop; honor errors.
- Official-API-first: HTTPS Siteverify only. No scrape of dashboards.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- POST JSON (`Content-Type: application/json`) **or** `application/x-www-form-urlencoded` — both documented; prefer JSON: `{ "secret", "response", "remoteip"?, "idempotency_key"? }`. Always parse JSON responses.
- Empty `secret` → `{ error: "Missing Turnstile secret. Bind secret in node parameters." }` — no HTTP.
- HTTP 200 with `success: false` → `{ status: "ok", success: false, passed: false, error_codes }` (not the error port).
- Transport retry: **only** with the same `idempotency_key` (generate UUID v4 if config/input empty **and** retrying). Do not retry after a JSON body with `success` true or `timeout-or-duplicate`.
- `internal-error`: one retry with backoff allowed (docs). Still use idempotency key.
- Body cap **64_000** bytes. Timeout default 15 s.
- Logs: operation, `success`, `passed`, error code names, hostname — never `secret`, never full token (length only).
- **UNVERIFIED APIs:** none — Siteverify `POST /turnstile/v0/siteverify` is the official server-side path (fetched 2026-08-20). Rate-limit numbers not published on that page.
- **DO NOT BUILD:** Cloudflare DNS/zone (`/client/v4/zones`), WAF, bot-fight custom rules, widget HTML. Catalog ops unchanged (`verify` only).
