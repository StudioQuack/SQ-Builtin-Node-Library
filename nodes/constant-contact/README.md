# Constant Contact

Official [Constant Contact V3 REST](https://developer.constantcontact.com/api_guide/v3_technical_overview.html) via `OfficialApiNode` / `api_spec.json`.

- **Docs:** https://developer.constantcontact.com/api_guide/v3_technical_overview.html
- **Base:** `https://api.cc.email/v3`
- **Auth:** OAuth 2.0 access token as `Authorization: Bearer` (customer supplies token; refresh outside the graph)
- **Account:** Customer owns the provider account. StudioQuack does not accept the provider ToS on the customer's behalf.

Inbound provider callbacks are **not** a node socket. `trigger.listen` is `kind: ingress` — use a BPMN message start / catch / boundary event. StudioQuack webhook ingress correlates onto those events.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `contact.create` | POST | `/contacts` | Official HTTP |
| `contact.get` | GET | `/contacts/{contact_id}` | Official HTTP |
| `contact.list` | GET | `/contacts` | Official HTTP |
| `contact.update` | PUT | `/contacts/{contact_id}` | Official HTTP |
| `contact.delete` | DELETE | `/contacts/{contact_id}` | Official HTTP |
| `list.create` | POST | `/contact_lists` | Official HTTP |
| `list.add` | POST | `/activities/add_list_memberships` | Official HTTP |
| `campaign.create` | POST | `/emails` | Official HTTP |
| `campaign.get` | GET | `/emails/{campaign_id}` | Official HTTP |
| `campaign.schedule` | POST | `/emails/activities/{campaign_activity_id}/schedules` | Official HTTP |
| `email.send` | POST | `/emails/activities/{campaign_activity_id}/tests` | Official HTTP |
| `trigger.listen` | ingress | — | BPMN message event (no socket) |

## Config
| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Default verb |
| `access_token` | password | yes | yes | Constant Contact V3 OAuth access token. Bearer. Refresh via authz.constantcontact.com outside this node. Never log or place on INPUT. |
| `timeout_seconds` | number | no | no | Default 25 |

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize PII: send only fields the operation needs. Do not log emails, phones, message bodies, or tokens.
- Consent, unsubscribe, and suppression are the **customer's** duty (CAN-SPAM / CASL / GDPR where they apply).
- Terms: https://developer.constantcontact.com/api_guide/api_terms_and_conditions.html
- Official API only. Do not scrape the provider dashboard.
- Physical mailing address on the Constant Contact account is required to send (CAN-SPAM footer).
- V3 partner webhooks are billing/account topics only. Contact/campaign delivery: poll contact.list with updated_after.
- File import (contacts_file_import) is out of this node.

