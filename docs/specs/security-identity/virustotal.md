# VirusTotal

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/virustotal/`
- **Researched:** 2026-08-20
- **Official API:** VirusTotal API v3 (`virustotal-api-v3` / OpenAPI `3.0`)
- **Docs:** https://docs.virustotal.com/reference/overview
- **Auth:** api_key — secret binding: `api_key` (header `x-apikey`)

## Scope

One node for VirusTotal **API v3** reputation and submission. The designer picks `operation`; the handler calls the matching REST path on `https://www.virustotal.com/api/v3` (override only via `base_url` for documented regional/enterprise hosts).

`scan-file` uploads a platform **file ref-id** (never inline bytes). Uploading a customer file **shares that sample with VirusTotal** (vendor analysis + Community visibility). Prefer `get-file-report` by hash when the operator already has MD5/SHA-1/SHA-256.

Do not call API v2. Do not scrape virustotal.com HTML. Do not wrap sample **download**, Livehunt, Retrohunt, feeds, or Premium Intelligence search.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `scan-url` | POST | `/urls` | Submit a URL for analysis (`application/x-www-form-urlencoded` field `url`). Returns an Analysis id. |
| `scan-file` | POST | `/files` (≤32 MiB) or upload URL from `GET /files/upload_url` (32–650 MiB) | Submit a file sample. Sharing the sample with VirusTotal is inherent to this call. |
| `get-file-report` | GET | `/files/{id}` | Report for MD5, SHA-1, or SHA-256. Prefer this over re-upload. |
| `get-url-report` | GET | `/urls/{id}` | Report for a URL id (SHA-256 of canonized URL **or** unpadded URL-safe base64 of the raw URL). |
| `get-ip-report` | GET | `/ip_addresses/{ip}` | IP address object. |
| `get-domain-report` | GET | `/domains/{domain}` | Domain object. |
| `search` | GET | `/search` | Lookup by hash, URL, domain, IP, or comment tag (`query`). Public search — **not** Premium `/intelligence/search`. |

Base origin default: `https://www.virustotal.com/api/v3`. Scan verbs return an Analysis object; this node does **not** poll `GET /analyses/{id}` (not a catalog operation). Downstream steps use `get-file-report` / `get-url-report` after analysis completes.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get-file-report` | no | Chooses the row above |
| `api_key` | password | yes | empty | yes | Community/Premium API key. Header `x-apikey` only. Never on INPUT, never logged. |
| `base_url` | string | no | `https://www.virustotal.com/api/v3` | no | Origin+prefix. HTTPS only. No trailing slash. |
| `timeout_seconds` | number | no | `25` (scan-file: `120`) | no | Connect + request timeout. Floor 1 s. |
| `max_upload_bytes` | number | no | `33554432` (32 MiB) | no | Soft cap for `scan-file`. Hard platform/VT max 650 MiB via upload URL. Never raise above 650 MiB. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | `scan-file.file` is type `file` (platform ref-id). No secrets. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` or file bytes |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = scan-url`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `url` | string | yes | Absolute URL to analyse. Trim; empty / non-http(s) → `{ error }` no HTTP. |

### `operation = scan-file`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Platform file **ref-id**. Handler resolves bytes just-in-time. Never accept inline base64/bytes on this field. Empty/missing ref → `{ error }` no HTTP. |
| `password` | password | no | Optional ZIP password (`password` multipart field). Treat as secret: never log, never echo on OUTPUT. |

### `operation = get-file-report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | MD5, SHA-1, or SHA-256. Alias `hash`. Hex only; trim. |

### `operation = get-url-report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | no* | VT URL identifier (SHA-256 form or unpadded URL-safe base64). |
| `url` | string | no* | Raw URL. Handler encodes with **unpadded URL-safe base64** (RFC 4648 §3.2) as documented: https://docs.virustotal.com/reference/url |

\*Exactly one of `id` or `url` required.

### `operation = get-ip-report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ip` | string | yes | IPv4 or IPv6. URL-encode path segment (IPv6 colons). |

### `operation = get-domain-report`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | yes | FQDN. No scheme. |

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | Search query (`query`). Hash, URL, domain, IP, or comment tag as documented. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `data` | object/array | 2xx | Provider JSON `data` (File / URL / Domain / IP / Analysis / search list). Cap body; do not flatten into logs. |
| `analysis_id` | string | `scan-url` / `scan-file` 2xx | `data.id` of the Analysis object |
| `resource_type` | string | 2xx | `data.type` when present (`file`, `url`, `domain`, `ip_address`, `analysis`, …) |
| `error` | string | failure | Actionable, no key, no bytes |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx/timeout |

## Legal

- Overview: https://docs.virustotal.com/reference/overview
- Public vs Premium: https://docs.virustotal.com/reference/public-vs-premium-api
- Terms (docs pointer): https://docs.virustotal.com/docs/terms-of-service — **UNVERIFIED:** GUI HTML at `https://www.virustotal.com/gui/terms-of-service` was JS-gated on 2026-08-20; constraints below are from the API docs pages fetched that day.
- Public API (free Community key): **500 requests/day**, **4 requests/minute**. Must **not** be used in commercial products or services; must **not** be used in business workflows that do not contribute new files; must **not** be used as a substitute for antivirus; must **not** be integrated in any project that may **harm the antivirus industry** directly or indirectly (reports are not a weapon). Multiple accounts to bypass quota are forbidden. Premium quotas follow the operator’s contract.
- Uploaded files and submitted URLs leave the customer host and are analysed by VirusTotal and participating vendors. Treat as a disclosure of customer content (may include PHI/PII).
- Official-API-first: HTTPS REST v3 only. No scrape, no unofficial GUI wrappers.
- Rate-limit 429: do not tight-loop inside the handler.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `x-apikey: {api_key}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty `api_key` → `{ error: "Missing VirusTotal API key. Bind api_key in node parameters." }` — no HTTP.
- `scan-file` body: `multipart/form-data` field `file` (filename from metadata). Size 0 → error. Size > 32 MiB: `GET /files/upload_url` then **one** POST to the returned URL (same multipart). Size > 650 MiB → `{ error }` no upload. Each upload URL is single-use.
- `scan-url` body: `application/x-www-form-urlencoded` `url=…`.
- `get-url-report` from raw `url`: URL-safe base64 **without** `=` padding.
- Response body cap **1_000_000** bytes. Logs: operation, HTTP status, sizes, hash/id lengths — never `api_key`, never file bytes, never ZIP password.
- 429 → `{ error, http_status: 429, retry_after_seconds }` (honor `Retry-After`). Do not auto-retry inside the handler.
- Other 4xx → `{ error }` with truncated body (240 chars), key redacted to `***`.
- **UNVERIFIED:** GUI ToS HTML (`/gui/terms-of-service`) was JS-gated on 2026-08-20; Public API constraints are from https://docs.virustotal.com/docs/api-overview and https://docs.virustotal.com/reference/public-vs-premium-api. Docs say **32MB** / **650MB** — treat as those decimal limits, not a guessed MiB conversion.
- **DO NOT BUILD:** API v2; `GET /files/{id}/download`; `POST /files/{id}/analyse` (rescan); behaviours/PCAP; comments; Premium `GET /intelligence/search`; `GET /analyses/{id}` as a catalog op; Livehunt/Retrohunt/feeds. Catalog `search` is public `GET /search` only. Catalog ops otherwise 1:1.
