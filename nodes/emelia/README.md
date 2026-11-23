# Emelia

Official [Emelia REST API v1](https://docs.emelia.io/docs/api-reference) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://docs.emelia.io/docs/api-reference
- **Base:** `https://api.emelia.io`
- **Auth:** `Authorization` header = API key (apiKey scheme; not a Bearer prefix)
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `campaign.create` | POST | `/advanced/campaigns` | Official HTTP |
| `campaign.get` | GET | `/advanced/campaigns/{campaignId}` | Official HTTP |
| `campaign.list` | GET | `/advanced/campaigns` | Official HTTP |
| `campaign.start` | POST | `/advanced/campaigns/{campaignId}/start` | Advanced multichannel campaigns (not legacy `/emails/campaigns`) |
| `campaign.pause` | POST | `/advanced/campaigns/{campaignId}/pause` | Same scope as `campaign.get` |
| `campaign.delete` | DELETE | `/advanced/campaigns/{campaignId}` | Permanently delete a campaign (cleanup / tests) |
| `campaign.addContact` | POST | `/advanced/campaign/contacts` | Official HTTP (legacy email: `/emails/campaign/contacts`) |
| `contactList.add` | POST | `/lists/list/{listId}/contacts` | Official HTTP |
| `contactList.list` | GET | `/lists/list` | Official HTTP (list envelopes; `page` / `limit`) |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `api_key` | password | yes | yes | Emelia API key from account settings. Sent as Authorization header value. Never log or place on INPUT. |
| `timeout_seconds` | number | no | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://emelia.io/cgv
- Official API only. Do not scrape the provider dashboard.
- **CAN-SPAM / CASL / GDPR:** Emelia is an outreach sender. The customer is the sender of record: lawful basis, physical postal address in the footer where required, working unsubscribe, and no purchased/scraped recipient lists. StudioQuack does not send on the customer's behalf except via the customer's own Emelia account.
- Sender reputation (domain authentication, bounce/complaint rates) is the customer's operational duty.
- Profile-URL fields on contacts remain subject to that destination network's terms; this node does not scrape.
- Create webhook HTTP is POST /webhook; inbound delivery still uses a BPMN message event (`trigger.listen`).

