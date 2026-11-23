# Snapchat

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/snapchat/`
- **Researched:** 2026-08-20
- **Official API:** Snap Marketing API — Ads API `v1` (`https://adsapi.snapchat.com/v1`) plus Public Profile API (`https://businessapi.snapchat.com`)
- **Docs:** https://developers.snap.com/marketing-api/home — Ads Quick Start https://developers.snap.com/marketing-api/Ads-API/quick-start — Public Profile Get Started https://developers.snap.com/marketing-api/Public-Profile-API/GetStarted
- **Auth:** oauth2 — secret binding: `access_token` (Bearer). Refresh: `client_id` + `client_secret` + `refresh_token` against `https://accounts.snapchat.com/login/oauth2/access_token`

## Scope

Ads and **allowlisted public profiles only**. This node calls Snap’s documented Marketing surfaces: creatives, ads, and stats on `adsapi.snapchat.com/v1` (OAuth scope `snapchat-marketing-api`), and read-only public profile GET on `businessapi.snapchat.com` (scope `snapchat-profile-api`; the OAuth **client ID must be allowlisted** by Snap). There is **no general personal-Snap graph**: no friends list, no private stories, no personal chat. Snap Kit Login Kit **SDKs are deprecated** in favor of raw OAuth2 identity login (display name / Bitmoji only — not this node). **Story Kit was removed** (Snap Kit changelog 2.0.0, 2022-03-30). Do not scrape stories, Spotlight, or the consumer app. Catalog ops `creative_create`, `ad_get`, `stats_get`, `public_profile_get` still map.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `creative_create` | POST (+ upload) | `/v1/adaccounts/{ad_account_id}/media` then `POST /v1/media/{media_id}/upload` then `POST /v1/adaccounts/{ad_account_id}/creatives` | Create a SNAP_AD creative from a `file` (or existing `media_id`) |
| `ad_get` | GET | `/v1/ads/{ad_id}` | Fetch one ad |
| `stats_get` | GET | `/v1/{entity_plural}/{entity_id}/stats` | Measurement for ad / adsquad / campaign / adaccount |
| `public_profile_get` | GET | `/public/v1/public_profiles/{profile_id}` | Allowlisted Public Profile API; host `businessapi.snapchat.com` |

Retired / unofficial verbs are omitted (no story scrape, no Snap Kit Story Kit).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `ad_get` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | Bearer for Ads and/or Public Profile. Never on INPUT, never logged. |
| `client_id` | string | no | empty | no | OAuth app id (Business Manager OAuth App — not the consumer Developer Portal app for Public Profile) |
| `client_secret` | password | no | empty | yes | Token refresh only |
| `refresh_token` | password | no | empty | yes | Refresh grant when access token expires (~60 min) |
| `ad_account_id` | string | no | empty | no | Default ad account UUID for `creative_create` |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s. Upload ops may use 60 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. No INPUT `media`. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = creative_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_account_id` | string | no | Else config `ad_account_id` |
| `name` | string | yes | Creative name |
| `type` | string | no | Default `SNAP_AD` |
| `headline` | string | no | Display headline |
| `profile_id` | string | no | Public profile id (`profile_properties.profile_id`) |
| `media_id` | string | no | Existing READY media; skip upload if set |
| `media_type` | string | no | `VIDEO` or `IMAGE` for the media object |

Empty account → `{ error }` no HTTP. This node does not take INPUT `media`.

### `operation = ad_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ad_id` | string | yes | Ad UUID |

### `operation = stats_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `entity_type` | string | yes | `ads` \| `adsquads` \| `campaigns` \| `adaccounts` |
| `entity_id` | string | yes | Entity UUID |
| `granularity` | string | yes | `TOTAL` \| `DAY` \| `HOUR` \| `LIFETIME` as documented |
| `start_time` | string | no | ISO-8601; required for DAY/HOUR per Measurement docs |
| `end_time` | string | no | ISO-8601 |
| `fields` | string | no | Comma-separated metrics; default impressions,spend |

### `operation = public_profile_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `profile_id` | string | yes | Public profile UUID |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx / SUCCESS | `ok` |
| `request_status` | string | Ads/Profile JSON | Provider `request_status` |
| `creative_id` | string | `creative_create` | New creative id |
| `media_id` | string | `creative_create` | Media used |
| `ad` | object | `ad_get` | Ad resource (id, name, status, type, review_status) |
| `stats` | object | `stats_get` | Measurement payload (cap body) |
| `public_profile` | object | `public_profile_get` | id, display_name, snap_user_name, subscriber_count, logos |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 60 on 429, 30 on 5xx |

## Legal

- Marketing API home: https://developers.snap.com/marketing-api/home
- Ads API: https://developers.snap.com/marketing-api/Ads-API/introduction
- Measurement: https://developers.snap.com/marketing-api/Ads-API/measurement
- Creatives: https://developers.snap.com/marketing-api/Ads-API/creatives
- Ads: https://developers.snap.com/marketing-api/Ads-API/ads
- Public Profile: https://developers.snap.com/marketing-api/Public-Profile-API/GetStarted
- Snap Kit changelog (Story Kit removed): https://developers.snap.com/snap-kit/changelog
- Login Kit (identity OAuth only; not a content graph): https://developers.snap.com/snap-kit/login-kit/overview
- Operator must have a Business Manager org, OAuth app, and accept Snap developer / ads terms. Public Profile is **allowlist-only** (email client ID to Snap contact; never send client secret).
- Ads traffic is advertising data (accounts, creatives, spend). Public Profile returns public creator metadata. Minimize PII in logs.
- Rate limits: creating ads with the **same creative** in rapid succession returns **429** (Ads docs). Numeric global Ads quotas were not published on the pages fetched 2026-08-20 — treat 429 as hard backoff. Do not tight-loop.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Snap SDK.
- Ads host `https://adsapi.snapchat.com`. Profile host `https://businessapi.snapchat.com`. Header `Authorization: Bearer {access_token}`, `Accept: application/json`.
- `creative_create` upload: `Content-Type: multipart/form-data` field `file`. Cap **32_000_000** bytes for the simple upload path (chunked up to 1 GB exists; do not implement chunked in v1 unless product asks).
- Empty `access_token` → `{ error }` no HTTP. 403 on Public Profile: mention allowlist / authorized vs public endpoint in the error text, still no secrets.
- Response body cap **1_000_000** bytes. Default timeout 30 s (60 s for upload).
- HTTP 4xx/5xx or Ads `request_status` failure → `{ error }` on OUTPUT, never throw past dispatch. Truncate body 240 chars; redact Bearer.
- Do not auto-retry inside the handler.
- Logs: operation, hosts, status, ids, sizes — never tokens or file bytes.
