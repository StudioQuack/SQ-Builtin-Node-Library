# X Ads

> **Writes may HTTP 401.** Official X Ads API authentication is **OAuth 1.0a request signing** on every call (https://docs.x.com/x-ads-api/fundamentals/authentication / making-authenticated-requests). OfficialApiNode cannot sign OAuth 1.0a. This node sends **Bearer** `access_token` only. Campaign/line-item/promoted-tweet/conversion **writes** often 401 without OAuth 1.0a. Some reads may succeed with an OAuth 2 bearer where X allows app-only access.


Official [X Ads API v12](https://docs.x.com/x-ads-api/introduction). Handler: `OfficialApiNode` via `api_spec.json`. **Ads API only** — not the existing X/Twitter API v2 search node (`api.x.com/2`).

- **Docs:** https://docs.x.com/x-ads-api/introduction · campaign management · measurement / web conversions
- **Base:** `https://ads-api.x.com/12`
- **Auth:** Bearer `access_token` (OAuth 2 user-context from an Ads API-approved app). Customer owns the X Ads account.

Access is restricted: apply in the X developer portal. Legacy host `ads-api.twitter.com` may still resolve; this node uses `ads-api.x.com`.

`OfficialApiNode` cannot sign OAuth 1.0a. Bearer is kept as a fallback. OAuth 1.0a consumer/access secrets are not collected. **Writes may 401.**

## Operations

| operation | HTTP | Path |
|---|---|---|
| `account.get` | GET | `/accounts/{account_id}` |
| `campaign.create` | POST | `/accounts/{account_id}/campaigns` |
| `campaign.list` | GET | `/accounts/{account_id}/campaigns` |
| `lineItem.create` | POST | `/accounts/{account_id}/line_items` |
| `lineItem.list` | GET | `/accounts/{account_id}/line_items` |
| `promotedTweet.create` | POST | `/accounts/{account_id}/promoted_tweets` |
| `stats.get` | GET | `/stats/accounts/{account_id}` |
| `targeting.criteria` | GET | `/accounts/{account_id}/targeting_criteria` |
| `conversion.upload` | POST | `/measurement/conversions/{pixel_id}` |

No `trigger.listen` in the catalog row. Conversion events: hash emails/phones per Web Conversions rules; max 500 events per request.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer OAuth 2 token |
| `account_id` | string | no | Default ads account id |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer owns the ads account. Developer policy and Ads API terms apply.
- Conversion identifiers are PII. Minimize. Hash emails/phones. Never log tokens, hashed identifiers, or event payloads.
- Official Ads API only. Do not call organic tweet search from this node.
