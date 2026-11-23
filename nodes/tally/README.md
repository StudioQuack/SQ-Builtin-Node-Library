# Tally

Official Tally REST API (`https://api.tally.so`). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Tally workspace. Date-versioned API (Stripe-style); pin with `tally-version` on the API key (default) — `OfficialApiNode` does not set a custom version header.

- **Docs:** https://developers.tally.so/api-reference/introduction · https://tally.so/help/api · Forms https://developers.tally.so/api-reference/endpoint/forms/submissions/list · Webhooks https://developers.tally.so/api-reference/endpoint/webhooks/post
- **API (2026-08-20):** HTTPS REST. Versions include `2025-01-15` and `2025-02-01` (pagination `items` / `hasMore`). Rate limit **100 requests/minute**.
- **Base:** `https://api.tally.so`
- **Auth:** Bearer API key (`api_key`). Never on INPUT.

`trigger.listen` is `kind: ingress`. `webhook.create` registers `url` + `eventTypes` (e.g. `FORM_RESPONSE`) against the StudioQuack message-event ingress. The listen operation itself does not open a socket — use a BPMN message start / catch / boundary event.

Submissions are respondent PII. Prefer webhooks over polling.

**AI Agent tools:** Map DuckTape `request` → INPUT (`operation`, `form_id`, …). Nested `config` / `request` envelopes are flattened before validation. INPUT accepts `form_id` or `formId` (webhook body uses official `formId`; path ops use `form_id`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `form.list` | GET | `/forms` |
| `form.get` | GET | `/forms/{form_id}` |
| `submission.list` | GET | `/forms/{form_id}/submissions` |
| `submission.get` | GET | `/forms/{form_id}/submissions/{submission_id}` |
| `webhook.create` | POST | `/webhooks` |
| `trigger.listen` | ingress | Webhook → BPMN message event |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Bearer token from Tally API keys |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of form responses. Minimize: send only form/submission ids needed.
- Provider terms: https://tally.so (current ToS on that site).
- Official API only. Do not scrape the builder.
