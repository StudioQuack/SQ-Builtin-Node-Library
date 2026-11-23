# Microsoft Ads

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/microsoft-ads/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Advertising API **v13** REST (Campaign Management + Reporting). SOAP still exists but new features REST-only from **2026-10-01**; SOAP full deprecation **2027-01-31**. This node is **REST-only**.
- **Docs:** https://learn.microsoft.com/en-us/advertising/guides/?view=bingads-13 · REST migrate https://learn.microsoft.com/en-us/advertising/guides/migrate-to-rest?view=bingads-13 · OAuth https://learn.microsoft.com/en-us/advertising/guides/authentication-oauth?view=bingads-13 · Add campaigns https://learn.microsoft.com/en-us/advertising/campaign-management-service/addcampaigns?view=bingads-13
- **Auth:** oauth2 (Entra ID / Microsoft identity; Google OAuth is an alternate IdP) **plus** developer token — secret bindings: `client_secret`, `refresh_token` / `access_token`, `developer_token`.

## Scope

One node for Microsoft Advertising (search/shopping campaigns, ad groups, ads, async reports, offline conversions, audiences). Handler uses REST hosts `campaign.api.bingads.microsoft.com` and `reporting.api.bingads.microsoft.com` with path `/…/v13/…`. Do not ship SOAP `CampaignManagementService.svc`. Do not scrape ads.microsoft.com.

## Operations (`operation` config values)

Campaign host (prod): `https://campaign.api.bingads.microsoft.com/CampaignManagement/v13`  
Reporting host (prod): `https://reporting.api.bingads.microsoft.com/Reporting/v13`  
Sandbox: `*.sandbox.bingads.microsoft.com` when `environment=sandbox`.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `campaign.get` | POST | `/Campaigns/QueryByIds` | Up to 100 campaign ids |
| `campaign.list` | POST | `/Campaigns/QueryByAccountId` | All campaigns of types in `CampaignType` (default Search) |
| `campaign.mutate` | POST or PUT | `/Campaigns` | POST add; PUT update. Max 100 campaigns per call |
| `adGroup.list` | POST | `/AdGroups/QueryByCampaignId` | Ad groups in a campaign |
| `ad.list` | POST | `/Ads/QueryByAdGroupId` | Ads in an ad group (`AdGroupId` + `AdTypes`) |
| `report.submit` | POST | `/GenerateReport/Submit` | Queue async report (e.g. CampaignPerformance) |
| `report.poll` | POST | `/GenerateReport/Poll` | Poll `ReportRequestId`; download URL when Success |
| `conversion.upload` | POST | `/OfflineConversions/Apply` | Offline conversions (max 1000 / request) |
| `audience.mutate` | POST or PUT | `/Audiences` | Add or update RemarketingList / CustomAudience (max 100) |

REST header names (verified): `Authorization: Bearer {token}`, `DeveloperToken`, `CustomerId`, `CustomerAccountId`. Optional `IdentityProvider: Google` when the token is from Google OAuth.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | Chooses the row above |
| `environment` | select | no | `prod` | no | `prod` \| `sandbox` (sandbox universal developer token `BBD37VB98` is public; still treat as config) |
| `developer_token` | password | yes | empty | yes | Production token from https://ads.microsoft.com/cc/Settings/DevSettings |
| `client_id` | string | yes | empty | no | Entra app (or Google OAuth client) |
| `client_secret` | password | yes | empty | yes | OAuth client secret |
| `tenant` | string | no | `common` | no | Entra tenant for work accounts |
| `access_token` | password | yes | empty | yes | Bearer. Refresh outside the hot path |
| `customer_id` | string | yes | empty | no | Manager account (CustomerId header) |
| `account_id` | string | yes | empty | no | Ad account (CustomerAccountId + body AccountId) |
| `timeout_seconds` | number | no | `45` | no | Reports may need a long read timeout on poll only; cap 120 |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never log developer token or Bearer |

FUNCTION ports: none.

## Input fields (by operation)

### `campaign.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_ids` | array | yes | Long ids, max 100 |
| `campaign_type` | string | no | Space-delimited flags e.g. `Search Shopping`; default Search |

### `campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_type` | string | no | Same flags as get |
| `return_additional_fields` | string | no | As QueryByAccountId docs |

### `campaign.mutate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | select | yes | `add` (POST) or `update` (PUT) |
| `campaigns` | array | yes | Campaign objects (Name, BudgetType, DailyBudget, CampaignType, …). Max 100 |

### `adGroup.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | Parent campaign |
| `ad_group_type` | string | no | Filter as documented |

### `ad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_group_id` | string | yes | Body `AdGroupId` |
| `ad_types` | array | yes | Body `AdTypes` — SOAP/REST required ([GetAdsByAdGroupId](https://learn.microsoft.com/en-us/advertising/campaign-management-service/getadsbyadgroupid?view=bingads-13)); e.g. `["ResponsiveSearch"]` |
| `return_additional_fields` | string | no | Optional `AdAdditionalField` flags |

### `report.submit`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `report_type` | string | yes | Derived type name, default `CampaignPerformanceReportRequest` |
| `aggregation` | string | yes | e.g. `Daily` |
| `columns` | array | yes | Report column enums |
| `time` | object | yes | `PredefinedTime` or custom start/end dates |
| `scope` | object | no | Account/campaign/ad group scope |

### `report.poll`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `report_request_id` | string | yes | From submit. Valid ≤ 1 day |

Do not download unbounded report files into memory. When status is Success, return `download_url` on OUTPUT; a follow-up HTTP Request / file node fetches it as type `file` if the operator needs the bytes.

### `conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `offline_conversions` | array | yes | Items: `ConversionName`, `ConversionTime` (UTC), `MicrosoftClickId` or hashed email per current docs, optional `ConversionValue`, `ConversionCurrencyCode`. Max 1000 |

### `audience.mutate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `mode` | select | yes | `add` (POST) or `update` (PUT) |
| `audiences` | array | yes | RemarketingList or CustomAudience JSON. Max 100 |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `campaigns` | array | campaign.* | Campaign objects |
| `ad_groups` | array | `adGroup.list` | Ad groups |
| `ads` | array | `ad.list` | Ads |
| `partial_errors` | array | mutate / conversion | Per-item BatchError; not a thrown failure if HTTP 200 |
| `report_request_id` | string | `report.submit` | Poll token |
| `report_status` | string | `report.poll` | e.g. `Success` / `Pending` |
| `download_url` | string | poll Success | Time-limited file URL — do not log query string |
| `audiences` | array | `audience.mutate` | Ids / objects returned |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | 60 / 30 |

## Legal

- Product: Microsoft Advertising. Developer token required for all API calls (prod).
- MFA / Entra: https://learn.microsoft.com/en-us/advertising/guides/authentication-oauth-mfa
- Policies: Microsoft Advertising policies + customer’s ad account. Ads policy review is the operator’s duty.
- Customer owns the Microsoft Advertising account. StudioQuack never holds the developer token in logs.
- Offline conversions and customer-match audiences are PII / click-id sensitive. Hash emails as currently required; never log click ids or emails.
- SOAP deprecation: do not start a SOAP client. REST is the build contract.

## Implementation notes

- JDK `HttpClient` only. No Bing Ads / msads SDK.
- Headers on every REST call: `Authorization: Bearer {access_token}`, `DeveloperToken`, `CustomerId`, `CustomerAccountId`, `Content-Type: application/json`, `Accept: application/json`.
- Sandbox host swap when `environment=sandbox`.
- Body cap **1_000_000** bytes. Timeout default 45 s.
- `campaign.mutate` / `audience.mutate`: HTTP method from `mode`; missing mode → `{ error }` no HTTP.
- Report poll: one HTTP call per node run (BPMN loop if still Pending). Do not busy-wait inside the handler.
- 429 / 5xx → `{ error }` with `retry_after_seconds`. No inner retry storm.
- `ad.list`: POST `https://campaign.api.bingads.microsoft.com/CampaignManagement/v13/Ads/QueryByAdGroupId` (REST zone of GetAdsByAdGroupId). Do not invent `QueryByIds` as a list-all-in-group call (that needs AdIds, max 20).
- Logs: operation, account id, entity counts, HTTP status — never developer token, Bearer, click ids, or conversion emails.
