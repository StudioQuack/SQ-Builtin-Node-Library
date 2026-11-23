# Google Ads

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/google-ads/`
- **Researched:** 2026-08-20
- **Official API:** Google Ads API **v25** (v25.0 2026-07-22; **v25.1** 2026-08-19, REST prefix still `/v25/`)
- **Docs:** https://developers.google.com/google-ads/api/docs/start — REST auth https://developers.google.com/google-ads/api/rest/auth — GAQL https://developers.google.com/google-ads/api/docs/query/overview — mutate https://developers.google.com/google-ads/api/rest/common/mutate — release notes https://developers.google.com/google-ads/api/docs/release-notes
- **Auth:** OAuth2 (`https://www.googleapis.com/auth/adwords`) **plus** developer token — secrets: `clientSecret`, `refreshToken`, `developerToken`

## Scope

One node for the Google Ads API REST interface at `https://googleads.googleapis.com/v25`. Every call needs **OAuth access token**, **`developer-token`**, and — when a manager (MCC) acts on a client — **`login-customer-id`** (10-digit, no dashes). Reads use **GAQL** via `GoogleAdsService.Search` / `SearchStream`. Writes use resource `:mutate`. Conversion upload uses `ConversionUploadService`. **There is no official push webhook** for campaign changes; catalog `trigger.listen` is **omitted**. Test-only developer tokens cannot touch production accounts. **Customer owns the Google Ads / MCC accounts.** JDK HTTP only — no Google Ads client library.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `customer.list` | GET | `/v25/customers:listAccessibleCustomers` | List customer resource names the token can access |
| `campaign.get` | POST | `/v25/customers/{customerId}/googleAds:search` | One campaign via GAQL |
| `campaign.list` | POST | `/v25/customers/{customerId}/googleAds:search` | Campaigns via GAQL |
| `campaign.mutate` | POST | `/v25/customers/{customerId}/campaigns:mutate` | Create/update/remove campaigns |
| `adGroup.list` | POST | `…/googleAds:search` | Ad groups via GAQL |
| `ad.list` | POST | `…/googleAds:search` | Ads via GAQL |
| `report.query` | POST | `…/googleAds:searchStream` | Reporting GAQL (metrics) |
| `conversion.upload` | POST | `/v25/customers/{customerId}:uploadClickConversions` | Offline click conversions |
| `audience.mutate` | POST | `/v25/customers/{customerId}/userLists:mutate` | User list create/update/remove |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | Chooses the row above |
| `clientId` | string | yes | | no | Google Cloud OAuth client |
| `clientSecret` | password | yes | | yes | OAuth secret |
| `refreshToken` | password | yes | | yes | Offline refresh token |
| `developerToken` | password | yes | | yes | API Center token (22-char) |
| `loginCustomerId` | string | MCC | | no | Manager customer id header |
| `customerId` | string | most ops | | no | Target 10-digit customer id |
| `apiVersion` | string | no | `v25` | no | Path version |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `customer.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Uses config OAuth + developer token |

### `campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | 10-digit id |
| `campaignId` | string | yes | Numeric campaign id |

Handler GAQL (fixed, not free-form): `SELECT campaign.id, campaign.name, campaign.status, campaign.advertising_channel_type, campaign.resource_name FROM campaign WHERE campaign.id = {id}`.

### `campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Target account |
| `pageSize` | number | no | Search page size |
| `pageToken` | string | no | Next page |
| `statusFilter` | string | no | Map to `campaign.status` enum in a **fixed** WHERE clause (`ENABLED` / `PAUSED` / `REMOVED`) |

### `campaign.mutate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Target account |
| `operations` | object[] | yes | `{ create \| update \| remove }` per REST mutate; updates need `updateMask` |
| `partialFailure` | boolean | no | Allow mixed success |

Cap `operations.length` (e.g. 100). Reject unknown resource fields.

### `adGroup.list` / `ad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Target account |
| `campaignId` | string | no | Restrict GAQL WHERE |
| `pageToken` | string | no | Pagination |

Use fixed SELECT lists from the GAQL cookbook (campaign / ad_group / ad_group_ad). Do not accept arbitrary GAQL from INPUT (injection / quota).

### `report.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Target account |
| `query` | string | yes | GAQL string; **allowlist resources** (`campaign`, `ad_group`, `ad_group_ad`, `customer`, `metrics` segments). Reject other FROM clauses |
| `pageSize` | number | no | Search page size (SearchStream may ignore) |

### `conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Account that owns the conversion action |
| `conversions` | object[] | yes | `gclid` or `gbraid`/`wbraid`, `conversionAction`, `conversionDateTime`, `conversionValue`, `currencyCode` |
| `partialFailure` | boolean | no | Recommended `true` |

### `audience.mutate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `customerId` | string | yes | Target account |
| `operations` | object[] | yes | UserList mutate operations |

## Output fields (by operation)

### `customer.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `resourceNames` | string[] | success | `customers/{id}` |
| `error` | object | failure | `{ status, code, message }` |

### `campaign.get` / `campaign.list` / `adGroup.list` / `ad.list` / `report.query`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `results` | object[] | success | `GoogleAdsRow` JSON (camelCase) |
| `nextPageToken` | string | when present | Pagination |
| `error` | object | failure | Ads error object summary (no tokens) |

### `campaign.mutate` / `audience.mutate`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `results` | object[] | success | `resourceName` per op |
| `partialFailureError` | object | mixed | Partial-failure details |
| `error` | object | failure | `{ status, code, message }` |

### `conversion.upload`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | success | HTTP status |
| `partialFailureError` | object | when set | Per-conversion errors |
| `results` | object[] | success | Upload results |
| `error` | object | failure | `{ status, code, message }` |

## Legal

- Google Ads API Terms: https://developers.google.com/google-ads/api/docs/api-policy/terms
- API policies / RMF: https://support.google.com/adspolicy/answer/6169371
- Google APIs ToS: https://developers.google.com/terms
- Google Ads program terms bind the **advertiser**. Permissible use: campaign creation/management/reporting only.
- Access levels: Test (15k ops, test accounts), Basic (15k/day production), Standard (higher). Conversion upload needs production access.
- PII: customer match / user lists / conversion identifiers (GCLID) are sensitive. Never log `developer-token`, OAuth secrets, emails, or GCLIDs.
- Official-API-first: `googleads.googleapis.com` + `oauth2.googleapis.com` token endpoint only.

## Implementation notes

- JDK `HttpClient` only. Headers: `Authorization: Bearer`, `developer-token`, `login-customer-id` (MCC), `Content-Type: application/json`.
- Refresh OAuth with `grant_type=refresh_token`; cache access token in memory for the dispatch only.
- Timeouts: connect 10s, read 60s (`searchStream`). Body cap 4 MiB.
- Retry: `429` / `UNAVAILABLE` with jitter; honor Ads retry guidance; max 3. Do not retry successful mutates.
- Strip dashes from customer ids. Errors → `{ error }` on OUTPUT.
- **DO NOT BUILD** in this task.
