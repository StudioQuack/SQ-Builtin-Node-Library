# Mixpanel

Official Mixpanel **Ingestion** + **Query** + **Raw Data Export** via `OfficialApiNode` / `api_spec.json`. No single version number (Ingestion OpenAPI `1.0.0`). TLS 1.2+.

- **Docs:** https://developer.mixpanel.com/reference/overview · ingest auth https://developer.mixpanel.com/reference/ingestion-api-authentication · track https://developer.mixpanel.com/reference/track-event · import https://docs.mixpanel.com/reference/import-events · insights https://developer.mixpanel.com/reference/insights-query · EU residency https://docs.mixpanel.com/docs/privacy/eu-residency
- **US hosts (this spec):** ingest `https://api.mixpanel.com` · query `https://mixpanel.com/api` · export `https://data.mixpanel.com`
- **Auth (split):** ingest `track` / `engage.*` use **project token in JSON**; `import` / Query / Export use **service account HTTP Basic** + `project_id`. This node’s `api_spec.json` auth is **basic** (`service_account_user` / `service_account_secret`) so query/export/import can run. Customer owns the Mixpanel project.

EU/IN residency is a **project** setting: wrong host silently drops ingest. EU projects created on/after 2025-08-18 must use EU hosts. US→EU forwarding for older EU projects ended July 2026.

## Operations

| operation | HTTP | URL | Auth expected by provider |
|---|---|---|---|
| `track` | POST | `{ingest}/track?verbose=1` | project token in event JSON |
| `import` | POST | `{ingest}/import?strict=1&project_id=` | service account Basic |
| `engage.set` / `engage.unset` | POST | `{ingest}/engage?verbose=1&ip=0` | project token `$token` |
| `export` | GET | per-op `https://data.mixpanel.com/api/2.0/export` | service account Basic |
| `insights.query` | GET | per-op `https://mixpanel.com/api/2.0/insights` | service account Basic |
| `funnel.query` | GET | per-op `https://mixpanel.com/api/2.0/funnels` | service account Basic (**maintenance**; prefer Insights) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `track` |
| `service_account_user` | string | no | Basic user |
| `service_account_secret` | password | yes | Basic password |
| `project_id` | string | no | Query param fallback |
| `region` | select | no | `us` \| `eu` \| `in` — **not interpolated into hosts** |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Failures: `{ error }` — never log token/secret or event property values.

## Legal

- Operator is the controller of end-user analytics data. Mixpanel Terms + DPA.
- EU Data Residency: https://mixpanel.com/legal/eu-data-residency — pick `region=eu` **and** EU hosts (handler gap: hosts stay US).
- Never log `distinct_id` together with emails/IPs from properties.

## OfficialApiNode notes — ingest vs query auth

- **One auth type per spec.** Basic service account is applied on **every** HTTP op, including `track`/`engage` (provider expects token-in-JSON there). Prefer **`import`** for server-side events.
- Ingest `track`/`engage` expect the project token inside event JSON (`properties.token` / `$token` on INPUT). Prefer **`import`** for server-side events.
- `track` and `import` post the `events` array itself (`Content-Type: application/json`). One `event` plus `properties` / `distinct_id` is wrapped into that array when `events` is omitted. A list of JSON object strings is parsed.
- `engage.set` posts `[{$token,$distinct_id,$set}]`. `engage.unset` posts `[{$token,$distinct_id,$unset}]` using the names in `properties` (or `$unset`). A verbose `status` of `0` is a failure, not success.
- `region` does not rewrite `api-eu` / `api-in` / `data-eu` hosts.
- Query API published limit: 60 queries/hour, 5 concurrent. Do not tight-loop 429.
