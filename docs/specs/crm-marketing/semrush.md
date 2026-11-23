# SEMrush

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/semrush/`
- **Researched:** 2026-08-20
- **Official API:** Semrush Analytics / Standard API **v3** (SEO reports on `https://api.semrush.com/`) + Backlinks **v4** (`/apis/v4/backlinks/v1/overview`) + Trends **v3** Traffic Summary. Docs last updated **2026-08-05** (overview) / **2026-07-15** (overview reports).
- **Docs:** https://developer.semrush.com/api/v4/introduction/semrush-api-overview/ · https://developer.semrush.com/api/v3/seo/overview-reports/ · https://developer.semrush.com/api/v3/analytics/domain-reports/ · https://developer.semrush.com/api/v4/seo/backlinks/ · https://developer.semrush.com/api/v3/trends/api-reference/ · https://developer.semrush.com/api/projects/position-tracking/ · https://developer.semrush.com/api/v4/introduction/api-usage-restrictions/
- **Auth:** API key query param `key` — secret binding: `api_key`

## Scope

One node for Semrush **Analytics** reports the catalog lists. Calls consume **API units** (Standard) or Trends units. Do not scrape semrush.com HTML. Do not republish raw datasets to third parties (operator’s contract + Content Policy). Projects Position Tracking (`project.keyword.add`, `project.positionTracking.enable`) are Standard **Projects API** — include only those two project verbs from the catalog.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `domain.overview` | GET | `https://api.semrush.com/?type=domain_rank` | One-database Domain Overview (10 units/line live; 50 historical) |
| `domain.organic` | GET | `https://api.semrush.com/?type=domain_organic` | Organic keywords for a domain |
| `domain.adwords` | GET | `https://api.semrush.com/?type=domain_adwords` | Paid keywords |
| `phrase.this` | GET | `https://api.semrush.com/?type=phrase_this` | Keyword overview for one phrase |
| `backlinks.overview` | GET | `https://api.semrush.com/apis/v4/backlinks/v1/overview` | v4 backlink aggregates (v3 `/analytics/v1/` is deprecated) |
| `traffic.summary` | GET | `https://api.semrush.com/analytics/ta/api/v3/summary` | Trends Traffic Summary (1 unit/line) |
| `project.keyword.add` | PUT | `https://api.semrush.com/management/v1/projects/{campaignID}/keywords?key=` | Add keywords to an existing Position Tracking campaign |
| `project.positionTracking.enable` | POST | `https://api.semrush.com/management/v1/projects/{projectID}/tracking/enable?key=` | Enable Position Tracking on a project |

`domain.overview` with all databases uses `type=domain_ranks` when input `database` is omitted (official). Prefer `domain_rank` + required `database` to control unit burn.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `domain.overview` | no | |
| `api_key` | password | yes | empty | yes | Query `key=`. Never on INPUT |
| `default_database` | string | no | `us` | no | Regional DB code (e.g. `us`, `uk`) |
| `timeout_seconds` | number | no | `60` | no | Reports can be large |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | |
| `output` | OUTPUT | `{ status, rows, … }` or `{ error }` | Parse CSV/JSON; do not log full row PII (rare) |

FUNCTION ports: none.

## Input fields (by operation)

### Shared Analytics GET (`domain.*`, `phrase.this`)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `domain` | string | domain.* | Hostname without scheme |
| `phrase` | string | phrase.this | Keyword |
| `database` | string | overview one-DB / organic / adwords / phrase | Regional DB |
| `display_limit` | number | no | Official query `display_limit` |
| `display_offset` | number | no | Pagination |
| `display_date` | string | no | Historical `YYYYMM15` (costs **5×** units on many reports) |
| `export_columns` | string | no | Comma-separated column codes |
| `display_sort` | string | no | Official sort token |
| `display_filter` | string | no | Official filter expression |

### `backlinks.overview`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `target` | string | yes | Domain or URL (max 2000 chars) |
| `target_type` | string | yes | `ROOT_DOMAIN` \| `SUBDOMAIN` \| `SUBFOLDER` \| `PAGE` |
| `export_columns` | string | no | Subset of v4 fields |

### `traffic.summary`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `targets` | string | yes | Comma-separated domains (max 200) |
| `display_date` | string | no | `YYYY-MM-01` |
| `country` | string | no | Country filter |
| `export_columns` | string | no | Default visits/users columns |

### `project.keyword.add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `campaign_id` | string | yes | `{projectId}_{campaignId}` from the Position Tracking URL (official) |
| `keywords` | array | yes | Official body `{ "keywords": [ { "keyword": "…", "tags": ["…"] } ] }` |

### `project.positionTracking.enable`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `project_id` | string | yes | Path `{projectID}` |
| `tracking_url_type` | string | yes | `rootdomain` \| `subdomain` \| `subfolder` \| `url` |
| `tracking_url` | string | yes | Tracked URL |
| `location_id` | number | yes | From Universal location search |
| `weekly_notification` | boolean | no | Default true |
| `timezone` | number | no | Official integer timezone |
| `device` | string | no | `desktop` \| `phone` \| `tablet` |
| `business_name` | string | no | |

JSON POST to enable (100 API units per request). Docs: https://developer.semrush.com/api/projects/position-tracking/ (fetched 2026-08-20).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `format` | string | 2xx | `csv` or `json` |
| `rows` | array | Analytics 2xx | Parsed objects (header → values). Cap **100_000** rows then `{ error }` |
| `raw_truncated` | boolean | cap hit | |
| `overview` | object | backlinks | v4 aggregate metrics |
| `error` | string | ERROR / 4xx / units | Include unit/auth errors from body |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 | |

Semrush often returns HTTP 200 with an `ERROR` line in CSV (`ERROR 50 :: NOTHING FOUND`, invalid key, insufficient units). Treat `ERROR` prefix as `{ error }`, not success.

## Legal

- Terms: https://www.semrush.com/company/legal/terms-of-service/
- Content Policy: https://www.semrush.com/company/legal/content-policy/ — Semrush content may not be copied, reproduced, **republished**, or distributed unless authorized.
- API restrictions (2026-08-05): **10 requests/second**, **10 simultaneous** requests per account; **do not cache API data longer than one month** without written consent (ToS §3.3).
- Standard API needs a Business/Advanced plan **plus purchased API units**. Trends Summary uses Trends units (not the same pool).
- Tools with **external end users** need Semrush sales approval (`sales@semrush.com`). This node is for the **operator’s** Semrush account only.
- Do not feed raw Semrush datasets into model training (ToS restriction on using Services content to train AI except official Semrush integrations).

## Implementation notes

- JDK HTTP GET/PUT/POST. Put `api_key` only in query `key` (or official header if v4 requires it — follow live backlinks doc). Never log the key (redact query string).
- Rate: serialize to ≤ **10 rps**. On 429 wait `Retry-After` or 1 s.
- `display_limit` default **10** in this node even if Semrush defaults to 10_000 — unit protection. Operator may raise up to cap.
- Timeout **60 s**. Response size cap **5_000_000** bytes; fail closed.
- Retry: 5xx twice; do not retry `ERROR` insufficient units.
- Empty key / empty domain → `{ error }`, no HTTP.
- Parse CSV with `;` delimiter as in official examples.
