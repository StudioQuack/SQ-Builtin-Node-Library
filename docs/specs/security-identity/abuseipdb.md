# AbuseIPDB

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/abuseipdb/`
- **Researched:** 2026-08-20
- **Official API:** AbuseIPDB APIv2
- **Docs:** https://docs.abuseipdb.com/
- **Auth:** api_key — secret binding: `api_key` (header `Key`)

## Scope

One node for AbuseIPDB **APIv2** IP reputation. The designer picks `operation`; the handler calls `https://api.abuseipdb.com/api/v2/{path}` over HTTPS.

`report` submits an abuse report **on the operator’s behalf**. The node must not invent evidence, must not retry a successful report, and must refuse `report` unless `acknowledge_accurate_report` is `true`. Do not automate speculative or “just in case” reports.

Do not wrap APIv1 (`abuseipdb.com/report/json`). Do not call undocumented endpoints.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `check` | GET | `/check` | Reputation for one IPv4/IPv6 |
| `report` | POST | `/report` | Submit a **verified** abuse report (categories required) |
| `blacklist` | GET | `/blacklist` | Download the community blacklist (JSON; plan-capped) |

Base origin: `https://api.abuseipdb.com/api/v2`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `check` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | APIv2 key. Header `Key` (preferred; query `key` is documented but logs the secret). Never on INPUT, never logged. |
| `max_age_in_days` | number | no | `30` | no | Default `maxAgeInDays` for `check` (1–365). Catalog: `maxAgeInDays`. |
| `acknowledge_accurate_report` | boolean | yes if `report` | `false` | no | Operator attests the IP was actually abusive. `false` → `{ error }` and **no HTTP**. Designer warning required. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |
| `blacklist_limit` | number | no | empty | no | Default `limit` for `blacklist` when input omits it. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = check`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ip_address` | string | yes | IPv4 or IPv6 (`ipAddress`). URL-encode (IPv6 colons). Alias `ip`. |
| `max_age_in_days` | number | no | 1–365. Else config `max_age_in_days` (API default 30). |
| `verbose` | boolean | no | When true, include up to 10_000 recent reports + `countryName`. Default omit (lighter payload). |

### `operation = report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ip` | string | yes | IPv4 or IPv6 being reported. |
| `categories` | string | yes | Comma-separated category **IDs** (max 30). IDs from https://www.abuseipdb.com/categories (e.g. `18,22` SSH brute-force). Empty / non-numeric → `{ error }` no HTTP. |
| `comment` | string | no | Evidence (logs, ports). Minimize PII. Do not send government IDs or unrelated personal data. |
| `timestamp` | string | no | ISO 8601 datetime of the abuse. Omit → server “now”. |

### `operation = blacklist`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `confidence_minimum` | number | no | 25–100. Default **100**. Values below 100 are a **subscriber** feature. |
| `limit` | number | no | Max entries. Free default 10_000; paid tiers higher (docs). Clamp to a handler cap (see notes). |
| `only_countries` | string | no | Comma-separated ISO 3166-1 alpha-2 include list (subscriber). |
| `except_countries` | string | no | Comma-separated exclude list (subscriber). |
| `ip_version` | number | no | `4` or `6`. Omit → mixed. |

Do **not** set `plaintext` / `Accept: text/plain`. Always JSON (`Accept: application/json`).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object/array | 2xx | Provider `data` (check object, report `{ ipAddress, abuseConfidenceScore }`, or blacklist entries). Cap body. |
| `abuse_confidence_score` | number | `check` / `report` 2xx | Convenience copy of `data.abuseConfidenceScore` when present |
| `error` | string | failure | Actionable, no key. Prefer provider `errors[].detail`. |
| `http_status` | number | HTTP error | Provider status (422 validation, 429 quota, …) |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 60 / 30 |
| `rate_limit_remaining` | number | when header present | `X-RateLimit-Remaining` (daily, per endpoint) |

## Legal

- Docs: https://docs.abuseipdb.com/
- Terms: https://www.abuseipdb.com/legal
- Privacy: https://www.abuseipdb.com/privacy
- Categories: https://www.abuseipdb.com/categories
- **Prohibited:** reporting IPs that the operator knows, suspects, or should have known are **not** malicious. False or speculative reports can suspend reporting privileges. This node must not fire `report` from unverified lists, enrichment-only hits, or another vendor’s unconfirmed score.
- Daily quotas (published 2026-08-20; confirm live): Standard **check** 1_000, **report** 1_000, **blacklist** 5 per day; higher on paid tiers. Headers: `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Same IP cannot be re-reported within **15 minutes** (HTTP 429).
- **PII:** IP addresses **may be personal data** in some jurisdictions (GDPR/ePrivacy). AbuseIPDB’s privacy page treats “Reported Data” as IP-only; the operator still needs a lawful basis to query/report. Do not put names, emails, or full logs with credentials in `comment`.
- Keys must not be used from browsers (CORS is intentionally unset). HTTPS only.
- Official-API-first: no scrape of the website blacklist HTML.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Key: {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- `check` / `blacklist`: GET with query params (`-G` style). `report`: POST `application/x-www-form-urlencoded`.
- Empty `api_key` → `{ error }` no HTTP.
- `report` without `acknowledge_accurate_report=true` → `{ error: "report requires acknowledge_accurate_report. Do not submit unverified IPs." }` — no HTTP.
- Validate IP syntax before HTTP. Invalid → `{ error }` no HTTP.
- Blacklist response can be large: body cap **2_000_000** bytes; default `limit` clamp **10_000** unless input/config sets a lower value. Do not download plaintext dumps.
- **Do not auto-retry `report`.** A retry can duplicate a true report or amplify a mistake. 429 on report → surface `{ error, http_status: 429, retry_after_seconds }` only.
- Other verbs: no auto-retry. 429 → honor `Retry-After`.
- Logs: operation, status, IP **length/version** not the address if avoidable (log truncated / hashed form at most), sizes — never `api_key`, never `comment` body.
- **UNVERIFIED APIs:** none of `check` / `report` / `blacklist` (live on https://docs.abuseipdb.com/ 2026-08-20). Category ID table fetch from https://www.abuseipdb.com/categories timed out — implementers must use that page, not a hardcoded stale list. AbuseIPDB privacy text vs “IP may be PII” (catalog): follow the stricter operator-jurisdiction view.
- **DO NOT BUILD:** `GET /reports` (paginated report dump), `GET /check-block`, `POST /bulk-report`, `DELETE/POST /clear-address`, APIv1 `abuseipdb.com/report/json`, plaintext blacklist. Not catalog rows — omit, do not list as “later”.
