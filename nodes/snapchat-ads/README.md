# Snapchat Ads

Official Snap **Marketing API** (Ads API **v1**). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Business Manager org and OAuth app. Scope `snapchat-marketing-api`. Access tokens last ~60 minutes — refresh outside this node; supply `access_token` as Bearer.

- **Docs:** https://developers.snap.com/marketing-api/home · Ads https://developers.snap.com/marketing-api/Ads-API/introduction · Organizations https://developers.snap.com/marketing-api/Ads-API/organizations · Measurement https://developers.snap.com/marketing-api/Ads-API/measurement · Conversions API v3 https://developers.snap.com/marketing-api/Conversions-API/UsingTheAPI
- **API (2026-08-22):** Ads API `https://adsapi.snapchat.com/v1`. `conversion.upload` uses per-op `baseUrl` `https://tr.snapchat.com` and path `/v3/{pixel_id}/events` with query `access_token` (https://developers.snap.com/marketing-api/Conversions-API/UsingTheAPI). CAPI v2 (`/v2/conversion`) is deprecated.
- **Base:** `https://adsapi.snapchat.com/v1`
- **Auth:** Bearer `access_token`. Never on INPUT.

`stats.get` path `/{entity_type}/{entity_id}/stats` — `entity_type` is `ads` \| `adsquads` \| `campaigns` \| `adaccounts`.

Do not scrape the consumer app. This node is ads management, not a personal graph.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `organization.list` | GET | `/me/organizations` |
| `campaign.create` | POST | `/adaccounts/{ad_account_id}/campaigns` | `name`/`status`/`objective` → `campaigns[0].*` |
| `campaign.list` | GET | `/adaccounts/{ad_account_id}/campaigns` |
| `adsquad.create` | POST | `/adaccounts/{ad_account_id}/adsquads` | `name`/`campaign_id`/`type`/`bid_micro` → `adsquads[0].*` |
| `adsquad.list` | GET | `/adaccounts/{ad_account_id}/adsquads` |
| `ad.create` | POST | `/adaccounts/{ad_account_id}/ads` | `name`/`ad_squad_id`/`creative_id` → `ads[0].*` |
| `ad.list` | GET | `/adaccounts/{ad_account_id}/ads` |
| `creative.create` | POST | `/adaccounts/{ad_account_id}/creatives` | `name`/`type`/`brand_name`/`headline` → `creatives[0].*` |
| `stats.get` | GET | `/{entity_type}/{entity_id}/stats` |
| `conversion.upload` | POST | `https://tr.snapchat.com/v3/{pixel_id}/events` | Body: data[] (event_name, event_time, action_source, user_data) + test_event_code |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth access token (~60 m) |
| `ad_account_id` | string | no | Default ad account UUID (path when input omits it) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Operator accepts Snap developer / ads terms. Customer owns the ad account.
- Ads traffic is advertising data (accounts, creatives, spend). Minimize PII in conversion `user_data`.
- Creating ads with the same creative in rapid succession may 429. Honor backoff.
- Official Marketing API only.
