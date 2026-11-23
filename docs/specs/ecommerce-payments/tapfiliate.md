# Tapfiliate

- **Status:** planned
- **Catalog:** `docs/catalog/ecommerce-payments.md`
- **Folder when built:** `nodes/tapfiliate/`
- **Researched:** 2026-08-20
- **Official API:** Tapfiliate REST **v1.6** (`https://api.tapfiliate.com/1.6/`)
- **Docs:** https://tapfiliate.com/docs/rest/ · REST integration: https://tapfiliate.com/docs/integrations/rest-api/ · help: https://support.tapfiliate.com/en/articles/12682358-how-to-integrate-with-tapfiliate-via-the-rest-api
- **Auth:** API key header `X-Api-Key` (docs also show `Api-Key` in older curl samples — send **`X-Api-Key`**) — secret binding: `api_key`

## Scope

One node for Tapfiliate affiliate programs: create affiliates, record clicks, record conversions, list programs. The customer owns the Tapfiliate account. **Affiliate PII minimization:** never log affiliate email, name, password, or IP; output ids only in logs.

Do not embed the API key in browser JS. HTTPS only.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `affiliate_create` | POST | `/affiliates/` | Create an affiliate (`firstname`, `lastname`, optional `email` / `password`) |
| `click_create` | POST | `/clicks/` | REST-only click; returns `click_id` for conversions (`referral_code` required) |
| `conversion_create` | POST | `/conversions/` | Record a conversion (click_id / referral_code / coupon / customer_id — precedence per docs) |
| `program_list` | GET | `/programs/` | List programs (`asset_id` optional query) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `affiliate_create` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Header `X-Api-Key`. Can approve commissions — treat as high-value |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Affiliate password/email are PII — never log |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key`. Truncate affiliate email on output if possible |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = affiliate_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `firstname` | string | yes | Affiliate first name — PII |
| `lastname` | string | yes | Affiliate last name — PII |
| `email` | string | no | Affiliate email — PII |
| `password` | password | no | Portal password; if omitted Tapfiliate generates one (may appear in **response** — do not log; do not persist on OUTPUT beyond the run) |
| `company` | object | no | `{ name, address }` |

### `operation = click_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `referral_code` | string | yes | Affiliate `ref=` code |
| `source_id` | string | no | Source id |
| `referrer` | string | no | HTTP referrer |
| `landing_page` | string | no | Landing URL |
| `user_agent` | string | no | UA — do not log |
| `ip` | string | no | Visitor IP — PII; send only if the merchant’s privacy policy allows |
| `meta_data` | object | no | Extra reporting keys |

### `operation = conversion_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `amount` | number | no | Conversion value (commission base) |
| `external_id` | string | no | Unique merchant order/txn id |
| `click_id` | string | conditional | One of click_id / referral_code / coupon / customer_id / tracking_id / asset+source |
| `referral_code` | string | conditional | Affiliate code |
| `coupon` | string | conditional | Coupon code |
| `customer_id` | string | conditional | Merchant customer id |
| `program_id` | string | no | Program slug |
| `currency` | string | no | ISO-4217 |
| `meta_data` | object | no | Extra |
| `override_max_cookie_time` | boolean | no | Query flag |

Precedence if several ids sent: customer_id > coupon > referral_code > click_id > asset+source > tracking_id.

### `operation = program_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `asset_id` | string | no | Filter |
| `page` | number | no | Pagination (default 25; `Link` headers) |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `affiliate_id` | string | `affiliate_create` | New affiliate id |
| `click_id` | string | `click_create` | Click id for conversion_create |
| `conversion_id` | string | `conversion_create` | Conversion id |
| `programs` | array | `program_list` | `{ id, title, currency }` |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `X-Ratelimit-Reset` (unix seconds) |

## Legal

- Terms: https://tapfiliate.com/terms/
- Affiliate PII (email, name, IP) — GDPR/CCPA: merchant is controller. Minimize fields (skip `ip` / `password` unless required).
- Rate limits via `X-Ratelimit-Limit` / `Remaining` / `Reset`. Do not tight-loop on 429.
- Official-API-first: no scrape of tapfiliate.com dashboard.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Header: `X-Api-Key: {api_key}`, `Content-Type: application/json`, `Accept: application/json`.
- Empty key → `{ error }` — no HTTP.
- If create-affiliate response includes a generated password, pass it on OUTPUT **once** for the workflow to store in the merchant’s secret store — **never log it**.
- Body cap **1_000_000** bytes. Timeout default 25 s.
- Logs: operation, ids (affiliate/click/conversion/program), HTTP status — never API key, emails, passwords, IPs, or user-agent strings.
