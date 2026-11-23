# Ahrefs

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/ahrefs/`
- **Researched:** 2026-08-20
- **Official API:** Ahrefs **API v3**. Help Center article “About API v3” updated **18 June 2026**. API v2 is **fully deprecated** and must not be called.
- **Docs:** https://docs.ahrefs.com/ · Introduction https://docs.ahrefs.com/api/docs/introduction.md · Keys https://docs.ahrefs.com/api/docs/api-keys-creation-and-management.md · Help https://help.ahrefs.com/en/articles/6559232-about-api-v3 · OpenAPI https://docs.ahrefs.com/openapi.json · Terms https://ahrefs.com/terms
- **Auth:** `api_key` — secret binding: `apiToken` (`Authorization: Bearer`)

## Scope

One mostly-GET node for Ahrefs SEO research. Each tool has its own server: `https://api.ahrefs.com/v3/{tool}{path}`. OpenAPI **3.2.0**, `info.version` **3.0.0** (fetched 2026-08-20). Paid Lite+ plans consume **API units** (minimum **50 units** per billable request). Rank Tracker `/overview` is documented **free (0 units)**. Keys: workspace owner/admin, up to 1,000 keys, **1-year lifetime**. Monetary fields are **USD cents**. There is **no** `/v3/site-explorer/overview` path — catalog `siteExplorer.overview` maps to `/metrics`. `batchAnalysis` is **POST JSON**, not GET. Do not implement Ahrefs Connect OAuth in this node.

## Operations (`operation` config values)

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `siteExplorer.overview` | GET | `/v3/site-explorer/metrics` | Overview metrics for a target (`select` required). Optional sibling: `/domain-rating` when only DR is needed |
| `siteExplorer.backlinks` | GET | `/v3/site-explorer/all-backlinks` | Backlink rows (`select` required). Not `/backlinks` |
| `keywordsExplorer.overview` | GET | `/v3/keywords-explorer/overview` | Keyword metrics |
| `rankTracker.overview` | GET | `/v3/rank-tracker/overview` | Rank Tracker project overview (**0 API units**) |
| `serpOverview` | GET | `/v3/serp-overview/serp-overview` | SERP positions (`select`, `keyword`, `country` required) |
| `batchAnalysis` | POST | `/v3/batch-analysis/batch-analysis` | JSON body: `select` + `targets` (1–100), each target `{ url, mode, protocol }` |

v2 is fully deprecated. Do not call it.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `siteExplorer.overview` | no | Chooses the row above |
| `apiToken` | password | yes | | **yes** | Bearer token from Account settings → API keys |
| `output` | select | no | `json` | no | `json` (default) or `xml` if the endpoint supports `output=` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Targets and filters only |
| `output` | OUTPUT | `{ status, resource }` or `{ error }` | Never echo the Bearer token |

FUNCTION ports: none. File payloads: none (JSON/XML text).

## Input fields (by operation)

### `operation = siteExplorer.overview | siteExplorer.backlinks`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | Query `target` as documented on the endpoint |
| `mode` | string | no | `exact` / `prefix` / `domain` / `subdomains` when the path documents `mode` |
| `select` | string | **yes** | Comma-separated columns from that path’s schema. Drop unused columns to cut units |
| `limit` | number | no | Row cap; plan max rows (Help Center 2026-06-18): Lite 100, Standard 250, Advanced 500, Enterprise unlimited |
| `where` | string | no | Official JSON filter syntax |
| `orderBy` | string | no | Sort |

### `operation = keywordsExplorer.overview`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keywords` | string | yes | Keyword or list per `/overview` query params |
| `country` | string | no | ISO country |
| `select` | string | yes if the path requires it | Columns |
| `limit` | number | no | Default 1000 on this path |

### `operation = rankTracker.overview`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `project` | string | yes | Rank Tracker project identifier as documented |
| `select` | string | as documented | Columns |

### `operation = serpOverview`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `keyword` | string | yes | Query |
| `country` | string | yes | ISO 3166-1 alpha-2 |
| `select` | string | yes | Columns (`position`, `url`, `title`, `traffic`, …). `traffic`/`value` cost extra units |
| `topPositions` | number | no | Cap organic positions |
| `type` | string | no | SERP feature filter enum (`organic`, `ai_overview`, …) |
| `date` | string | no | `YYYY-MM-DDThh:mm:ss` snapshot |

### `operation = batchAnalysis`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `targets` | object[] | yes | 1–100 items: `{ url, mode: exact\|prefix\|domain\|subdomains, protocol: both\|http\|https }` |
| `select` | string[] | yes | Field names (`domain_rating`, `org_traffic`, …) |
| `country` | string | no | ISO country |
| `volumeMode` | string | no | `monthly` (default) or `average` |
| `topCountries` | number | no | 1–10 for `org_traffic_top_by_country` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP 200 |
| `resource` | object | success | Endpoint JSON (`metrics`, `backlinks`, `keywords`, …) |
| `unitsCost` | number | if present in body/headers | For operator logs (numeric only) |
| `error` | object | 4xx/5xx | `{ code, message, status }` — no token, no raw SERP dumps in logs |

## Legal

- Terms: https://ahrefs.com/terms · Privacy: https://ahrefs.com/privacy-policy. Usage is subject to Ahrefs ToS; **paid API units are non-refundable**. Use free test queries for development.
- Rate limit: **60 requests per minute** default; HTTP **429** on burst or dynamic throttling.
- Contract limits **redistribution of SERP / backlink datasets**. This node returns data to the customer’s workflow only; do not cache wholesale SERP dumps for resale.
- **(a)** Select only needed `select` fields (units scale with rows × fields). **(b)** Customer owns the Ahrefs workspace and is the controller of queries/targets they submit. **(c)** StudioQuack does not accept Ahrefs ToS on their behalf. **(d)** Never log `apiToken` or full row payloads.
- Official-API-first: `api.ahrefs.com/v3` only. v2 is dead.

## Implementation notes

- Per-tool OpenAPI: site-explorer, keywords-explorer, rank-tracker, serp-overview, batch-analysis JSON under https://docs.ahrefs.com/reference/ (index https://docs.ahrefs.com/reference/index.json). Pin against those files at build time.
- JDK `HttpClient` only. `Authorization: Bearer {apiToken}`, `Accept: application/json`. GET query-string except `batchAnalysis` (JSON POST). `output=json` unless the operator asks for xml.
- Timeouts: connect 10s, read 60s (large backlink rows). Body cap **2 MiB**; if truncated, return `{ error }` asking for a smaller `limit`/`where`.
- Retry: 429/5xx, max 3, honor `Retry-After`. Default 60 rpm limiter.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Map 401 to expired/invalid key (keys last 1 year; invalidated if creator is removed from the workspace).
- Do not implement “pagination” by silently looping until units are exhausted; one operation = one HTTP call unless INPUT explicitly provides the next `where` cursor the official filter syntax supports.
