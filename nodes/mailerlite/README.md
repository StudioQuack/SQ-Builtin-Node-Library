# MailerLite

Official **current** MailerLite REST (`https://connect.mailerlite.com/api`). Handler: `OfficialApiNode` via `api_spec.json`. This is **not** MailerLite Classic (`https://api.mailerlite.com/api/v2` + `X-MailerLite-ApiKey`). Classic keys do not work here.

- **Docs:** https://developers.mailerlite.com/docs/ · Subscribers: https://developers.mailerlite.com/api/subscribers
- **Base:** `https://connect.mailerlite.com/api`
- **Auth:** Bearer `api_token` (secret). Optional provider header `X-Version: YYYY-MM-DD` pins a date; OfficialApiNode cannot send it — empty means latest.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `subscriber.create` | POST | `/subscribers` (upsert by email) |
| `subscriber.get` | GET | `/subscribers/{subscriber_id_or_email}` |
| `subscriber.list` | GET | `/subscribers` |
| `subscriber.update` | PUT | `/subscribers/{subscriber_id}` |
| `subscriber.delete` | DELETE | `/subscribers/{subscriber_id}` |
| `group.add` | POST | `/subscribers/{subscriber_id}/groups/{group_id}` |
| `group.remove` | DELETE | `/subscribers/{subscriber_id}/groups/{group_id}` |
| `campaign.send` | POST | `/campaigns/{campaign_id}/schedule` (`delivery: instant` to send now) |
| `trigger.listen` | ingress | BPMN message start / catch / boundary |

List filter key is `filter[status]` on input (active / unsubscribed / unconfirmed / bounced / junk). Rate limit: 120 requests / minute.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_token` | password | yes | Bearer token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{status,http_status,data}` — never includes `api_token`.

## Legal

- Terms: https://www.mailerlite.com/legal/terms-of-service
- If subscribers are collected without MailerLite forms, anti-spam duty sits on the API key holder. Caller owns CAN-SPAM / CASL / GDPR. Customer owns the MailerLite account.
- Keys stop working if the creating user is deleted.
