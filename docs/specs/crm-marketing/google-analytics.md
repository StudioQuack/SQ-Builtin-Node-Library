# Google Analytics

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/google-analytics/`
- **Researched:** 2026-08-20
- **Official API:** Google Analytics **Data API v1beta** + **Admin API v1beta** (GA4 only)
- **Docs:** Data API https://developers.google.com/analytics/devguides/reporting/data/v1 — `runReport` https://developers.google.com/analytics/devguides/reporting/data/v1/rest/v1beta/properties/runReport — `runRealtimeReport` https://developers.google.com/analytics/devguides/reporting/data/v1/rest/v1beta/properties/runRealtimeReport — `getMetadata` https://developers.google.com/analytics/devguides/reporting/data/v1/rest/v1beta/properties/getMetadata — Admin https://developers.google.com/analytics/devguides/config/admin/v1
- **Auth:** OAuth2 — secrets: `clientSecret`, `refreshToken`. Scopes: `https://www.googleapis.com/auth/analytics.readonly` (reports) or `https://www.googleapis.com/auth/analytics` / `analytics.edit` (admin list)

## Scope

One node for **GA4**. Data API host `https://analyticsdata.googleapis.com`; Admin host `https://analyticsadmin.googleapis.com`. **Universal Analytics is dead** (sunset 2024-07-01). Catalog verb `userActivity.search` was Reporting API v4 (`analyticsreporting.googleapis.com/v4/userActivity:search`) and **must not be implemented** — Google’s UA docs now 404. Individual-user activity search is not a Data API v1beta method. **Customer owns the GA4 property.** Google APIs ToS apply. No UA view ids (`ga:`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `report.run` | POST | `/v1beta/{property=properties/*}:runReport` | Standard dimension/metric report |
| `report.realtime` | POST | `/v1beta/{property=properties/*}:runRealtimeReport` | Last 30 min (60 min on 360) |
| `metadata.get` | GET | `/v1beta/{name=properties/*/metadata}` | Dimensions/metrics catalog |
| `property.list` | GET | `/v1beta/accountSummaries` (Admin) | List account/property summaries |

Omitted: `userActivity.search` (UA). Alpha-only Admin methods stay off this node.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `report.run` | no | Chooses the row above |
| `clientId` | string | yes | | no | OAuth client |
| `clientSecret` | password | yes | | yes | OAuth secret |
| `refreshToken` | password | yes | | yes | Offline token |
| `propertyId` | string | reports | | no | Numeric GA4 property id (no `properties/` prefix required in config) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = report.run`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `propertyId` | string | yes | GA4 property id |
| `dateRanges` | object[] | yes | `{ startDate, endDate }` (`YYYY-MM-DD` or `NdaysAgo`) |
| `dimensions` | object[] | no | `{ name }` — max 9 |
| `metrics` | object[] | yes | `{ name }` — at least one |
| `dimensionFilter` / `metricFilter` | object | no | `FilterExpression` |
| `limit` | number | no | Default 10_000; API max 250_000 — **cap at 10_000** in this node |
| `offset` | number | no | Pagination |
| `orderBys` | object[] | no | Sort |
| `keepEmptyRows` | boolean | no | Include zero rows |
| `returnPropertyQuota` | boolean | no | Include quota object |
| `currencyCode` | string | no | ISO 4217 |

### `operation = report.realtime`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `propertyId` | string | yes | GA4 property id |
| `dimensions` / `metrics` | object[] | metrics yes | Same names as Data API realtime schema |
| `minuteRanges` | object[] | no | `{ startMinutesAgo, endMinutesAgo }`; max 2 ranges |
| `limit` | number | no | Cap at 10_000 |
| `returnPropertyQuota` | boolean | no | Realtime quota |

### `operation = metadata.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `propertyId` | string | yes | Use `0` for universal metadata only (no custom defs) |

### `operation = property.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pageSize` | number | no | Admin page size (cap 200) |
| `pageToken` | string | no | Next page |

## Output fields (by operation)

### `report.run` / `report.realtime`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `dimensionHeaders` | object[] | success | Column names |
| `metricHeaders` | object[] | success | Metric names/types |
| `rows` | object[] | success | `{ dimensionValues, metricValues }` |
| `rowCount` | number | success | Total rows before limit |
| `propertyQuota` | object | if requested | Remaining quota |
| `metadata` | object | `report.run` | currency/timezone |
| `error` | object | failure | `{ status, code, message }` |

### `metadata.get`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `name` | string | success | `properties/{id}/metadata` |
| `dimensions` | object[] | success | `DimensionMetadata` |
| `metrics` | object[] | success | `MetricMetadata` |
| `error` | object | failure | `{ status, code, message }` |

### `property.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `accountSummaries` | object[] | success | Accounts + property summaries |
| `nextPageToken` | string | when present | Pagination |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- Google APIs ToS: https://developers.google.com/terms
- Google Analytics Terms: https://www.google.com/analytics/terms/
- Measurement/user-data policies: https://support.google.com/analytics/answer/4597324
- Data API quota is per property (tokens for core vs realtime). `returnPropertyQuota` to surface it; `429` when exhausted.
- Reports can include user-level dimensions depending on property identity settings. Minimize. Never log access tokens or row values containing user ids/emails.
- Official-API-first: `analyticsdata.googleapis.com` and `analyticsadmin.googleapis.com` only. No UA Core Reporting, no Measurement Protocol from this node, no scraping the GA UI.

## Implementation notes

- JDK `HttpClient` only. Path property: `properties/{propertyId}`.
- OAuth refresh in-process; scope must include `analytics.readonly` for Data API.
- Timeouts: connect 10s, read 60s. Body cap 4 MiB.
- Retry: `429` honor `Retry-After`; `5xx` backoff + jitter, max 3.
- Reject `propertyId` containing `UA-` or `ga:`.
- Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
