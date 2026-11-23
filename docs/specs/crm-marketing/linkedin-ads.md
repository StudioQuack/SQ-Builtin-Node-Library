# LinkedIn Ads

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/linkedin-ads/`
- **Researched:** 2026-08-20
- **Official API:** LinkedIn Marketing API Program, version header **`202608`** (August 2026)
- **Docs:** https://learn.microsoft.com/en-us/linkedin/marketing/?view=li-lms-2026-08 · Versioning: https://learn.microsoft.com/en-us/linkedin/marketing/versioning?view=li-lms-2026-08 · Data storage: https://learn.microsoft.com/en-us/linkedin/marketing/data-storage-requirements?view=li-lms-2026-08
- **Auth:** oauth2 — secret bindings: `client_secret`, refresh/access tokens (daemon-held)

## Scope

One **ads** node for the LinkedIn Marketing API on `https://api.linkedin.com/rest/`. This is **not** the LinkedIn share / social / community-management node (those stay on `/v2/` consumer APIs). Access is a **partner program** (Marketing Developer Platform / ads products on the LinkedIn developer portal). Every call must send `Linkedin-Version: 202608` and `X-Restli-Protocol-Version: 2.0.0`. Missing version header → error (LinkedIn does not default to latest). Version `202508` sunsets **2026-08-17**.

Lead Sync (`leadForms`, `leadFormResponses`, `leadNotifications`) is a **separate program** from Advertising API; `r_ads` / `rw_ads` cannot retrieve leads.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `account.list` | GET | `/adAccounts` | List ad accounts the member can access |
| `campaign.create` | POST | `/adAccounts/{adAccountId}/adCampaigns` | Create campaign |
| `campaign.get` | GET | `/adAccounts/{adAccountId}/adCampaigns/{campaignId}` | Fetch one campaign |
| `campaign.list` | GET | `/adAccounts/{adAccountId}/adCampaigns` | List/search campaigns |
| `campaign.update` | POST | `/adAccounts/{adAccountId}/adCampaigns/{campaignId}` | Partial update (Rest.li) |
| `creative.create` | POST | `/adAccounts/{adAccountId}/creatives` | Create creative |
| `creative.list` | GET | `/adAccounts/{adAccountId}/creatives` | List creatives |
| `analytics.get` | GET | `/adAnalytics?q=analytics&…` | Spend/impressions/clicks. Use query tunneling if URL > max. |
| `audience.create` | POST | `/dmpSegments` | Empty DMP segment (`rw_dmp_segments`, Audiences program — **not** auto-granted with ads). Max 1,000 segments/account. |
| `leadForm.list` | GET | `/leadForms` | List forms (Advertising API; **not** lead responses) |
| `lead.get` | GET | `/leadFormResponses` | Fetch submissions — **Lead Sync** permission required |
| `conversion.upload` | POST | `/conversionEvents` (stream) and/or `/conversions` (rule) | Conversions API |
| `trigger.lead` | POST | `/leadNotifications` | Webhook subscription for new leads (Lead Sync) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `account.list` | no | Chooses the row above |
| `client_id` | string | yes | empty | no | LinkedIn app client id |
| `client_secret` | password | yes | empty | yes | App secret |
| `oauth_access_token` | password | yes | empty | yes | Member access token with ads scopes |
| `ad_account_id` | string | no | empty | no | Numeric account id used in paths (`sponsoredAccount` URN built as `urn:li:sponsoredAccount:{id}`) |
| `linkedin_version` | string | no | `202608` | no | `Linkedin-Version` YYYYMM. Must not be sunset. |
| `timeout_seconds` | number | no | `30` | no | Floor 1 s. Analytics may need more. |

Required ads scopes typically include `r_ads` / `rw_ads` and `r_ads_reporting`. Lead ops need Lead Sync scopes (`r_ads_leadgen_automation` family as published on the Lead Sync page).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = account.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `count` | number | no | Page size |
| `start` | number | no | Offset |

### `operation = campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account id |
| `name` | string | yes | Campaign name |
| `type` | string | yes | e.g. `SPONSORED_UPDATES` |
| `campaign_group` | string | yes | `urn:li:sponsoredCampaignGroup:…` |
| `cost_type` | string | yes | e.g. `CPC` |
| `run_schedule` | object | yes | `{ start, end }` epoch ms as documented |
| `targeting_criteria` | object | yes | Rest.li targeting |
| `unit_cost` | object | no | `{ amount, currencyCode }` |
| `objective_type` | string | no | e.g. `LEAD_GENERATION` |
| `locale` | object | no | `{ country, language }` |

### `operation = campaign.get` | `campaign.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account |
| `campaign_id` | string | yes | Campaign id |
| `patch` | object | update | Fields to change |

### `operation = campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account |
| `search` | object | no | Finder/search query as documented |
| `count` | number | no | Page size |
| `start` | number | no | Offset |

### `operation = creative.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account |
| `campaign` | string | yes | `urn:li:sponsoredCampaign:…` |
| `type` | string | yes | e.g. `TEXT_AD` |
| `status` | string | no | `ACTIVE` / `PAUSED` |
| `variables` | object | yes | Creative payload (`clickUri`, text/title, …) |

### `operation = creative.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account |
| `count` | number | no | Page size |
| `start` | number | no | Offset |

### `operation = analytics.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `accounts` | array | no | Account URNs |
| `campaigns` | array | no | Campaign URNs |
| `date_range_start` | object | yes | `{ year, month, day }` |
| `date_range_end` | object | no | End date |
| `time_granularity` | string | yes | e.g. `DAILY` / `MONTHLY` |
| `pivot` | string | yes | e.g. `CAMPAIGN` / `CREATIVE` |
| `fields` | array | no | Metric names (max 20). Default may be impressions+clicks only. |

If the encoded GET URL is long, POST the query with `X-HTTP-Method-Override: GET` and `Content-Type: application/x-www-form-urlencoded` (query tunneling).

### `operation = audience.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes unless config | Account |
| `name` | string | yes | Segment name |
| `account` | string | yes | `urn:li:sponsoredAccount:…` |
| `type` | string | yes | `USER` \| `COMPANY` \| list-upload types |
| `source_platform` | string | yes | LinkedIn-assigned ENUM: `DIRECT_API` / `AGENCY_API` / `PARTNER_API` / `LIST_UPLOAD` |
| `description` | string | no | Description |

### `operation = leadForm.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `owner` | string | yes | Organization or ad account URN as required by Lead Forms finder |

### `operation = lead.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `account` | string | yes | `urn:li:sponsoredAccount:…` |
| `form` | string | no | `urn:li:adForm:…` |
| `count` | number | no | Page size |
| `start` | number | no | Offset |

### `operation = conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversion` | string | yes | Conversion URN / rule |
| `conversion_happened_at` | number | yes | Epoch ms |
| `user` | object | yes | Matched identifiers as Conversions API documents (hash emails per LinkedIn rules) |
| `event_id` | string | no | Dedup |

### `operation = trigger.lead`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `webhook_url` | string | yes | StudioQuack message-event ingress |
| `owner` | string | yes | Organization / account URN for notifications |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create | Rest.li id from `x-restli-id` |
| `elements` | array | list/analytics | `elements` array |
| `paging` | object | list | `start`, `count`, links |
| `resource` | object | get | Entity JSON (capped) |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After` |

## Legal

- Marketing API terms: https://www.linkedin.com/legal/l/marketing-api-terms
- Partner terms: https://business.linkedin.com/marketing-solutions/marketing-partners/become-a-partner/marketing-developer-program/terms-and-conditions
- **Data storage (must honor; more restrictive wins):**
  - Ad/org IDs and URNs: no duration cap
  - Ad account admin/reporting (non-member-level): **1 year**
  - Lead gen **response** data: no duration cap, but **only on behalf of the authorized client**; no multi-account lead database; no commingling to append profiles
  - Other members’ profile data (not lead form, not the authenticated member): **24-hour cache only, do not store**
  - Members’ social activity: **48 hours**
- Do not implement organic share/posting here.
- Leads and conversion identifiers are PII. Caller owns consent. Customer owns the ad account.
- Never log OAuth tokens, member emails, or lead answers.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Authorization: Bearer`, `Linkedin-Version: 202608`, `X-Restli-Protocol-Version: 2.0.0`, `Content-Type: application/json`.
- Empty token → `{ error }` no HTTP. 403 when the member lacks the ad-account role.
- Response cap **1_000_000** bytes. Timeout default 30 s.
- 429 `SEGMENT_LIMIT_EXCEEDED` on DMP create → `{ error }` without retry loop.
- Logs: operation, version, account id, http_status, sizes — never tokens or lead payloads.

`audience.create` path `POST /dmpSegments` verified on matched-audiences docs. Requires **Audiences** partner approval + `rw_dmp_segments`. Streaming users: `/dmpSegments/{id}/users` (600 req/user/min as of 2022-10-31) — not a catalog op. Numeric global ads QPS is portal-specific — honor 429.
