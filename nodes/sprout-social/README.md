# Sprout Social

Official Sprout Social Public API v1 (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_SPROUT_SOCIAL`). Dispatched by `OfficialApiNode` from `api_spec.json`.

- **Docs:** https://api.sproutsocial.com/docs/
- **Support / plan:** https://support.sproutsocial.com/hc/en-us/articles/360045006152-Sprout-Public-API
- **Base:** `https://api.sproutsocial.com`
- **Auth:** Bearer `access_token` (account token from Settings → Global Features → API, or OAuth JWT). Never on INPUT. Never logged.

Requires an eligible plan (Advanced + API Permissions). Publishing create currently produces **draft** posts only (`is_draft: true`).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `message_create` | POST | `/v1/{customer_id}/publishing/posts` |
| `message_get` | GET | `/v1/{customer_id}/publishing/posts/{publishing_post_id}` |
| `analytics_get` | POST | `/v1/{customer_id}/analytics/profiles` |
| `media_upload` | POST | `/v1/{customer_id}/media/` |

No inbound `trigger`: the public docs do not document a webhook subscribe. Use a BPMN message event if you add your own listener.

`customer_id` is a path variable (config or input). Discover ids with `GET /v1/metadata/client`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer token |
| `customer_id` | string | no | Path `{customer_id}` |
| `timeout_seconds` | number | no | Default 30 (use 60 for uploads) |

## Ports

One INPUT `input`, one OUTPUT `output`. `media` is type `file` (platform ref-id, cap 50 MB). Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

`OfficialApiNode` JSON-encodes `bodyFrom` (including `media_upload`). Multipart single-request upload under 50 MB is the documented simple path.

## Legal

- API docs / developer acknowledgement: https://api.sproutsocial.com/docs/
- Product overview: https://sproutsocial.com/insights/sprout-social-api/
- **Rate limits:** 60 requests/minute, 250,000/month. HTTP 429.
- Publishing and analytics contain social PII and third-party content. Destination-network terms still apply. Official API only; do not scrape app.sproutsocial.com.
- Customer owns the Sprout account and token on their daemon.
