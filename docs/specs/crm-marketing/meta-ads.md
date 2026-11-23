# Meta Ads

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/meta-ads/`
- **Researched:** 2026-08-20
- **Official API:** Meta Marketing API + Graph API **v26.0** (released 2026-07-29). Marketing overview pages may still say v25.0 — pin `api_version` in config. Unversioned Graph calls fail for Marketing API.
- **Docs:** https://developers.facebook.com/docs/marketing-api/ · changelog https://developers.facebook.com/docs/graph-api/changelog/versions/ · Lead retrieval https://developers.facebook.com/docs/marketing-api/guides/lead-ads/retrieving/ · Leadgen webhooks https://developers.facebook.com/docs/graph-api/webhooks/getting-started/webhooks-for-leadgen/
- **Auth:** Bearer `access_token` minted outside StudioQuack (long-lived Page token for leads; User/System User token for ads). Header `Authorization: Bearer {access_token}`.

## Scope

One node for Meta **paid ads** and **Lead Ads**. Designer picks `operation`. Handler calls `https://graph.facebook.com/{api_version}/…`. Folds the former Facebook Lead Ads trigger into `trigger.leadgen` / `trigger.listen` (`leadgen` event type). Graph generic **page posting, comments, and organic feed** belong in the Social catalog — do not add them here.

Do not scrape Ads Manager HTML. Do not call Marketing API without a version prefix.

## Operations (`operation` config values)

Base: `https://graph.facebook.com/{api_version}` (default `v26.0`). `act_id` is the ad account numeric id **without** `act_` unless noted.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `campaign.create` | POST | `/act_{act_id}/campaigns` | Create campaign (`name`, `objective`/`buying_type`, `status`, `special_ad_categories`) |
| `campaign.get` | GET | `/{campaign_id}` | Read one campaign |
| `campaign.list` | GET | `/act_{act_id}/campaigns` | Page campaigns (`fields`, `limit`, `after`) |
| `campaign.update` | POST | `/{campaign_id}` | Update name/status/budget fields documented on Campaign |
| `adset.create` | POST | `/act_{act_id}/adsets` | Create ad set (budget, targeting, `campaign_id`, `optimization_goal`) |
| `adset.get` | GET | `/{adset_id}` | Read one ad set |
| `adset.list` | GET | `/act_{act_id}/adsets` | Page ad sets |
| `ad.create` | POST | `/act_{act_id}/ads` | Create ad (`adset_id`, `creative`, `status`) |
| `ad.get` | GET | `/{ad_id}` | Read one ad |
| `insights.get` | GET | `/{object_id}/insights` | Insights on campaign, ad set, ad, or `act_{act_id}` |
| `audience.create` | POST | `/act_{act_id}/customaudiences` | Create empty custom audience (then users via a later task; hashed PII only) |
| `leadForm.list` | GET | `/{page_id}/leadgen_forms` | List Instant Forms on the Page |
| `lead.get` | GET | `/{lead_id}` | Retrieve one lead (`fields=created_time,id,ad_id,form_id,field_data`) |
| `customConversion.create` | POST | `/act_{act_id}/customconversions` | Create custom conversion |
| `trigger.leadgen` | POST | `/{page_id}/subscribed_apps` | Install app on Page with `subscribed_fields=leadgen` |
| `trigger.listen` | POST | `/{page_id}/subscribed_apps` | Same subscribe path; `event_type` config (default `leadgen`) |

Lead bulk read (optional helper on `lead.get` when `form_id` or `ad_id` is set without `lead_id`): GET `/{form_id}/leads` or `/{ad_id}/leads`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `campaign.list` | no | Chooses the row above |
| `access_token` | password | yes | empty | yes | User, System User, or Page token minted outside StudioQuack. Never on INPUT |
| `ad_account_id` | string | yes for ads ops | empty | no | Numeric ad account id (`act_` stripped if present) |
| `page_id` | string | lead + trigger | empty | no | Page that owns Instant Forms / leadgen |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1 s |

OAuth scopes (production, App Review): `ads_management`, `ads_read`, `business_management`; leads also `leads_retrieval`, `pages_show_list`, `pages_read_engagement`, `pages_manage_metadata`, `pages_manage_ads`. Advanced Access / Marketing API Access Tier as published.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No tokens |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo tokens or raw `field_data` into logs |

FUNCTION ports: none.

## Input fields (by operation)

### `campaign.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Campaign name |
| `objective` | string | no | e.g. `OUTCOME_LEADS`, `OUTCOME_SALES` (current outcome objectives) |
| `status` | string | no | `PAUSED` recommended on create |
| `special_ad_categories` | array | yes | Meta required; use `NONE` or published special-ad enums |
| `buying_type` | string | no | e.g. `AUCTION` |

### `campaign.get` | `adset.get` | `ad.get` | `lead.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes except bulk lead | Object id (`campaign_id` / `adset_id` / `ad_id` / `lead_id` aliases accepted) |
| `fields` | string | no | Comma Graph `fields` |
| `form_id` | string | bulk lead | GET `/{form_id}/leads` when `id` omitted |
| `ad_id` | string | bulk lead | GET `/{ad_id}/leads` when `id` omitted |

### `campaign.list` | `adset.list` | `leadForm.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `fields` | string | no | Graph fields |
| `limit` | number | no | Page size; cap 100 |
| `after` | string | no | Cursor |

### `campaign.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Campaign id |
| `name` | string | no | New name |
| `status` | string | no | `ACTIVE` / `PAUSED` / `DELETED` / `ARCHIVED` |

### `adset.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Ad set name |
| `campaign_id` | string | yes | Parent campaign |
| `daily_budget` | number | one of daily/lifetime | Account currency subunits as documented |
| `lifetime_budget` | number | one of daily/lifetime | |
| `billing_event` | string | yes | e.g. `IMPRESSIONS` |
| `optimization_goal` | string | yes | e.g. `LEAD_GENERATION` |
| `targeting` | object | yes | Targeting spec JSON |
| `start_time` | string | no | ISO-8601 |
| `status` | string | no | Default `PAUSED` |
| `promoted_object` | object | lead ads | Page / form promoted object |

### `ad.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Ad name |
| `adset_id` | string | yes | Parent ad set |
| `creative` | object | yes | `{ creative_id }` or inline creative as documented |
| `status` | string | no | Default `PAUSED` |

### `insights.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `object_id` | string | yes | Campaign, ad set, ad, or `act_{id}` |
| `fields` | string | no | Metrics e.g. `impressions,spend,clicks` |
| `time_range` | object | no | `{ since, until }` YYYY-MM-DD |
| `level` | string | no | `campaign` / `adset` / `ad` |
| `limit` | number | no | Cap 100 |

### `audience.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Audience name |
| `subtype` | string | no | e.g. `CUSTOM` |
| `description` | string | no | Internal description |
| `customer_file_source` | string | no | As Custom Audience docs |

Do **not** accept raw emails on this op. User upload is a later, hashed-only operation if added.

### `customConversion.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Conversion name |
| `event_source_id` | string | yes | Pixel / dataset id |
| `rule` | object | yes | Event rule JSON as documented |

### `trigger.leadgen` | `trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `callback_url` | string | yes | StudioQuack message-event ingress URL (HTTPS) |
| `event_type` | string | no | Default `leadgen`. `trigger.listen` uses this as `subscribed_fields` |
| `page_id` | string | no | Overrides config `page_id` |

Webhook **verify** is handled by the ingress (hub.mode / hub.verify_token / hub.challenge). Handler does not implement a public HTTP server.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `id` | string | create/get/update/subscribe | Graph object id |
| `campaign` / `adset` / `ad` / `audience` / `conversion` / `lead` | object | matching get/create | Provider JSON, size-capped. Leads: pass `field_data` on OUTPUT only; never log |
| `data` | array | list / insights / bulk leads | Page of objects |
| `paging` | object | list | `{ cursors.after, next }` |
| `subscription` | object | trigger.* | `{ page_id, subscribed_fields }` |
| `error` | string | failure | Actionable; tokens redacted |
| `http_status` | number | HTTP error | Graph status |
| `fbtrace_id` | string | Graph error body | Correlation; safe to log |
| `retry_after_seconds` | number | 429 / 5xx | Honor Graph `Retry-After` if present; else 60 / 30 |

## Legal

- Platform Terms: https://developers.facebook.com/terms (updated 2026-02-03)
- Lead Generation Terms: https://www.facebook.com/legal/leadgen/tos
- Commercial / ads: https://www.facebook.com/legal/commercial_terms · Advertising Standards
- Custom Audience / customer-file: hashed identifiers only; customer-file ToS in Ads Manager
- **Data use:** customer owns the ad account and Page. StudioQuack is a processor for the workflow operator. Do not sell lead data. Minimize PII. Do not persist leads longer than the operator’s stated purpose. Meta Platform Data Use restrictions apply to Platform Data (no independent use, no transfer except as Terms allow).
- App Review required for production `leads_retrieval` / ads Advanced Access. Development mode is limited to app roles.
- Rate limiting: Graph business-use + Marketing API Access Tier (Limited vs Full). HTTP 429 must not tight-loop.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Facebook SDK.
- Headers: `Authorization: Bearer {access_token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty token / missing `ad_account_id` for ads ops → `{ error }` no HTTP.
- Body cap **1_000_000** bytes. Insights / lead list: do not follow unbounded paging inside one dispatch; return `paging` for the next node run.
- v26 HEF (Housing / Employment / Financial) constrained targeting: set `targeting.targeting_automation.advantage_audience` to `0` or `1` explicitly or the ad-set call fails ([v26.0 blog](https://developers.facebook.com/blog/post/2026/07/29/introducing-graph-api-v26-and-marketing-api-v26/)).
- Retry: none inside the handler except a single retry on connect timeout. 429 → `{ error, http_status: 429, retry_after_seconds }`.
- Logs: operation, version, object id, status, byte sizes — never access tokens, `field_data`, emails, phones, or audience user lists.
