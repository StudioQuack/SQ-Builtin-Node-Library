# reCAPTCHA

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/recaptcha/`
- **Researched:** 2026-08-20
- **Official API:** Google reCAPTCHA **siteverify** (Classic v2 / v3)
- **Docs:** https://developers.google.com/recaptcha/docs/verify
- **Auth:** secret — secret binding: `secret` (POST body `secret`)

## Scope

Server-side verification of a reCAPTCHA **response token** produced in the browser. This node calls **only** `POST https://www.google.com/recaptcha/api/siteverify` (or the `www.recaptcha.net` twin). The **secret key stays on the daemon**; it must never be placed in frontend graphs, INPUT edges, or logs. The site key is public and is **not** sent to siteverify.

**Catalog path (no change):** siteverify remains the documented verify API as of 2026-08-20 (also still valid after Classic→Google Cloud key migration: https://cloud.google.com/recaptcha/docs/migrate-recaptcha — “you can continue to use the `siteverify` method”).

**Not this node:** reCAPTCHA Enterprise / Google Cloud Fraud Defense `projects.assessments.create` (`https://recaptchaenterprise.googleapis.com/v1/projects/{PROJECT_ID}/assessments`). That is a different auth model (API key / service account) and a different catalog row if product later wants it.

This node does not render the widget (browser HTML). It only verifies a token already collected by the operator’s site or an upstream HTTP webhook.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `verify` | POST | `https://www.google.com/recaptcha/api/siteverify` | Validate `response` token; apply v3 `min_score` when configured |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `verify` | no | Only `verify` |
| `secret` | password | yes | empty | yes | reCAPTCHA **secret**. POST field `secret`. Never on INPUT, never in FE. |
| `version` | select | yes | `v2` | no | `v2` \| `v3`. Controls whether `score` / `min_score` / `action` checks apply. |
| `min_score` | number | no | `0.5` | no | v3 only. Google’s documented starting threshold. Inclusive: `score >= min_score` → `passed`. |
| `siteverify_host` | select | no | `www.google.com` | no | `www.google.com` \| `www.recaptcha.net` (FAQ: use recaptcha.net when google.com is blocked). |
| `timeout_seconds` | number | no | `15` | no | Connect + request timeout. Floor 1 s. Tokens expire in **2 minutes**. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Token + optional remote IP / expected action |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never include `secret` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = verify`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `response` | string | yes | Token from `g-recaptcha-response` / `grecaptcha.execute`. Trim. Empty → `{ error }` no HTTP. |
| `remote_ip` | string | no | Visitor IP (`remoteip`). Optional; do not invent. |
| `expected_action` | string | no | v3: must match JSON `action` (alphanumeric, `/`, `_` only per FAQ). Mismatch → `passed: false`. |
| `expected_hostname` | string | no | If set, must equal JSON `hostname`. Mismatch → `passed: false`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | HTTP 200 JSON parsed | `ok` even when `success` is false (bot/invalid token is a business result) |
| `success` | boolean | 200 | Provider `success` (valid token for this secret) |
| `passed` | boolean | 200 | `success` and (v2: true) and (v3: `score >= min_score`) and action/hostname checks |
| `score` | number | v3 when present | 0.0–1.0 (1.0 likely human) |
| `action` | string | v3 when present | Action name from the token |
| `challenge_ts` | string | when present | Challenge timestamp |
| `hostname` | string | when present | Hostname where the widget ran |
| `apk_package_name` | string | Android tokens | Package name when present |
| `error_codes` | array | when present | Provider `error-codes` |
| `error` | string | transport / 5xx / parse | Network or non-JSON failure — not used for `success: false` |
| `http_status` | number | HTTP error / transport | Provider or 0 on timeout |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 30 default |

Provider error codes (verify docs): `missing-input-secret`, `invalid-input-secret`, `missing-input-response`, `invalid-input-response`, `bad-request`, `timeout-or-duplicate`.

## Legal

- Verify API: https://developers.google.com/recaptcha/docs/verify
- v3: https://developers.google.com/recaptcha/docs/v3
- FAQ (quotas, recaptcha.net, test keys): https://developers.google.com/recaptcha/docs/faq
- Google APIs ToS: https://developers.google.com/terms
- Tokens: **single-use**, valid **2 minutes**. Replay → `timeout-or-duplicate`.
- Quota (FAQ, 2026-08-20): above **1_000 QPS** or **1_000_000 calls/month** requires Enterprise or an exception. v3 over monthly quota may **fail-open** with static score **0.9** and error `"Over free quota."` — treat `error-codes` containing over-quota as **not passed** even if `score` looks high.
- Secret is server-side only. Test keys in the FAQ are for **non-production** v2 (`6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI` / `6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe`).
- **UNVERIFIED:** whether Google still issues **new** Classic keys vs console-only Enterprise; siteverify itself remains documented. No catalog change.
- Official-API-first: no unofficial captcha-solving services.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- POST `application/x-www-form-urlencoded`: `secret`, `response`, optional `remoteip`. Do not put the secret in the query string.
- Empty `secret` → `{ error: "Missing reCAPTCHA secret. Bind secret in node parameters." }` — no HTTP.
- HTTP 200 with `success: false` → OUTPUT `{ status: "ok", success: false, passed: false, error_codes }` (not the error port).
- v3: if `score` missing, `passed: false`. Compare `min_score` with default 0.5.
- Body cap **64_000** bytes. Timeout default 15 s (token TTL is 2 min; do not queue long).
- Logs: operation, `success`, `passed`, score **bucket** (e.g. one decimal) optional — never `secret`, never full token (log length only).
- No auto-retry of a token (single-use). Transport 5xx may retry once only if the operator did **not** already consume the token — prefer no retry.
- **UNVERIFIED:** whether the Classic admin console still **creates** new v2/v3 keys (Google pushes Cloud migration). **Verified:** siteverify remains documented at https://developers.google.com/recaptcha/docs/verify and remains valid after key migration. **Catalog change: none.**
- **DO NOT BUILD:** `projects.assessments.create` / reCAPTCHA Enterprise REST in this node; widget HTML; SafetyNet Android-only extras beyond passing through `apk_package_name` if Google returns it.
