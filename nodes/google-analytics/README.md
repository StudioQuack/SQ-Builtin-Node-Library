# Google Analytics

Official **GA4 Data API v1beta** + **Admin API v1beta**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** Data API https://developers.google.com/analytics/devguides/reporting/data/v1 · `runReport` https://developers.google.com/analytics/devguides/reporting/data/v1/rest/v1beta/properties/runReport · Admin https://developers.google.com/analytics/devguides/config/admin/v1
- **Hosts:** `https://analyticsdata.googleapis.com` (reports/metadata) · `https://analyticsadmin.googleapis.com` (`property.list`)
- **Auth:** Bearer `access_token` (customer supplies a current OAuth token). Scope `https://www.googleapis.com/auth/analytics.readonly` for reports.

**Universal Analytics is dead** (sunset 2024-07-01). Catalog verb `userActivity.search` is **omitted** — it was Reporting API v4 and is not a Data API v1beta method. No `UA-` / `ga:` view ids.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `report.run` | POST | `/v1beta/properties/{propertyId}:runReport` |
| `report.realtime` | POST | `/v1beta/properties/{propertyId}:runRealtimeReport` |
| `metadata.get` | GET | `/v1beta/properties/{propertyId}/metadata` |
| `property.list` | GET | `/v1beta/accountSummaries` (Admin) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth Bearer |
| `propertyId` | string | no | Default numeric GA4 property id |
| `timeout_seconds` | number | no | Default 60 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer owns the GA4 property.
- Google APIs ToS: https://developers.google.com/terms
- Google Analytics Terms: https://www.google.com/analytics/terms/
- Measurement/user-data policies: https://support.google.com/analytics/answer/4597324
- Reports can include user-level dimensions. Minimize. Never log access tokens or row values containing user ids/emails.
- Official API only. No UA Core Reporting, no Measurement Protocol from this node, no scraping the GA UI.
