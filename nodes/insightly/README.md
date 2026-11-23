# Insightly

Official [Insightly REST v3.1](https://api.insightly.com/v3.1/Help) on pod hosts `https://api.{pod}.insightly.com/v3.1/`. Handler: `OfficialApiNode` via `api_spec.json`. Contacts, organisations, leads, opportunities, projects, tasks, notes, events, custom fields.

- **Docs:** https://api.insightly.com/v3.1/Help · pod Help e.g. https://api.na1.insightly.com/v3.1/
- **Base (this spec):** `https://api` + path `.{pod}.insightly.com/v3.1/…` (pod interpolates from config)
- **API:** v3.1
- **Auth:** HTTP Basic — official contract is **API key as username, empty password** (`Base64(api_key + ":")`). OfficialApiNode Basic encodes `{userKey}:{configKey}` and requires a non-blank secret, so both are bound to `api_key` (`key:key`). If the instance 401s, that empty-password encoding is the documented form.

## Operations

Catalog CRM verbs plus `customField.list`. `trigger.listen` is **ingress** (Insightly System Settings → Integrations → Webhooks). Do not invent REST webhook CRUD.

List query: `top` / `skip` (max 500). JSON POST/PUT; PUT is partial. Create/update send the official ALL_CAPS fields (`FIRST_NAME`, `ORGANISATION_NAME`, `OPPORTUNITY_STATE`, …) plus `CUSTOMFIELDS` — not the whole input map.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Basic username (and handler password) |
| `pod` | string | no | Host label only, e.g. `na1` |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Create/update: official field names on INPUT (`FIRST_NAME`, `EMAIL_ADDRESS`, `CUSTOMFIELDS`, …). Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Rate: 10 req/s; daily quota by plan (headers `X-RateLimit-*`).
- Contacts/leads/emails are PII. Customer owns the instance.
- Official API only. Do not scrape the Insightly UI.
