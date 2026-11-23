# SEMrush

Official Semrush Analytics / Standard API **v3** + Backlinks **v4** + Trends Traffic Summary **v3** + Projects Position Tracking. Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Semrush account. Calls consume API units.

- **Docs:** https://developer.semrush.com/api/v4/introduction/semrush-api-overview/ · https://developer.semrush.com/api/v3/seo/overview-reports/ · https://developer.semrush.com/api/v3/analytics/domain-reports/ · https://developer.semrush.com/api/v4/seo/backlinks/ · https://developer.semrush.com/api/v3/trends/api-reference/ · Position Tracking https://developer.semrush.com/api/v3/projects/position-tracking/
- **API (2026-08-20):** Standard reports on `https://api.semrush.com/?type=…` (v3). Backlinks overview `GET /apis/v4/backlinks/v1/overview` (v3 `/analytics/v1/` is deprecated). Trends `GET /analytics/ta/api/v3/summary`. Projects: `PUT /management/v1/projects/{campaign_id}/keywords`, `POST /management/v1/projects/{project_id}/tracking/enable`. Docs updated 2026-08-05 (overview) / 2026-07-15 (overview reports) / 2026-07-15 (projects overview).
- **Base:** `https://api.semrush.com`
- **Auth:** API key as query `key=` (`OfficialApiNode` query auth, config `key`). Never on INPUT.

HTTP 200 with a CSV `ERROR` line (invalid key, no units, nothing found) is a provider error — treat as failure.

Do not scrape HTML. Do not republish raw datasets. This node is for the operator’s account only.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `domain.overview` | GET | `/?type=domain_rank` |
| `domain.organic` | GET | `/?type=domain_organic` |
| `domain.adwords` | GET | `/?type=domain_adwords` |
| `phrase.this` | GET | `/?type=phrase_this` |
| `backlinks.overview` | GET | `/apis/v4/backlinks/v1/overview` |
| `traffic.summary` | GET | `/analytics/ta/api/v3/summary` |
| `project.keyword.add` | PUT | `/management/v1/projects/{campaign_id}/keywords` |
| `project.positionTracking.enable` | POST | `/management/v1/projects/{project_id}/tracking/enable` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `key` | password | yes | Semrush API key (query `key`) |
| `database` | string | no | Regional DB code (e.g. `us`); sent as query `database` |
| `timeout_seconds` | number | no | Default 60 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Terms: https://www.semrush.com/company/legal/terms-of-service/
- Content Policy: https://www.semrush.com/company/legal/content-policy/
- API restrictions (2026-08-05): 10 requests/second, 10 simultaneous; do not cache API data longer than one month without written consent.
- Official API only.
