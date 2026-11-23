# TikTok Ads

Official [TikTok Marketing API](https://business-api.tiktok.com/portal/docs) v1.3. Handler: `OfficialApiNode` via `api_spec.json`. Ads Manager campaigns, ad groups, ads, reporting, Events API 2.0, Instant Form lead tasks. Not Organic / Business Messaging.

- **Docs:** https://business-api.tiktok.com/portal/docs · Rate limits: https://business-api.tiktok.com/portal/docs/rate-limits/v1.3
- **Base:** `https://business-api.tiktok.com` · path `/open_api/v1.3/`
- **Auth:** header `Access-Token` (`access_token` secret). Customer owns the TikTok for Business / Business Center account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `advertiser.list` | GET | `/open_api/v1.3/oauth2/advertiser/get/` |
| `campaign.create` | POST | `/open_api/v1.3/campaign/create/` |
| `campaign.get` | GET | `/open_api/v1.3/campaign/get/` |
| `campaign.list` | GET | `/open_api/v1.3/campaign/get/` |
| `adgroup.create` | POST | `/open_api/v1.3/adgroup/create/` |
| `adgroup.list` | GET | `/open_api/v1.3/adgroup/get/` |
| `ad.create` | POST | `/open_api/v1.3/ad/create/` |
| `ad.list` | GET | `/open_api/v1.3/ad/get/` |
| `report.get` | GET | `/open_api/v1.3/report/integrated/get/` |
| `pixel.track` | POST | `/open_api/v1.3/event/track/` | `event`/`event_time`/`event_id`/`user`/`properties`/`page` → `data[0].*` |
| `lead.list` | POST | `/open_api/v1.3/page/lead/task/` |
| `trigger.listen` | ingress | BPMN message event |

`lead.list` creates the download task (one HTTP call). Poll/download is a later dispatch with the returned `task_id`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Header `Access-Token` |
| `advertiser_id` | string | no | Default advertiser (v1.3 string ids) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. `trigger.listen` does not open a socket.

## Legal

- Customer owns the ad account and consent for Instant Form leads and Events `user` objects.
- Minimize: do not send health, government id, or precise location beyond Business Products (Data) Terms. Hash emails/phones for `pixel.track`.
- Never log tokens, emails, phones, hashed identifiers, or form answers.
- Official API only: `business-api.tiktok.com/open_api/v1.3/`. Do not scrape Ads Manager.
- v1.3 confirmed current 2026-08-20. Retired: `/v1.2/` paths.
