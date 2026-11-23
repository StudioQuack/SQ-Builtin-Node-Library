# Ahrefs

Official Ahrefs API v3 (OpenAPI 3.0.0 at https://docs.ahrefs.com). API v2 is fully deprecated. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://docs.ahrefs.com/ · Site Explorer https://docs.ahrefs.com/en/api/reference/site-explorer · OpenAPI https://docs.ahrefs.com/openapi.json
- **Base:** `https://api.ahrefs.com`
- **Auth:** Bearer `api_token`. Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `siteExplorer.overview` | GET | `/v3/site-explorer/metrics` |
| `siteExplorer.backlinks` | GET | `/v3/site-explorer/all-backlinks` |
| `keywordsExplorer.overview` | GET | `/v3/keywords-explorer/overview` |
| `rankTracker.overview` | GET | `/v3/rank-tracker/overview` |
| `serpOverview` | GET | `/v3/serp-overview/serp-overview` |
| `batchAnalysis` | POST | `/v3/batch-analysis/batch-analysis` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `timeout_seconds` | number | no | Default 25 |

Shared production host: `api.ahrefs.com` (no regional API host). Default 60 rpm; 429 on burst. Select only needed `select` fields. One HTTP call per invoke. `siteExplorer.overview` requires `target` + `date`; `rankTracker.overview` requires `project_id`, `select`, `date` (YYYY-MM-DD) and `device` (`desktop` | `mobile`).

## Ports

`input` / `output` only. Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://ahrefs.com/terms — paid API units are non-refundable; do not redistribute SERP/backlink datasets.
- Official API only. Do not scrape the vendor dashboard.
