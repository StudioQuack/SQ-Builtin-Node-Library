# Snapchat

Official Snap Marketing **Ads API v1** (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_SNAPCHAT`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

- **Docs:** https://developers.snap.com/marketing-api/home · Ads https://developers.snap.com/marketing-api/Ads-API/introduction · Measurement https://developers.snap.com/marketing-api/Ads-API/measurement
- **Base:** `https://adsapi.snapchat.com/v1`
- **Auth:** StudioQuack secret `access_token` → `Authorization: Bearer`. Scope `snapchat-marketing-api`. Never on INPUT. Never logged.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `creative_create` | POST | `/adaccounts/{ad_account_id}/creatives` body `name`, `type`, `headline` |
| `ad_get` | GET | `/ads/{ad_id}` |
| `stats_get` | GET | `/{entity_type}/{entity_id}/stats` query `granularity`, `start_time`, `end_time`, `fields` |

`entity_type`: `ads` \| `adsquads` \| `campaigns` \| `adaccounts`. Ads media upload is not chained; this node does not take INPUT `media`. OfficialApiNode sends a flat JSON body (`name`, `type`, `headline`); if the live Ads API requires a `creatives` array wrapper, a later revision must nest it.

## Public Profile API (not in this node)

The Public Profile API is **allowlist-only** on `businessapi.snapchat.com` (scope `snapchat-profile-api`; Snap must allowlist the OAuth client). `OfficialApiNode` cannot switch host. **`public_profile_get` is omitted** — do not invent a path on the Ads host. Until multi-host support exists, call Public Profile out of band.

No personal graph, friends list, private stories, or chat. Story Kit was removed.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`ad_get`) |
| `access_token` | password | yes | Bearer |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Operator needs a Business Manager org and accepted Snap developer / ads terms.
- Ads traffic is advertising data (accounts, creatives, spend). Minimize PII in logs.
- Rapid create of ads with the same creative may 429. Honor `Retry-After`; do not tight-loop.
