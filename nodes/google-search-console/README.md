# Google Search Console

Official **Search Console API**: Webmasters **v3** + URL Inspection **v1**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developers.google.com/webmaster-tools · searchAnalytics.query https://developers.google.com/webmaster-tools/v1/searchanalytics/query · authorize https://developers.google.com/webmaster-tools/v1/how-tos/authorizing
- **Hosts:** `https://www.googleapis.com/webmasters/v3` (sites, sitemaps, searchAnalytics) · `https://searchconsole.googleapis.com/v1/urlInspection/index:inspect`
- **Auth:** Bearer `access_token` (customer supplies a current OAuth token). Scope `https://www.googleapis.com/auth/webmasters.readonly` or `webmasters`.

`site.add` **adds the property to the user’s Search Console list**; it does **not** complete verification (DNS/HTML/file still required). This is **not** a general web-search API.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `site.list` | GET | `/webmasters/v3/sites` |
| `site.add` | PUT | `/webmasters/v3/sites/{siteUrl}` |
| `searchAnalytics.query` | POST | `/webmasters/v3/sites/{siteUrl}/searchAnalytics/query` |
| `sitemap.list` | GET | `/webmasters/v3/sites/{siteUrl}/sitemaps` |
| `sitemap.submit` | PUT | `/webmasters/v3/sites/{siteUrl}/sitemaps/{feedpath}` |
| `urlInspection.inspect` | POST | `/v1/urlInspection/index:inspect` |

`siteUrl` is path-encoded (e.g. `https://www.example.com/` or `sc-domain:example.com`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | OAuth Bearer |
| `siteUrl` | string | no | Default property |
| `timeout_seconds` | number | no | Default 60 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer owns the Search Console property. Verified properties only.
- Google APIs ToS: https://developers.google.com/terms
- Redistributing Search Analytics query strings or scraping third-party rankings violates Google’s terms.
- Search Analytics does not guarantee all rows — “top ones” only.
- Query dimension values can be personal (search terms). Minimize logging.
- Official API only: `www.googleapis.com/webmasters/v3` and `searchconsole.googleapis.com`.
