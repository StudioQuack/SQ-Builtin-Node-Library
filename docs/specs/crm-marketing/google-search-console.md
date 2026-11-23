# Google Search Console

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/google-search-console/`
- **Researched:** 2026-08-20
- **Official API:** Search Console API — **Webmasters v3** (sites, sitemaps, searchAnalytics) + **Search Console v1** (URL Inspection)
- **Docs:** https://developers.google.com/webmaster-tools — API index https://developers.google.com/webmaster-tools/v1/api_reference_index — searchAnalytics.query https://developers.google.com/webmaster-tools/v1/searchanalytics/query — authorize https://developers.google.com/webmaster-tools/v1/how-tos/authorizing
- **Auth:** OAuth2 — secrets: `clientSecret`, `refreshToken`. Scopes: `https://www.googleapis.com/auth/webmasters.readonly` or `https://www.googleapis.com/auth/webmasters`

## Scope

One node for **Google Search Console**. Sites/sitemaps/search analytics use `https://www.googleapis.com/webmasters/v3`. URL Inspection uses `https://searchconsole.googleapis.com/v1/urlInspection/index:inspect`. The API returns **performance for properties the user has verified** (URL-prefix `https://www.example.com/` or domain `sc-domain:example.com`). `site.add` **adds the property to the user’s Search Console list**; it does **not** complete verification — Google still requires a verification token (DNS/HTML/file). **Customer owns the Search Console property.** This is **not** a general web-search API and must not be used to scrape SERPs. Google APIs ToS apply.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `site.list` | GET | `/webmasters/v3/sites` | List properties the user can access |
| `site.add` | PUT | `/webmasters/v3/sites/{siteUrl}` | Add a site resource (verification still required) |
| `searchAnalytics.query` | POST | `/webmasters/v3/sites/{siteUrl}/searchAnalytics/query` | Clicks/impressions/CTR/position |
| `sitemap.list` | GET | `/webmasters/v3/sites/{siteUrl}/sitemaps` | Listed sitemaps |
| `sitemap.submit` | PUT | `/webmasters/v3/sites/{siteUrl}/sitemaps/{feedpath}` | Submit sitemap URL |
| `urlInspection.inspect` | POST | `/v1/urlInspection/index:inspect` | Indexing status for one URL |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `site.list` | no | Chooses the row above |
| `clientId` | string | yes | | no | OAuth client |
| `clientSecret` | password | yes | | yes | OAuth secret |
| `refreshToken` | password | yes | | yes | Offline token |
| `siteUrl` | string | most ops | | no | Property URL or `sc-domain:` |

Path-encode `siteUrl` (e.g. `https://www.example.com/` → `https%3A%2F%2Fwww.example.com%2F`).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = site.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Uses OAuth user |

### `operation = site.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteUrl` | string | yes | Exact property string Google expects |

### `operation = searchAnalytics.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteUrl` | string | yes | Verified property |
| `startDate` | string | yes | `YYYY-MM-DD` (PT) |
| `endDate` | string | yes | `YYYY-MM-DD` inclusive |
| `dimensions` | string[] | no | `date`, `query`, `page`, `country`, `device`, `searchAppearance`, `hour` |
| `type` | string | no | `web` (default), `image`, `video`, `news`, `discover`, `googleNews` |
| `dimensionFilterGroups` | object[] | no | `{ groupType: "and", filters: [{ dimension, operator, expression }] }` |
| `aggregationType` | string | no | `auto`, `byPage`, `byProperty`, `byNewsShowcasePanel` |
| `rowLimit` | number | no | 1–25_000; default 1_000; **cap 5_000** in this node |
| `startRow` | number | no | Offset |
| `dataState` | string | no | `final` (default), `all`, `hourly_all` |

### `operation = sitemap.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteUrl` | string | yes | Property |
| `sitemapIndex` | string | no | If listing children of a sitemap index |

### `operation = sitemap.submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `siteUrl` | string | yes | Property |
| `feedpath` | string | yes | Full sitemap URL (path-encoded in the request URL) |

### `operation = urlInspection.inspect`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `inspectionUrl` | string | yes | Absolute URL to inspect |
| `siteUrl` | string | yes | Verified property containing that URL |
| `languageCode` | string | no | e.g. `en-US` |

## Output fields (by operation)

### `site.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `siteEntry` | object[] | success | `{ siteUrl, permissionLevel }` |
| `error` | object | failure | `{ status, code, message }` |

### `site.add`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `siteUrl` | string | success | Added property |
| `error` | object | failure | `{ status, code, message }` — 403 if unverified / no permission |

### `searchAnalytics.query`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `rows` | object[] | success | `{ keys, clicks, impressions, ctr, position }` |
| `responseAggregationType` | string | success | How rows were aggregated |
| `metadata` | object | when present | Incomplete date/hour for fresh data |
| `error` | object | failure | `{ status, code, message }` |

### `sitemap.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `sitemap` | object[] | success | Sitemap entries |
| `error` | object | failure | `{ status, code, message }` |

### `sitemap.submit`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `submitted` | boolean | success | `true` |
| `error` | object | failure | `{ status, code, message }` |

### `urlInspection.inspect`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `inspectionResult` | object | success | Index status, crawled-as, verdicts |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- Google APIs ToS: https://developers.google.com/terms
- Search Console / webmaster tools are for **your verified properties only**. Redistributing Search Analytics query strings or scraping third-party rankings violates Google’s terms.
- Search Analytics does not guarantee all rows — “top ones” only (official query docs).
- Query dimension values can be personal (search terms). Minimize logging (ids, sizes, status only).
- Official-API-first: `www.googleapis.com/webmasters/v3` and `searchconsole.googleapis.com` only.

## Implementation notes

- JDK `HttpClient` only.
- Timeouts: connect 10s, read 60s (searchAnalytics). Body cap 2 MiB.
- Retry: `429` honor `Retry-After`; `5xx` backoff + jitter, max 3.
- Always URL-encode `siteUrl` and `feedpath` path segments.
- Errors → `{ error }` on OUTPUT. Map 403 “site not verified” to a clear message.
- **DO NOT BUILD** in this task.
