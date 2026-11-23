# LinkedIn Ads

Official **LinkedIn Marketing API** (ads) on `https://api.linkedin.com/rest/`. Handler: `OfficialApiNode` via `api_spec.json`. This is **not** the LinkedIn share / social / community-management node (those stay on `/v2/` consumer APIs).

- **Docs:** https://learn.microsoft.com/en-us/linkedin/marketing/?view=li-lms-2026-08 · Versioning: https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08
- **Base:** `https://api.linkedin.com/rest`
- **Auth:** Bearer `oauth_access_token` (secret). Typical ads scopes: `r_ads` / `rw_ads` / `r_ads_reporting`. Lead ops need Lead Sync scopes. Access is a partner program.
- **Version headers:** Every call must send `Linkedin-Version: 202608` and `X-Restli-Protocol-Version: 2.0.0`. Missing version → error. Version `202508` sunsets 2026-08-17. extraHeaders send both on every call.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `account.list` | GET | `/adAccounts` |
| `campaign.create` / `get` / `list` / `update` | POST/GET | `/adAccounts/{ad_account_id}/adCampaigns` |
| `creative.create` / `list` | POST/GET | `/adAccounts/{ad_account_id}/creatives` (create: `campaign`, `content` e.g. `{"reference":"urn:li:share:…"}`, optional `status` → `intendedStatus`) |
| `analytics.get` | GET | `/adAnalytics` |
| `audience.create` | POST | `/dmpSegments` (`name`, `type`, `account`, `sourcePlatform`, `destinations`) |
| `leadForm.list` | GET | `/leadForms` (forms only — not responses) |
| `lead.get` | GET | `/leadFormResponses` (Lead Sync) |
| `conversion.upload` | POST | `/conversionEvents` |
| `trigger.lead` | ingress | BPMN message start / catch / boundary |

`ad_account_id` may be set in config and reused as the path id. `r_ads` cannot retrieve lead responses.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `oauth_access_token` | password | yes | Member access token |
| `ad_account_id` | string | no | Numeric ad account id for paths |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes tokens.

## Legal

- Marketing API terms: https://www.linkedin.com/legal/l/marketing-api-terms
- Data storage: ad/org IDs uncapped; non-member reporting **1 year**; lead responses only on behalf of the authorized client; other members’ profile data **24-hour cache, do not store**.
- Do not implement organic share/posting here. Leads and conversion identifiers are PII. Caller owns consent. Customer owns the ad account.
