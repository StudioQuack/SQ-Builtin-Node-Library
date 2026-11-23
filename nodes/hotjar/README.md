# Hotjar

Official [Hotjar HTTP API v1](https://help.hotjar.com/hc/en-us/articles/36820005914001-Hotjar-API-Reference) at `https://api.hotjar.io`. Handler: `OfficialApiNode` via `api_spec.json`. Survey export + user lookup. **No heatmap scrape. No recording video/DOM fetch.** Identify and Events are **browser JavaScript only** and are not operations on this node.

- **Docs:** https://help.hotjar.com/hc/en-us/articles/36820005914001-Hotjar-API-Reference
- **Webhooks:** https://help.hotjar.com/hc/en-us/articles/13052816995991-Webhooks-Reference
- **Base:** `https://api.hotjar.io`
- **API:** v1
- **Auth:** Bearer `access_token`. Official OAuth is client-credentials against `POST /v1/oauth/token` (`grant_type=client_credentials`, `client_id`, `client_secret`). OfficialApiNode does not fetch that token — paste a pre-fetched access token. Tokens typically last 3600s; API key pairs expire after 1 year.
- **Plan:** Ask Scale (surveys) / Observe Scale (user lookup). Rate: 3000 req/min.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `survey.list` | GET | `/v1/sites/{site_id}/surveys` |
| `survey.get` | GET | `/v1/sites/{site_id}/surveys/{survey_id}` |
| `survey.response.list` | GET | `/v1/sites/{site_id}/surveys/{survey_id}/responses` |
| `user.lookup` | POST | `/v1/organizations/{organization_id}/user-lookup` |
| `trigger.listen` | n/a | **ingress** — paste the StudioQuack message-event URL in Hotjar Survey (or Recording Segment) webhook UI. Events: `survey_response`, `recording` (metadata only), `test_message`. HMAC header `com-hotjar-signature` (SHA3-256) is verified on the ingress path, not in this node. |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Pre-fetched Bearer token |
| `site_id` | string | no | Path `{site_id}` when input is blank |
| `timeout_seconds` | number | no | Default 25 |

`user.lookup` needs INPUT `organization_id` plus `data_subject_email` and/or `data_subject_site_id_to_user_id_map` (`{"<site_id>":"<your user id>"}`). `delete_all_hits=true` irreversibly deletes the subject's data without a report; Hotjar answers 202 with no body.

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Contentsquare ToS (scraping / site crawl prohibited): https://contentsquare.com/page-legal-terms-of-service/
- Survey answers and session metadata are personal data. Customer owns the Hotjar site.
- Official API only. Do not scrape recordings, heatmaps, or the dashboard.
