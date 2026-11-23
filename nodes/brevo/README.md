# Brevo

Official [Brevo REST API v3](https://developers.brevo.com/docs/getting-started) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://developers.brevo.com/docs/getting-started
- **Base:** `https://api.brevo.com/v3`
- **Auth:** `api-key` header (config `api_key`)
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `contact.create` | POST | `/contacts` | Official HTTP |
| `contact.upsert` | POST | `/contacts` | Official HTTP |
| `contact.get` | GET | `/contacts/{identifier}` | Official HTTP |
| `contact.list` | GET | `/contacts` | Official HTTP |
| `contact.update` | PUT | `/contacts/{identifier}` | Official HTTP |
| `contact.delete` | DELETE | `/contacts/{identifier}` | Official HTTP |
| `contactAttribute.create` | POST | `/contacts/attributes/{attributeCategory}/{attributeName}` | Official HTTP |
| `contactAttribute.getAll` | GET | `/contacts/attributes` | Official HTTP |
| `contactAttribute.update` | PUT | `/contacts/attributes/{attributeCategory}/{attributeName}` | Official HTTP |
| `contactAttribute.delete` | DELETE | `/contacts/attributes/{attributeCategory}/{attributeName}` | Official HTTP |
| `email.send` | POST | `/smtp/email` | Official HTTP |
| `email.sendTemplate` | POST | `/smtp/email` | Official HTTP |
| `sender.create` | POST | `/senders` | Official HTTP |
| `sender.list` | GET | `/senders` | Official HTTP |
| `sender.delete` | DELETE | `/senders/{senderId}` | Official HTTP |
| `list.add` | POST | `/contacts/lists/{listId}/contacts/add` | Official HTTP |
| `campaign.send` | POST | `/emailCampaigns/{campaignId}/sendNow` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `api_key` | password | yes | yes | Brevo API key. Sent as header api-key (not Authorization). Never log or place on INPUT. Dashboard: SMTP & API → API keys. |
| `timeout_seconds` | number | no | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://www.brevo.com/legal/termsofuse/
- Official API only. Do not scrape the provider dashboard.
- Rate limits: honor HTTP 429. Contacts and SMTP payloads are PII.
- Do not send to purchased lists. Customer maintains unsubscribe/suppression in Brevo.

