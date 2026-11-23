# Hootsuite

Official Hootsuite REST API **1.0** (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HOOTSUITE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

- **Docs:** https://developer.hootsuite.com/docs/api-overview · Schedule https://developer.hootsuite.com/docs/message-scheduling · Analytics https://developer.hootsuite.com/docs/analytics-overview · Reference https://platform.hootsuite.com/docs/api/index.html
- **Base:** `https://platform.hootsuite.com/v1`
- **Auth:** StudioQuack secret `access_token` → `Authorization: Bearer`. Never on INPUT. Never logged.

A Developer account may require Hootsuite approval. This is not an anonymous public API.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `message_schedule` | POST | `/messages` body `text`, `scheduledSendTime`, `socialProfileIds` |
| `message_list` | GET | `/messages` query `startTime`, `endTime` (window ≤ 4 weeks) |
| `analytics_get` | POST | `/analytics/posts` body `filters`, `limit`, `cursor` |
| `trigger` | ingress | BPMN message start / catch for `com.hootsuite.messages.event.v1` |

`scheduledSendTime` is UTC ISO-8601 ending in `Z`, ≥ 5 minutes ahead. Resolve profile ids with `GET /v1/socialProfiles` (not this node’s path list). Media upload (`POST /v1/media` then PUT `uploadUrl`) is not chained in this node. Analytics requires the Advanced Analytics add-on and OAuth scope `analytics:read`. Publishing does not cover every destination network — see the API overview.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`message_list`) |
| `access_token` | password | yes | Bearer (refresh tokens are single-use) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- API ToS: https://hootsuite.com/legal/api-terms-of-service
- Rate limits: 20 requests/second, 100,000 calls/day. HTTP 429 may include code 1003.
- Messages and analytics contain social-network PII and copyrighted posts. Destination-network terms still apply. Official API only.
