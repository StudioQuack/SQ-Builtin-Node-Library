# Heap

Official [Heap server-side HTTP APIs](https://developers.heap.io/reference/server-side-apis-overview) (docs updated 2026-04-01; ingest host verified live 2026-08-20). Handler: `OfficialApiNode` via `api_spec.json`. Custom events, identity merge, user properties, account properties. **Not** heatmap/session-replay export, **not** browser heap.js, **not** the Contentsquare Enrichment API.

- **Docs:** https://developers.heap.io/reference/server-side-apis-overview · Identify https://developers.heap.io/reference/identify-1
- **Base:** `https://{region}` — US `heapanalytics.com`, EU `c.eu.heap-api.com` (https://developers.heap.io/reference/server-side-apis-overview)
- **API:** server-side ingest (no calendar version string)
- **Auth:** none — `app_id` in the JSON body. Ingest does not use an API key.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `track` | POST | `/api/track` |
| `identify` | POST | `/api/v1/identify` |
| `addUserProperties` | POST | `/api/add_user_properties` |
| `addAccountProperties` | POST | `/api/add_account_properties` |

`track` requires **either** `identity` **or** `user_id`, not both. Copy `app_id` onto **INPUT** (bodyFrom reads input only).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `app_id` | string | no | Heap environment ID (also on INPUT) |
| `region` | select | no | Official host: `heapanalytics.com` (US) or `c.eu.heap-api.com` (EU) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer is the controller of identities and property maps they submit. Minimize: no government IDs or health data.
- Contentsquare ToS (parent): https://contentsquare.com/page-legal-terms-of-service/
- Developer terms: https://contentsquare.com/legal-developer-terms/
- Official API only. Do not scrape recordings or the dashboard.
