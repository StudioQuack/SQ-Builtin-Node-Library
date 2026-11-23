# Campaign Monitor

Official [Campaign Monitor REST API v3.3](https://www.campaignmonitor.com/api/v3-3/getting-started/) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://www.campaignmonitor.com/api/v3-3/getting-started/
- **Base:** `https://api.createsend.com/api/v3.3`
- **Auth:** HTTP Basic `api_key:` (username = API key, empty password)
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `subscriber.add` | POST | `/subscribers/{listId}.json` | Official HTTP |
| `subscriber.get` | GET | `/subscribers/{listId}.json` | Official HTTP |
| `subscriber.update` | PUT | `/subscribers/{listId}.json` | Official HTTP |
| `subscriber.unsubscribe` | POST | `/subscribers/{listId}/unsubscribe.json` | Official HTTP |
| `subscriber.delete` | DELETE | `/subscribers/{listId}.json` | Official HTTP |
| `list.create` | POST | `/lists/{clientId}.json` | Official HTTP |
| `list.get` | GET | `/lists/{listId}.json` | Official HTTP |
| `campaign.create` | POST | `/campaigns/{clientId}.json` | Official HTTP |
| `campaign.send` | POST | `/campaigns/{campaignId}/send.json` | Official HTTP |
| `campaign.unsent` | GET | `/clients/{clientId}/drafts.json` | Official HTTP |
| `transactional.send` | POST | `/transactional/smartEmail/{smartEmailId}/send` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `api_key` | password | yes | yes | Campaign Monitor API key, sent as the Basic username with an empty password. Never log or place on INPUT. |
| `clientId` | string | no | no | Campaign Monitor client (tenant) id. Path {clientId} for list.create, campaign.create, campaign.unsent. Also accepted on INPUT. |
| `listId` | string | no | no | Default list id for subscriber ops when INPUT listId is blank. |
| `timeout_seconds` | number | no | no | Default 25 |

`campaign.create` requires `Name`, `Subject`, `FromName`, `FromEmail`, `ReplyTo`, `HtmlUrl` and at least one of `ListIDs` / `SegmentIDs` (arrays of ids).

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://www.campaignmonitor.com/legal/terms/
- Official API only. Do not scrape the provider dashboard.
- Transactional routes have a stricter rate limit than the general API. Honor 429.
- Classic transactional send is a different path; this node’s transactional.send is smart email.
- List webhooks (Subscribe/Update/Deactivate) belong on a BPMN message event, not this node’s HTTP.

