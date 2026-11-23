# Mastodon

Official Mastodon REST (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_MASTODON`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

**Default instance is `mastodon.social`.** `OfficialApiNode` uses a fixed `baseUrl` (`https://mastodon.social`). Other instances require changing `api_spec.json` `baseUrl` (HTTPS origin, no path suffix) and rebuilding the library image. There is no per-invoke instance override in the engine.

- **Docs:** https://docs.joinmastodon.org/methods/statuses/ · Timelines https://docs.joinmastodon.org/methods/timelines/ · Search https://docs.joinmastodon.org/methods/search/
- **Auth:** StudioQuack secret `access_token` (user token) → `Authorization: Bearer`. Never on INPUT. Never logged.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `status_create` | POST | `/api/v1/statuses` body `status`, `visibility`, `in_reply_to_id` |
| `status_get` | GET | `/api/v1/statuses/{status_id}` |
| `timeline_get` | GET | `/api/v1/timelines/home` query `max_id`, `limit` |
| `search` | GET | `/api/v2/search` query `q`, `type`, `limit` |
| `account_get` | GET | `/api/v1/accounts/verify_credentials` |
| `trigger` | ingress | BPMN message start / catch — not SSE, not a node socket |

`timeline_get` is home only. Media upload (`POST /api/v2/media`) is not chained.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer user token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `access_token`.

## Legal

- Operator must comply with **that instance’s** terms, local law, and copyright. Rate limits are per instance (`GET /api/v2/instance`).
- Statuses, accounts, and media descriptions are personal data. Do not log HTML bodies. `direct` visibility is a private mention — treat as PII.
- Official REST only. Do not scrape instance HTML. Search v1 and the direct timeline are removed — this node does not call them.
