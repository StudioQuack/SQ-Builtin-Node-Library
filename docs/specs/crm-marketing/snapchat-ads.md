# Snapchat Ads

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/snapchat-ads/`
- **Researched:** 2026-08-20
- **Official API:** Snap Marketing API — **Ads API v1** (`https://adsapi.snapchat.com/v1`) + **Conversions API v3** (`https://tr.snapchat.com/v3/{ID}/events`)
- **Docs:** https://developers.snap.com/api/marketing-api · https://developers.snap.com/marketing-api/Ads-API/authentication · https://developers.snap.com/marketing-api/Ads-API/organizations · https://developers.snap.com/marketing-api/Ads-API/campaigns · https://developers.snap.com/marketing-api/Ads-API/ad-squads · https://developers.snap.com/marketing-api/Ads-API/measurement · https://developers.snap.com/marketing-api/Ads-API/quick-start · https://developers.snap.com/marketing-api/Conversions-API/UsingTheAPI · https://developers.snap.com/marketing-api/Conversions-API/GetStarted
- **Auth:** OAuth 2.0 — secret bindings: `client_secret`, `refresh_token`; optional long-lived CAPI token `capi_token`

## Scope

One node for Snap Ads Manager entities (organizations, campaigns, ad squads, ads, creatives, stats) and server-side conversion upload. Access tokens from the user OAuth flow expire in **3600 seconds (60 minutes)**; refresh with `grant_type=refresh_token`. Marketing calls need scope `snapchat-marketing-api`. Conversion upload needs **`snapchat-offline-conversions-api`** (separate) **or** a Business Manager **long-lived Conversions API token**. Do not use a Snap Business SDK.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `organization.list` | GET | `/v1/me/organizations` | Orgs the token can access; `?with_ad_accounts=true` to include ad accounts |
| `campaign.create` | POST | `/v1/adaccounts/{ad_account_id}/campaigns` | Create campaign(s) |
| `campaign.list` | GET | `/v1/adaccounts/{ad_account_id}/campaigns` | List + cursor |
| `adsquad.create` | POST | `/v1/campaigns/{campaign_id}/adsquads` | Create ad squad |
| `adsquad.list` | GET | `/v1/campaigns/{campaign_id}/adsquads` | List squads |
| `ad.create` | POST | `/v1/adsquads/{ad_squad_id}/ads` | Create ad |
| `ad.list` | GET | `/v1/adsquads/{ad_squad_id}/ads` | List ads |
| `creative.create` | POST | `/v1/adaccounts/{ad_account_id}/creatives` | Create creative |
| `stats.get` | GET | `/v1/{entity}/{id}/stats` | Measurement |
| `conversion.upload` | POST | `https://tr.snapchat.com/v3/{pixel_or_app_id}/events?access_token=` | CAPI v3 events |

`entity` for stats is `adaccounts` \| `campaigns` \| `adsquads` \| `ads` (official). No catalog `trigger.listen`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | |
| `client_id` | string | yes | empty | no | OAuth app from Business Manager |
| `client_secret` | password | yes | empty | yes | Shown only at app creation |
| `refresh_token` | password | no | empty | yes | Long-lived; used to mint 60m access tokens |
| `access_token` | password | no | empty | yes | Optional pre-minted Bearer; still refresh on 401 |
| `ad_account_id` | string | no | empty | no | Default ad account UUID |
| `capi_token` | password | no | empty | yes | Long-lived CAPI token (does not expire). Used for `conversion.upload` when set |
| `timeout_seconds` | number | no | `30` | no | |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Creative media should be a `file` ref if uploading assets (follow live creatives media docs) |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `organization.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `with_ad_accounts` | boolean | no | Query `with_ad_accounts=true` (official) |

### `campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes | Path |
| `name` | string | yes | Max 375 chars |
| `status` | string | yes | `ACTIVE` \| `PAUSED` |
| `start_time` | string | yes | ISO 8601 |
| `end_time` | string | no | |
| `buy_model` | string | no | Default `AUCTION` |
| `objective_v2_properties` | object | no | `{ objective_v2_type, promotion_type? }` — required for new Ads Manager parity |
| `campaign` | object | no | Full campaign object merged into `campaigns: [ ]` body |

### `campaign.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes | |
| `limit` | number | no | e.g. 100 |
| `cursor` | string | no | From `paging.next_link` |
| `sort` | string | no | e.g. `updated_at-desc` |

### `adsquad.create` / `adsquad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | |
| `name` | string | create | |
| `status` | string | create | |
| `type` | string | create | Usually `SNAP_ADS` |
| `billing_event` | string | create | e.g. `IMPRESSION` |
| `optimization_goal` | string | create | |
| `bid_strategy` | string | create | |
| `bid_micro` | number | create | Micro-currency |
| `daily_budget_micro` | number | no | |
| `targeting` | object | create | Official targeting object |
| `placement_v2` | object | no | e.g. `{ "config": "AUTOMATIC" }` |
| `adsquad` | object | no | Full squad JSON if the operator supplies it |

### `ad.create` / `ad.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_squad_id` | string | yes | |
| `name` | string | create | |
| `creative_id` | string | create | |
| `type` | string | create | e.g. `SNAP_AD` |
| `status` | string | create | |

### `creative.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | yes | |
| `creative` | object | yes | Official creative payload (brand name, headline, media) |
| `media_file` | file | no | If the live creatives API requires a prior media upload, upload via official media endpoint first using this ref-id |

### `stats.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `entity` | string | yes | `adaccounts` \| `campaigns` \| `adsquads` \| `ads` |
| `id` | string | yes | Entity UUID |
| `granularity` | string | yes | `TOTAL` \| `DAY` \| `HOUR` \| `LIFETIME` |
| `start_time` | string | often | ISO 8601; required except some LIFETIME |
| `end_time` | string | often | |
| `fields` | string | no | Comma-separated metrics |
| `breakdown` | string | no | Official breakdown |
| `async` | boolean | no | If true, poll `stats_report` instead of inline stats |

### `conversion.upload`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `pixel_or_app_id` | string | yes | Path `{ID}` |
| `events` | array | yes | CAPI v3 `data` array. Each event: `event_name`, `event_time`, `action_source`, `event_source_url` (web), `user_data` |
| `test_mode` | boolean | no | If true, POST `…/events/validate` instead of production |

Hash emails/phones as official SHA-256 hex in `user_data.em` / `ph` **before** send if the operator passed plaintext — prefer requiring pre-hashed values to avoid logging PII.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | `request_status=SUCCESS` | `ok` |
| `request_id` | string | 2xx | Snap `request_id` |
| `organizations` / `campaigns` / `adsquads` / `ads` / `creatives` | array | list/create | Nested official entities |
| `paging` | object | list | `next_link` |
| `stats` | object/array | stats | Timeseries / totals |
| `conversion_response` | object | CAPI | v3 response body |
| `error` | string | failure | `request_status` + sub errors |
| `http_status` | number | HTTP error | 401 token expired |
| `retry_after_seconds` | number | 429 | |

## Legal

- Snap Developer Terms + Snap Business Tools Terms (accepted when creating the OAuth app in Business Manager).
- Auth: https://developers.snap.com/marketing-api/Ads-API/authentication
- Ads policy / HCE (housing, credit, employment) restricted_delivery_signals — operator responsibility.
- Conversion payloads are **PII** (hashed identifiers still personal data). Customer owns the ad account. Never log access tokens, refresh tokens, `user_data`, or event arrays.
- Marketing API rate limit commonly **10 req/s** (CAPI OAuth). Long-lived CAPI token: Snap recommends pacing (~1000 QPS); still cap this node at **10 rps**.
- CAPI `event_time` cannot be more than **7 days** in the past.

## Implementation notes

- JDK HTTP. Ads: `Authorization: Bearer {access_token}`, `Content-Type: application/json`, base `https://adsapi.snapchat.com`.
- Token refresh: `POST https://accounts.snapchat.com/login/oauth2/access_token` with `grant_type=refresh_token`, `client_id`, `client_secret`, `refresh_token`. On Ads 401 `invalid_token`, refresh once and retry. Never log token JSON.
- CAPI: prefer `capi_token` as `access_token` query param on `tr.snapchat.com` (official). Do not put the token in logs or OUTPUT. If only marketing refresh token exists, it works for CAPI **only** if the grant included `snapchat-offline-conversions-api`.
- Timeout **30 s**. Stats async: poll with total budget **120 s**.
- Response size cap **5_000_000** bytes.
- Retry 429/5xx twice. Do not retry 400 validation.
- `creative.create` media: if implement-day docs require a separate `/media` upload, use `file` type and the official multipart/JSON media route — re-fetch https://developers.snap.com/marketing-api/Ads-API (Creatives) on build day.
