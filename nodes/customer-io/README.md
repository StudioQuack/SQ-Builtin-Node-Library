# Customer.io

Official [Customer.io App API v1](https://docs.customer.io/integrations/api/app/) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://docs.customer.io/integrations/api/app/
- **Base:** `https://{api_host}/v1` (config `api_host`: `api.customer.io` US default, `api-eu.customer.io` EU)
- **Auth:** App API: `Authorization: Bearer {app_api_key}`. Track API (separate host + Basic auth) is not this node.
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `customer.get` | GET | `/customers/{identifier}/attributes` | Official HTTP |
| `customer.list` | GET | `/customers?email=` | **Email lookup only** (not a workspace-wide list). Parameterless call → `email is required`. `limit`/`start` on INPUT are **not** sent on this op — use `customer.search` for paginated discovery. |
| `customer.search` | POST | `/customers` | Paginated filter search (`filter` body; `start`/`limit` query). |
| `customer.segments` | GET | `/customers/{identifier}/segments` | Official HTTP |
| `campaign.list` | GET | `/campaigns` | Official HTTP |
| `campaign.get` | GET | `/campaigns/{campaign_id}` | Official HTTP |
| `campaign.metrics` | GET | `/campaigns/{campaign_id}/metrics` | Official HTTP |
| `segment.list` | GET | `/segments` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `app_api_key` | password | yes | yes | Customer.io App API key. Bearer on api.customer.io. Never log or place on INPUT. |
| `api_host` | select | no | no | App API region host: `api.customer.io` (US, default) or `api-eu.customer.io` (EU) |
| `timeout_seconds` | number | no | no | Default 25 |


## Track API (not dispatched)

OfficialApiNode has one `baseUrl` and one auth scheme. This node calls **App API** Bearer at `https://api.customer.io/v1`.

Track (US `https://track.customer.io/api/v1`, EU `https://track-eu.customer.io/api/v1`) uses HTTP Basic and is not dispatched here.

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://customer.io/legal/terms-of-service/
- Official API only. Do not scrape the provider dashboard.
- Track identify/events/manual segments live on https://track.customer.io/api/v1 (US) or https://track-eu.customer.io/api/v1 (EU) with HTTP Basic — not this node.
- EU App host is https://api-eu.customer.io/v1 — set config `api_host` to `api-eu.customer.io` for EU workspaces. Pick region in the Customer.io workspace; do not send health data or government ids.
- Reporting webhook HMAC is verified on the BPMN message event, not on this node.

