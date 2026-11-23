# Shodan

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/shodan/`
- **Researched:** 2026-08-20
- **Official API:** Shodan REST API
- **Docs:** https://developer.shodan.io/api
- **Auth:** api_key — secret binding: `api_key` (query `key`, as documented)

## Scope

One node for **lookup-only** Shodan REST methods: host banners already in Shodan’s index, search of that index, DNS resolve/reverse, and account profile.

**DO NOT BUILD** on-demand scan, Internet crawl, exploit, alert, stream, or “scan this CIDR” wrappers. Those official paths exist (`POST /shodan/scan`, `POST /shodan/scan/internet`, …) and are **out of scope** because they initiate network interrogation. This node must not become an unauthorized scanning tool. Operators looking up **their own** assets still use `host` / `search` against data Shodan already collected.

Do not scrape shodan.io HTML. Do not call the Streaming API.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `host` | GET | `/shodan/host/{ip}` | Services Shodan has already seen on one IP |
| `search` | GET | `/shodan/host/search` | Search the banner index (query credits may apply) |
| `dns-resolve` | GET | `/dns/resolve` | Hostname → IP map |
| `dns-reverse` | GET | `/dns/reverse` | IP → hostname list |
| `account-profile` | GET | `/account/profile` | Credits / membership for this key |

Base origin: `https://api.shodan.io`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `host` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Shodan API key (`key` query param). Never on INPUT. Redact from every log line including request URLs. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |
| `search_minify` | boolean | no | `true` | no | Default `minify` for `search` (truncate large banner fields). |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = host`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ip` | string | yes | IPv4/IPv6 to look up. Validate before HTTP. |
| `history` | boolean | no | `true` → historical banners (default false) |
| `minify` | boolean | no | `true` → ports + general host info only (default false per Shodan host method) |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Shodan search string (`query`). Filters use `filter:value` (e.g. `apache country:DE`). Empty → `{ error }` no HTTP. |
| `facets` | string | no | Comma-separated facet names (`property` or `property:count`) |
| `page` | number | no | Page of 100 matches (default 1). Pages past 1 consume query credits. |
| `minify` | boolean | no | Override config `search_minify` |
| `fields` | string | no | Comma-separated banner fields to return |

### `operation = dns-resolve`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `hostnames` | string | yes | Comma-separated hostnames |

### `operation = dns-reverse`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ips` | string | yes | Comma-separated IPv4/IPv6 addresses |

### `operation = account-profile`

No input fields.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object | `host` / `account-profile` 2xx | Host object or profile (`member`, `credits`, `display_name`, `created`) |
| `matches` | array | `search` 2xx | Banner matches (may be minified) |
| `total` | number | `search` 2xx | Total matched (when present) |
| `facets` | object | `search` 2xx | Facet buckets when requested |
| `resolved` | object | `dns-resolve` / `dns-reverse` 2xx | Hostname→IP or IP→hostname[] map as returned |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 1 s floor (Shodan 1 rps); 60 on 429 if no header |

## Legal

- REST docs: https://developer.shodan.io/api
- Terms: https://static.shodan.io/legal/terms.html
- Credits: https://help.shodan.io/the-basics/credit-types-explained
- On-demand scanning (do not wrap): https://help.shodan.io/the-basics/on-demand-scanning
- **Prohibited for this node:** initiating scans or crawls of networks the operator does not have authorization to test; building exploit/crawl helpers; scraping the website. `search` / `host` read Shodan’s **existing** index only.
- Query credits: 1 credit when the search uses a **filter** or when requesting page 2+ (1 credit per 100 results past page 1). Host IP lookup does not consume query credits (published 2026-08-20).
- Rate limit: **1 request per second** on API plans (https://account.shodan.io/billing, 2026-08-20). Do not tight-loop.
- Results can include banner payloads, hostnames, and geolocation. Treat as potentially sensitive infrastructure data. Do not use lookups to target third parties for attack.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Auth: query `key={api_key}` as documented. Build the URI so logs/redaction can strip `key`. Prefer never logging the full URI.
- Empty `api_key` → `{ error: "Missing Shodan API key. Bind api_key in node parameters." }` — no HTTP.
- Unknown `operation` → `{ error }` no HTTP. Do not implement verbs listed under **DO NOT BUILD**.
- `search` default `minify=true` unless input overrides.
- Response body cap **1_000_000** bytes.
- No auto-retry inside the handler (credits + 1 rps). 429 → `{ error, http_status: 429, retry_after_seconds }`.
- Logs: operation, status, result counts, sizes — never `api_key`, never full banner `data` blobs.
- **UNVERIFIED APIs:** none of the five catalog-mapped methods (all live on https://developer.shodan.io/api 2026-08-20). Catalog ops unchanged.
- **DO NOT BUILD:** `POST /shodan/scan`, `POST /shodan/scan/internet`, `GET /shodan/scans`, `GET /shodan/scan/{id}`, exploits, Streaming API, alerts/notifiers, `GET /dns/domain/{domain}` (not in catalog), utility `/tools/*`. Lookup-only. No unauthorized scanning.
