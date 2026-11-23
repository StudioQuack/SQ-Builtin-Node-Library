# Pinterest Ads

Official **Pinterest API v5** (only supported version; v3 retired) via `OfficialApiNode` / `api_spec.json`. Ads live under the same `/v5` host. Organic Pins belong in Social — not this node.

- **Docs:** Ads use-case https://developers.pinterest.com/usecase/ads/ · auth https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/ · campaigns https://developers.pinterest.com/docs/api/v5/campaigns-list · conversions https://developers.pinterest.com/docs/track-conversions/track-conversions-in-the-api/ · rate limits https://developers.pinterest.com/docs/reference/rate-limits/
- **Base:** `https://api.pinterest.com/v5`
- **OAuth token URL:** `POST https://api.pinterest.com/v5/oauth/token` (refresh outside this node)
- **Auth:** Bearer `access_token` (or conversion access token for `conversion.upload`). Customer owns the Pinterest ad account.

Ads write/analytics require **Standard** access (operator completes app review).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `adAccount.list` | GET | `/ad_accounts` |
| `campaign.create` | POST | `/ad_accounts/{ad_account_id}/campaigns` |
| `campaign.list` | GET | `/ad_accounts/{ad_account_id}/campaigns` |
| `adGroup.create` | POST | `/ad_accounts/{ad_account_id}/ad_groups` |
| `adGroup.list` | GET | `/ad_accounts/{ad_account_id}/ad_groups` |
| `ad.create` | POST | `/ad_accounts/{ad_account_id}/ads` |
| `ad.list` | GET | `/ad_accounts/{ad_account_id}/ads` |
| `analytics.get` | GET | `/ad_accounts/{ad_account_id}/campaigns/analytics` |
| `audience.create` | POST | `/ad_accounts/{ad_account_id}/customer_lists` |
| `conversion.upload` | POST | `/ad_accounts/{ad_account_id}/events` |

`analytics.get` with `level=ad_group` / `ad` would need `/ad_groups/analytics` or `/ads/analytics`. OfficialApiNode path is campaign analytics only.

`columns` is a required list for analytics; OfficialApiNode `queryFrom` emits scalar query params — if the API needs repeated `columns=`, send a documented workaround or expect a 400 until array query support exists.

`records` for `audience.create` must already be **hashed** (SHA-256 as currently required). Never accept raw emails.

`conversion.upload` takes purpose fields `event_name`, `action_source` (`web`\|`app_android`\|`app_ios`\|`offline`), `event_time` (unix seconds), `event_id`, `user_data`, `custom_data`, `event_source_url` nested into `data[0]`. `data` remains a one-event-object escape hatch. Docs: https://developers.pinterest.com/docs/api/v5/events-create/

`campaign.create` / `adGroup.create` / `ad.create` take the official arrays (`campaigns`, `ad_groups`, `ads`) of provider objects (name, objective_type, campaign_id, pin_id, …). List ops accept `entity_statuses`, `campaign_ids`, `ad_group_ids`, `ad_ids`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `adAccount.list` |
| `access_token` | password | yes | Bearer minted outside StudioQuack |
| `ad_account_id` | string | no | Path fallback |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Pagination: one page per node run; return `bookmark` in `data`. Never log Bearer, emails, or MAIDs.

## Legal

- Pinterest Developer Guidelines + Advertising Guidelines. Operator completes ads access review.
- Conversion `user_data` is PII — hash as documented; never log.
- Do not store conversion payloads.
- Rate limits (Standard, published): ads_write ~400/min; ads_analytics ~300/min; ads_read ~1000/min. Honor 429.
