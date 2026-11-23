# Segment

Official Twilio Segment **HTTP Tracking API**. Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Segment workspace / source. This is the HTTP Source (`api.segment.io`), not the Public API (`api.segmentapis.com`).

- **Docs:** https://www.twilio.com/docs/segment/connections/sources/catalog/libraries/server/http-api · Spec: https://segment.com/docs/connections/spec/
- **API (2026-08-20):** Tracking API paths `/v1/identify`, `/v1/track`, `/v1/page`, `/v1/screen`, `/v1/group`, `/v1/alias`. Docs last modified 2026-02-24 on Twilio.
- **Base:** `{data_plane_url}/v1`. Oregon default `https://api.segment.io`. EU Business regional data plane: `https://events.eu1.segmentapis.com`. Using the Oregon host from the EU can silently drop events — set `data_plane_url` for EU workspaces.
- **Auth:** Source **write key**. Official options: JSON body `writeKey`, or HTTP Basic `write_key:` (empty password). `OfficialApiNode` Basic encodes `{write_key}:{write_key}` (non-empty password required). Prefer keeping `write_key` only in config.

No `trigger.listen` — the Tracking API does not push events to callers.

At least one of `userId` or `anonymousId` is required on identify/track/page/screen/group (alias uses `userId` + `previousId`). Minimize traits/properties.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `identify` | POST | `/identify` |
| `track` | POST | `/track` |
| `page` | POST | `/page` |
| `screen` | POST | `/screen` |
| `group` | POST | `/group` |
| `alias` | POST | `/alias` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `write_key` | password | yes | Source write key (Basic username). Never on INPUT |
| `data_plane_url` | string | no | Tracking API origin used as `{data_plane_url}/v1`. Default Oregon |
| `timeout_seconds` | number | no | Default 15 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer is the controller of identifiers they submit.
- Twilio Segment DPA: https://www.twilio.com/legal/data-protection-addendum
- Recommended cap ~1,000 requests/second per workspace. Single event 32 KB; batch 500 KB.
- Official Tracking API only.
