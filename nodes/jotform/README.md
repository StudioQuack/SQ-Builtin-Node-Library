# Jotform

Official [Jotform REST API v1](https://api.jotform.com/docs/). Handler: `OfficialApiNode` via `api_spec.json`. Forms, submissions, reports.

- **Docs:** https://api.jotform.com/docs/
- **Base:** `https://{host}` (config `host`)
- **API:** v1
- **Auth:** header `APIKEY` (`api_key`). **Do not** put `apiKey` on the query string.
- **Hosts:** official `api.jotform.com` / `eu-api.jotform.com` / `hipaa-api.jotform.com`. Keys are region-bound.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.list` | GET | `/user/forms` |
| `form.get` | GET | `/form/{form_id}` |
| `submission.list` | GET | `/form/{form_id}/submissions` |
| `submission.get` | GET | `/submission/{submission_id}` |
| `submission.delete` | DELETE | `/submission/{submission_id}` |
| `report.list` | GET | `/form/{form_id}/reports` |
| `trigger.listen` | n/a | **ingress** — register the StudioQuack HTTPS message-event URL as a form webhook in Jotform (this op does not POST `/form/{id}/webhooks`). |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Header `APIKEY` |
| `host` | select | no | Official hostname (`api.jotform.com` / `eu-api.jotform.com` / `hipaa-api.jotform.com`) → `https://{host}` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key` or answer values.

## Legal

- Terms: https://www.jotform.com/terms/ · Privacy: https://www.jotform.com/privacy/
- Daily call limits by plan (Starter 1 000 … Enterprise unlimited — confirm live “Daily API Call Limits”).
- Respondent answers are personal data. EU host is the residency control for EU plans. HIPAA host only when the account is HIPAA.
- Official API only. Do not scrape the builder UI.
