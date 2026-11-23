# Typeform

Official Typeform **Create + Responses + Webhooks** REST. Handler: `OfficialApiNode` via `api_spec.json`. Form responses are respondent PII.

- **Docs:** https://www.typeform.com/developers/ · Create / Responses / Webhooks under that site
- **Base (US):** `https://api.typeform.com` — no numeric version in the path. `api_spec.json` uses `baseUrl` `https:/` plus path `/{api_host}/…` so OfficialApiNode can interpolate the DC host (it does not interpolate `baseUrl`).
- **Auth:** Bearer `access_token` (personal token or OAuth). Customer owns the Typeform account.

## Data centers

Set config `api_host` (hostname only). OfficialApiNode interpolates it into the URL.

| Account | `api_host` |
|---|---|
| US (default) | `api.typeform.com` |
| EU | `api.eu.typeform.com` |
| New EU DC (accounts onboarded ~2025-08+) | `api.typeform.eu` |

OAuth authorize stays on the matching host. Tokens are not interchangeable across the new EU DC.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.list` | GET | `/forms` |
| `form.get` | GET | `/forms/{form_id}` |
| `form.create` | POST | `/forms` |
| `response.list` | GET | `/forms/{form_id}/responses` |
| `response.get` | GET | `/forms/{form_id}/responses` (`included_response_ids`) |
| `webhook.register` | PUT | `/forms/{form_id}/webhooks/{tag}` |
| `trigger.listen` | ingress | BPMN message event |

Recent Responses API results can lag (~30 minutes); use webhooks for near-real-time. Point `webhook.register` `url` at StudioQuack message-event ingress. `trigger.listen` itself does not open a socket.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token |
| `api_host` | string | no | Hostname; default `api.typeform.com` |
| `form_id` | string | no | Default form id |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `access_token` or HMAC `secret`. File answers: type `file` (ref-id).

## Legal

- Customer is the controller of respondent answers (may include payment/hidden fields). Minimize. GDPR/CCPA basis is the caller's.
- Create + Responses: 2 requests per second per account. Never log tokens, answers, or HMAC secrets.
- Official API only: `api.typeform.com` / `api.eu.typeform.com` / `api.typeform.eu`. Do not scrape the builder. Embed SDK is out of scope.
