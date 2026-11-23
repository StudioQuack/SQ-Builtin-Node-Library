# Microsoft Ads

Official **Microsoft Advertising API v13 REST** (Campaign Management + Reporting) via `OfficialApiNode` and `api_spec.json`. SOAP `CampaignManagementService.svc` is not used (SOAP full deprecation 2027-01-31).

- **Docs:** https://learn.microsoft.com/en-us/advertising/guides/?view=bingads-13 · REST migrate https://learn.microsoft.com/en-us/advertising/guides/migrate-to-rest?view=bingads-13 · OAuth https://learn.microsoft.com/en-us/advertising/guides/authentication-oauth?view=bingads-13
- **Campaign host:** `https://campaign.api.bingads.microsoft.com/CampaignManagement/v13`
- **Reporting host:** `https://reporting.api.bingads.microsoft.com/Reporting/v13`
- **Auth:** OAuth Bearer `access_token` **plus** extra headers `DeveloperToken`, `CustomerId`, `CustomerAccountId`.
- Customer owns the Microsoft Advertising account.

## Operations

| operation | HTTP | Host + path | Required INPUT |
|---|---|---|---|
| `campaign.get` | POST | campaign `/Campaigns/QueryByIds` | `AccountId`, `CampaignIds` (max 100) |
| `campaign.list` | POST | campaign `/Campaigns/QueryByAccountId` | `AccountId` |
| `campaign.mutate` | POST | campaign `/Campaigns` (AddCampaigns) | `AccountId`, `Campaigns` (max 100) |
| `adGroup.list` | POST | campaign `/AdGroups/QueryByCampaignId` | `CampaignId` |
| `ad.list` | POST | campaign `/Ads/QueryByAdGroupId` | `AdGroupId`, `AdTypes` |
| `report.submit` | POST | reporting `/GenerateReport/Submit` | `ReportRequest` |
| `report.poll` | POST | reporting `/GenerateReport/Poll` | `ReportRequestId` |
| `conversion.upload` | POST | campaign `/OfflineConversions/Apply` | `OfflineConversions` (max 1000) |
| `audience.mutate` | POST | campaign `/Audiences` (AddAudiences) | `Audiences` (max 100) |

Paths verified against the v13 REST request URLs on learn.microsoft.com (2026-10-31). Sandbox hosts (`*.sandbox.bingads.microsoft.com`) are not rewritten from the `environment` flag — spec URLs are production.

`campaign.mutate` / `audience.mutate` are POST add. OfficialApiNode cannot switch to PUT from a `mode` field.

Report poll: one HTTP call per node run. Do not busy-wait. Do not download unbounded report files; return the provider JSON (`download_url` / `ReportDownloadUrl` stays in `data`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer |
| `developer_token` | password | yes | Header `DeveloperToken` |
| `customer_id` | string | no | Header `CustomerId` |
| `account_id` | string | no | Header `CustomerAccountId` |
| `client_id` | string | no | Entra app (refresh only; not sent) |
| `client_secret` | password | yes | OAuth client secret (refresh only; not sent) |
| `tenant` | string | no | Default `common` (refresh only; not sent) |
| `environment` | select | no | `prod` \| `sandbox` — hosts stay production |
| `timeout_seconds` | number | no | Default 45 |

## Ports

`input` / `output` only. INPUT fields are the official PascalCase JSON keys. Errors raise a BPMN error; never includes developer token, Bearer, click ids, or conversion emails.

## Legal

- Customer owns the Microsoft Advertising account. Ads policy review is the operator’s duty.
- Offline conversions and customer-match audiences are PII / click-id sensitive. Hash emails as currently required.
- MFA / Entra: https://learn.microsoft.com/en-us/advertising/guides/authentication-oauth-mfa
