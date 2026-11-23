# Mixpanel

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/mixpanel/`
- **Researched:** 2026-08-20
- **Official API:** Mixpanel **Ingestion API** + **Query API** + **Raw Data Export** (no single version number; OpenAPI Ingestion `1.0.0`). TLS 1.2+ required (TLS 1.0/1.1 rejected from April 2026).
- **Docs:** https://developer.mixpanel.com/reference/overview · ingest auth https://developer.mixpanel.com/reference/ingestion-api-authentication · track https://developer.mixpanel.com/reference/track-event · import https://docs.mixpanel.com/reference/import-events · engage set https://developer.mixpanel.com/reference/profile-set · insights https://developer.mixpanel.com/reference/insights-query · EU residency https://docs.mixpanel.com/docs/privacy/eu-residency
- **Auth:** **split by operation** — ingest `track` / `engage.*` use **project token** in JSON; `import` prefers **service account** Basic auth + `project_id`; Query/Export require **service account** Basic auth + `project_id`. Secret bindings: `service_account_secret`. Ingest token belongs in event JSON on INPUT (`properties.token` / `$token`).

## Scope

One node for Mixpanel product analytics: live track, historical import, people profiles (`$set` / `$unset`), raw export, saved Insights, and funnel reports. **Ingest vs query use different hosts and credentials.** EU/IN residency is a **project** setting: wrong host silently drops ingest.

Do not scrape mixpanel.com UI. Do not use deprecated Project Secret for new bindings (service account instead). Funnel Query API is in **maintenance**; prefer Insights (`bookmark_id`) for new funnels.

## Operations (`operation` config values)

Hosts by `region` (`us` / `eu` / `in`). Query host/path from Mixpanel OpenAPI **2026-08-20**: `GET https://mixpanel.com/api/query/insights` (EU `eu.mixpanel.com`, IN `in.mixpanel.com`). Do not ship legacy `/api/2.0/insights` for new code.

| Surface | US | EU | IN |
|---|---|---|---|
| Ingest | `https://api.mixpanel.com` | `https://api-eu.mixpanel.com` | `https://api-in.mixpanel.com` |
| Query | `https://mixpanel.com/api/query` | `https://eu.mixpanel.com/api/query` | `https://in.mixpanel.com/api/query` |
| Export | `https://data.mixpanel.com` | `https://data-eu.mixpanel.com` | `https://data-in.mixpanel.com` |

| operation | HTTP | Path / RPC | Auth | When to use |
|---|---|---|---|---|
| `track` | POST | `{ingest}/track?verbose=1` | project token in each event `properties.token` | Untrusted / small live events. Last **5 days** only. Max 2000 events |
| `import` | POST | `{ingest}/import?strict=1&project_id=` | service account **or** token as Basic user | Server-side / historical (after 1971-01-01). `$insert_id` required |
| `engage.set` | POST | `{ingest}/engage#profile-set` (POST `/engage`) | project token `$token` | People `$set` |
| `engage.unset` | POST | `{ingest}/engage` | project token | People `$unset` |
| `export` | GET | `{export}/api/2.0/export?project_id=` | service account | Raw events `from_date` / `to_date` |
| `insights.query` | GET | `{query}/insights?project_id=&bookmark_id=` | service account | Saved Insights / Funnel-in-Insights report |
| `funnel.query` | GET | `{query}/funnels?project_id=&funnel_id=&from_date=&to_date=` | service account | **Maintenance** ([funnels-query](https://developer.mixpanel.com/reference/funnels-query)). Prefer `insights.query` |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `track` | no | Chooses the row above |
| `region` | select | yes | `us` | no | `us` \| `eu` \| `in` — documented only; OfficialApiNode hosts stay US |
| `project_id` | string | import + query + export | empty | no | Numeric project id |
| `service_account_user` | string | query/export/import | empty | no | Service account username |
| `service_account_secret` | password | query/export/import | empty | yes | Service account secret (shown once at creation) |
| `timeout_seconds` | number | no | `30` | no | Export may use 60 s |

EU projects created on/after 2025-08-18 **must** use EU hosts. US→EU forwarding for older EU projects ended **July 2026**.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never log token/secret or event property values |

FUNCTION ports: none.

## Input fields (by operation)

### `track`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `events` | array | yes | `{ event, properties }`. Handler injects `token`. Max 2000. Each event ≤ 1MB, &lt; 255 properties |
| `distinct_id` | string | if single-event shorthand | Used when `event` is a string instead of `events` |
| `event` | string | shorthand | Single event name |
| `properties` | object | shorthand | Merged into the event; do not put `token` on INPUT |

### `import`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `events` | array | yes | Same shape; each `properties` **must** include `time`, `distinct_id`, `$insert_id` (≤ 36 bytes) |

### `engage.set`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | Profile id |
| `properties` | object | yes | `$set` map. Server-side: Mixpanel `ip=0` to avoid geo overwrite |

### `engage.unset`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `distinct_id` | string | yes | Profile id |
| `properties` | array | yes | Property names to `$unset` |

### `export`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from_date` | string | yes | `YYYY-MM-DD` |
| `to_date` | string | yes | `YYYY-MM-DD` |
| `event` | array | no | Event name filter |
| `where` | string | no | Filter expression (cap 2000 chars) |

Cap download at **1_000_000** bytes; if truncated, `{ error }` with `truncated: true` rather than loading a full project dump.

### `insights.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `bookmark_id` | number | yes | From report URL `report-{id}` |
| `workspace_id` | number | no | If the project uses workspaces |

### `funnel.query`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `funnel_id` | number | yes | Saved funnel id |
| `from_date` | string | yes | `YYYY-MM-DD` inclusive (OpenAPI required) |
| `to_date` | string | yes | `YYYY-MM-DD` inclusive |
| `length` / `length_unit` | number/string | no | Conversion window; max 90 days |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `mixpanel_status` | number | track/engage | Provider `1` / `0` (200 can still be `0`) |
| `num_records_imported` | number | import strict | Count ingested |
| `failed_records` | array | import 400 partial | `$insert_id` + error; no full event dump in logs |
| `report` | object | insights / funnel | Series / headers. Cap body |
| `events_ndjson` | string | export | Truncated NDJSON; prefer not to log |
| `error` | string | failure | No secrets |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 502 / 503 | Start 2 s backoff guidance; handler returns 60 |

Query API published limit: **60 queries/hour**, **5 concurrent**. Ingest: ~2GB uncompressed JSON/minute rolling.

## Legal

- Mixpanel Terms + DPA: operator is the controller of end-user analytics data.
- EU Data Residency: https://mixpanel.com/legal/eu-data-residency — pick `region=eu` and EU hosts.
- GDPR/CCPA deletion: Mixpanel GDPR API exists; out of this node’s catalog ops (do not add scrape).
- Never log `distinct_id` together with emails/IPs from properties. Log event **count** and **names** only.

## Implementation notes

- JDK `HttpClient` only. TLS 1.2+. `Redirect.NEVER`.
- Query/Export/Import (service account): `Authorization: Basic` base64(`user:secret`).
- Query paths: `{query}/insights` and `{query}/funnels` (not `/api/2.0/…`).
- Import with project token: Basic `token:` (empty password) if no service account.
- `track` / `engage`: POST JSON array; `verbose=1` so a `200` + `status:0` becomes `{ error }`.
- Engage: query `ip=0` on server-side.
- Empty required credential for the op → `{ error }` no HTTP.
- Do not gzip engage (provider: gzip unsupported on profile-set).
- Logs: operation, region, HTTP status, event count — never tokens, secrets, or property maps.
