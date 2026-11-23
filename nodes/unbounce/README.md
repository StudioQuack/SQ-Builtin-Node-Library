# Unbounce

Official [Unbounce REST API v0.4](https://developer.unbounce.com/api_reference/). Handler: `OfficialApiNode` via `api_spec.json`. Pages, form fields, and page leads. Page leads are PII.

- **Docs:** https://developer.unbounce.com/getting_started/ · https://developer.unbounce.com/api_reference/
- **Base:** `https://api.unbounce.com`
- **Auth:** HTTP Basic (`api_key` as username). Customer owns the Unbounce account.

v0.4 is current (v0.3 removed May 2018). API Access is request-gated (support enables it, then keys are created in Manage Account). Keys act as account administrators.

`subdomain` is **not** the API host — do not interpolate it into `api.unbounce.com`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `page.list` | GET | `/pages` |
| `page.get` | GET | `/pages/{page_id}` |
| `form.list` | GET | `/pages/{page_id}/form_fields` |
| `lead.list` | GET | `/pages/{page_id}/leads` |
| `lead.get` | GET | `/pages/{page_id}/leads/{lead_id}` |
| `trigger.listen` | ingress | BPMN message event |

v0.4 has no REST “create webhook” endpoint. `trigger.listen` does not open a socket — paste the StudioQuack message-event URL into Unbounce UI webhook settings. Sub-account-wide lead list was removed in v0.4.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Basic username |
| `page_id` | string | no | Default page UUID |
| `timeout_seconds` | number | no | Default 25 |

Official Unbounce docs use an empty Basic password. `OfficialApiNode` requires a non-empty password, so the same `api_key` is sent as username and password. Unbounce authenticates the username.

Provider `Accept` for v0.4 is `application/vnd.unbounce.api.v0.4+json`. The shared HTTP client also sends `Accept: application/json`. If the origin returns 406, that header mismatch is the cause.

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `api_key` or `form_data`.

## Legal

- Customer owns the Unbounce account and landing-page consent. Leads are PII. Minimize. Never log `api_key` or form field values.
- Rate limit: 500 requests per minute per user account and IP (HTTP 429).
- Official API only: `api.unbounce.com` + v0.4. Do not scrape the Smart Builder. Do not ship page HTML/CSS/JS sub-resources.
