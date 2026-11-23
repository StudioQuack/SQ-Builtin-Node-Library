# Iterable

Official [Iterable REST](https://api.iterable.com/api/docs). Handler: `OfficialApiNode` via `api_spec.json`. User upsert/get/delete, event track, list subscribe/unsubscribe, catalog item upsert, campaign trigger.

- **Docs:** https://api.iterable.com/api/docs · EU explorer: https://api.eu.iterable.com/api/docs
- **Getting started:** https://support.iterable.com/hc/en-us/articles/41044692130196-Getting-Started-with-Iterable-s-API
- **Base:** `https://{region}` — `api.iterable.com` (USDC) / `api.eu.iterable.com` (EDC)
- **API:** `/api/...` (no calendar version string). Project is USDC or EDC.
- **Auth:** header `Api-Key` (`api_key`). Server-side project key only — not client-side / JWT browser keys.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `user.upsert` | POST | `/api/users/update` |
| `user.get` | GET | `/api/users/{email}` |
| `user.delete` | DELETE | `/api/users/{email}` |
| `event.track` | POST | `/api/events/track` |
| `list.add` | POST | `/api/lists/subscribe` |
| `list.remove` | POST | `/api/lists/unsubscribe` |
| `catalog.upsert` | PUT | `/api/catalogs/{catalogName}/items/{itemId}` |
| `campaign.trigger` | POST | `/api/campaigns/trigger` |
| `trigger.listen` | n/a | **ingress** — BPMN message event. Does not POST `/api/webhooks`. |

Set consent fields on the user (`emailListIds`, `unsubscribedChannelIds`, …) **before** `campaign.trigger`. StudioQuack does not infer consent. Wrong `region` for the key → 401; do not retry the other DC.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Header `Api-Key` |
| `region` | select | no | `api.iterable.com` (USDC) \| `api.eu.iterable.com` (EDC) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_key`, emails, or `dataFields` in logs.

## Legal

- Terms: https://iterable.com/legal/terms/ · Trust: https://iterable.com/trust/terms-of-service/
- CAN-SPAM/CASL/GDPR consent: caller duty.
- Published throughput ~30 req/s. Customer owns the project.
- Official API only.
